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
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AdminAdapter(requestList) { request -> 
            approveRequest(request) 
        }
        recyclerView.adapter = adapter

        fetchData()
    }

    private fun fetchData() {
        val rootRef = FirebaseDatabase.getInstance().reference
        
        // Added 'deposit_requests' and 'data_requests' to the list
        val nodes = listOf(
            "advertiser_requests", 
            "mini_task_submissions", 
            "deposit_requests", 
            "data_requests"
        )

        for (node in nodes) {
            rootRef.child(node).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    requestList.removeAll { it.nodeSource == node }

                    for (child in snapshot.children) {
                        try {
                            val req = child.getValue(AdminRequest::class.java)
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

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun approveRequest(request: AdminRequest) {
        if (request.id.isEmpty() || request.nodeSource.isEmpty()) {
            Toast.makeText(this, "Error: Invalid Request", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseDatabase.getInstance().reference
        
        // Determine the correct status string based on the node
        val finalStatus = when (request.nodeSource) {
            "advertiser_requests" -> "Active"
            "data_requests" -> "Completed"
            else -> "Approved"
        }

        db.child(request.nodeSource).child(request.id).child("status").setValue(finalStatus)
            .addOnSuccessListener {
                handlePostApprovalActions(request)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handlePostApprovalActions(request: AdminRequest) {
        when (request.nodeSource) {
            "mini_task_submissions" -> {
                // User earned KES 2.0 for TikTok follow
                rewardUser(request.userId, 2.0, "Task Reward Paid")
            }
            "deposit_requests" -> {
                // IMPORTANT: For deposits, you'll need to check the amount. 
                // For now, let's assume a default or manually handled reward.
                // In a real scenario, you might add an 'amount' field to AdminRequest.
                rewardUser(request.userId, 0.0, "Deposit Confirmed! (Manually add balance if needed)")
            }
            "advertiser_requests" -> {
                Toast.makeText(this, "Promotion is now LIVE!", Toast.LENGTH_SHORT).show()
            }
            "data_requests" -> {
                Toast.makeText(this, "Data Request marked as Sent!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rewardUser(userId: String, amount: Double, successMsg: String) {
        if (userId.isEmpty()) return

        val userBalanceRef = FirebaseDatabase.getInstance().reference
            .child("users").child(userId).child("balance")

        userBalanceRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentBalance = mutableData.getValue(Double::class.java) ?: 0.0
                mutableData.value = currentBalance + amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                runOnUiThread {
                    Toast.makeText(this@AdminActivity, successMsg, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
