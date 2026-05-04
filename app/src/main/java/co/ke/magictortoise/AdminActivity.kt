package co.ke.magictortoise

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class AdminActivity : AppCompatActivity() {

    private lateinit var adapter: AdminAdapter
    private val requestList = mutableListOf<AdminRequest>()
    private val DB_URL = "https://magic-tortoise-default-rtdb.firebaseio.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val recyclerView = findViewById<RecyclerView>(R.id.rv_admin_requests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AdminAdapter(requestList) { request -> approveRequest(request) }
        recyclerView.adapter = adapter

        fetchData()
    }

    private fun fetchData() {
        val rootRef = FirebaseDatabase.getInstance(DB_URL).reference
        // Added 'mini_task_submissions' so you can see the TikTok proofs
        val nodes = listOf("advertiser_requests", "exchange_offers", "mini_task_submissions")

        for (node in nodes) {
            rootRef.child(node).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Clear existing items for this node to avoid duplicates on refresh
                    requestList.removeAll { it.nodeSource == node }

                    for (child in snapshot.children) {
                        val req = child.getValue(AdminRequest::class.java)
                        if (req != null && req.status == "pending") {
                            req.id = child.key ?: ""
                            req.nodeSource = node // Helper field to remember where it came from
                            requestList.add(req)
                        }
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@AdminActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun approveRequest(request: AdminRequest) {
        val db = FirebaseDatabase.getInstance(DB_URL).reference
        
        // 1. Update the status of the request
        db.child(request.nodeSource).child(request.id).child("status").setValue("Approved")
            .addOnSuccessListener {
                
                // 2. If it's a TikTok Task, automatically add KES 2.0 to user balance
                if (request.nodeSource == "mini_task_submissions") {
                    rewardUser(request.userId, 2.0)
                }
                
                Toast.makeText(this, "Approved & Processed!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rewardUser(userId: String, amount: Double) {
        val userBalanceRef = FirebaseDatabase.getInstance(DB_URL).reference
            .child("users").child(userId).child("balance")

        // Use a transaction to safely update the balance
        userBalanceRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentBalance = mutableData.getValue(Double::class.java) ?: 0.0
                mutableData.value = currentBalance + amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    // Success
                }
            }
        })
    }
}
