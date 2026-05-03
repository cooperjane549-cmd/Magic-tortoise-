package co.ke.magictortoise

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class AdminActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    // We will use a simple list of Map to hold the data
    private val requestList = mutableListOf<DataSnapshot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        database = FirebaseDatabase.getInstance().reference
        recyclerView = findViewById(R.id.rv_admin_requests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // For a beginner, we'll use a simple listener to see all "mini_task_submissions"
        loadPendingTasks()
    }

    private fun loadPendingTasks() {
        // Listen for all proofs submitted by users
        database.child("mini_task_submissions").orderByChild("status").equalTo("pending")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    requestList.clear()
                    for (data in snapshot.children) {
                        requestList.add(data)
                    }
                    // Normally you'd use a RecyclerView Adapter here, 
                    // but for now, let's just toast how many items you have
                    Toast.makeText(this@AdminActivity, "Found ${requestList.size} items", Toast.LENGTH_SHORT).show()
                    
                    // TODO: Connect to an Adapter to show the actual cards
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // This function will be called when you click "Approve"
    private fun approveUserMoney(userId: String, amount: Double, requestId: String) {
        val userRef = database.child("users").child(userId).child("balance")
        
        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val current = mutableData.getValue(Double::class.java) ?: 0.0
                mutableData.value = current + amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, commited: Boolean, snp: DataSnapshot?) {
                if (commited) {
                    // Mark as approved in your list
                    database.child("mini_task_submissions").child(requestId).child("status").setValue("approved")
                    Toast.makeText(this@AdminActivity, "Paid KES $amount to User", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
