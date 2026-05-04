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
    // Removed the hardcoded DB_URL to prevent connection crashes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val recyclerView = findViewById<RecyclerView>(R.id.rv_admin_requests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize adapter with the list and the approval logic
        adapter = AdminAdapter(requestList) { request -> approveRequest(request) }
        recyclerView.adapter = adapter

        fetchData()
    }

    private fun fetchData() {
        // Use default instance - safer than a hardcoded string
        val rootRef = FirebaseDatabase.getInstance().reference
        val nodes = listOf("advertiser_requests", "exchange_offers", "mini_task_submissions")

        for (node in nodes) {
            rootRef.child(node).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Filter out old items from this specific node to prevent duplicates
                    requestList.removeAll { it.nodeSource == node }

                    for (child in snapshot.children) {
                        try {
                            val req = child.getValue(AdminRequest::class.java)
                            if (req != null && req.status == "pending") {
                                req.id = child.key ?: ""
                                req.nodeSource = node // Track which folder this came from
                                requestList.add(req)
                            }
                        } catch (e: Exception) {
                            // If one item is corrupted/null, skip it instead of crashing the app
                            continue 
                        }
                    }
                    // Use the safe update function from the Adapter
                    adapter.updateData(ArrayList(requestList))
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@AdminActivity, "DB Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun approveRequest(request: AdminRequest) {
        val db = FirebaseDatabase.getInstance().reference
        
        // 1. Update the status to 'Approved'
        db.child(request.nodeSource).child(request.id).child("status").setValue("Approved")
            .addOnSuccessListener {
                
                // 2. If it's a TikTok Task, pay the user KES 2.0
                if (request.nodeSource == "mini_task_submissions" && request.userId.isNotEmpty()) {
                    rewardUser(request.userId, 2.0)
                }
                
                Toast.makeText(this, "Success: Request Approved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Approval Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rewardUser(userId: String, amount: Double) {
        val userBalanceRef = FirebaseDatabase.getInstance().reference
            .child("users").child(userId).child("balance")

        userBalanceRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentBalance = mutableData.getValue(Double::class.java) ?: 0.0
                mutableData.value = currentBalance + amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                // Balance updated in Firebase
            }
        })
    }
}
