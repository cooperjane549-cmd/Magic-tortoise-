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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val recyclerView = findViewById<RecyclerView>(R.id.rv_admin_requests)
        
        // Ensure LayoutManager is set before data arrives
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Adapter
        adapter = AdminAdapter(requestList) { request -> 
            approveRequest(request) 
        }
        recyclerView.adapter = adapter

        fetchData()
    }

    private fun fetchData() {
        val rootRef = FirebaseDatabase.getInstance().reference
        val nodes = listOf("advertiser_requests", "exchange_offers", "mini_task_submissions")

        for (node in nodes) {
            rootRef.child(node).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    requestList.removeAll { it.nodeSource == node }

                    for (child in snapshot.children) {
                        try {
                            val req = child.getValue(AdminRequest::class.java)
                            
                            // Load if status is pending or empty
                            if (req != null && (req.status == "pending" || req.status.isNullOrEmpty())) {
                                req.id = child.key ?: ""
                                req.nodeSource = node
                                requestList.add(req)
                            }
                        } catch (e: Exception) {
                            continue 
                        }
                    }

                    runOnUiThread {
                        adapter.updateData(ArrayList(requestList))
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    runOnUiThread {
                        Toast.makeText(this@AdminActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun approveRequest(request: AdminRequest) {
        if (request.id.isEmpty() || request.nodeSource.isEmpty()) {
            Toast.makeText(this, "Error: Invalid Request", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseDatabase.getInstance().reference
        
        // --- LOGIC SYNC ---
        // For advertiser_requests (Paid Promo), we set status to "Active" so it shows in the app market.
        // For mini_task_submissions (User proof), we set status to "Approved".
        val finalStatus = if (request.nodeSource == "advertiser_requests") "Active" else "Approved"

        db.child(request.nodeSource).child(request.id).child("status").setValue(finalStatus)
            .addOnSuccessListener {
                
                // If it's a Proof Submission, reward the user KES 2.0
                if (request.nodeSource == "mini_task_submissions" && !request.userId.isNullOrEmpty()) {
                    rewardUser(request.userId, 2.0)
                    Toast.makeText(this, "Proof Approved! KES 2.0 Paid.", Toast.LENGTH_SHORT).show()
                } else if (request.nodeSource == "advertiser_requests") {
                    Toast.makeText(this, "Promotion is now LIVE (Active)!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Request Approved", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                // Balance updated successfully
            }
        })
    }
}
