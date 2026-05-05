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

        // 1. Initialize UI components
        val recyclerView = findViewById<RecyclerView>(R.id.rv_admin_requests)
        
        // 2. Set LayoutManager first to avoid immediate crash on data load
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 3. Initialize Adapter with the list and approve/pay logic
        adapter = AdminAdapter(requestList) { request -> 
            approveRequest(request) 
        }
        recyclerView.adapter = adapter

        // 4. Start listening to Firebase
        fetchData()
    }

    private fun fetchData() {
        val rootRef = FirebaseDatabase.getInstance().reference
        // Listening to all three business nodes
        val nodes = listOf("advertiser_requests", "exchange_offers", "mini_task_submissions")

        for (node in nodes) {
            rootRef.child(node).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Remove items from this specific node to prevent list duplication
                    requestList.removeAll { it.nodeSource == node }

                    for (child in snapshot.children) {
                        try {
                            // Map Firebase data to our AdminRequest model
                            val req = child.getValue(AdminRequest::class.java)
                            
                            // Only show items that are still "pending"
                            if (req != null && (req.status == "pending" || req.status.isNullOrEmpty())) {
                                req.id = child.key ?: ""
                                req.nodeSource = node
                                requestList.add(req)
                            }
                        } catch (e: Exception) {
                            // If one entry is formatted wrong (old data), skip it instead of crashing
                            continue 
                        }
                    }

                    // CRITICAL: Update the UI on the Main Thread to prevent "crushing"
                    runOnUiThread {
                        adapter.updateData(ArrayList(requestList))
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    runOnUiThread {
                        Toast.makeText(this@AdminActivity, "Database Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun approveRequest(request: AdminRequest) {
        if (request.id.isEmpty() || request.nodeSource.isEmpty()) {
            Toast.makeText(this, "Error: Invalid Request ID", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseDatabase.getInstance().reference
        
        // 1. Update the status in the specific node (e.g., mini_task_submissions)
        db.child(request.nodeSource).child(request.id).child("status").setValue("Approved")
            .addOnSuccessListener {
                
                // 2. If it's a TikTok Task, pay the user KES 2.0 automatically
                if (request.nodeSource == "mini_task_submissions" && !request.userId.isNullOrEmpty()) {
                    rewardUser(request.userId, 2.0)
                }
                
                Toast.makeText(this, "Success: Approved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rewardUser(userId: String, amount: Double) {
        val userBalanceRef = FirebaseDatabase.getInstance().reference
            .child("users").child(userId).child("balance")

        // Use a Transaction to safely add money without overwriting current balance
        userBalanceRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentBalance = mutableData.getValue(Double::class.java) ?: 0.0
                mutableData.value = currentBalance + amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                // Balance update finished
            }
        })
    }
}
