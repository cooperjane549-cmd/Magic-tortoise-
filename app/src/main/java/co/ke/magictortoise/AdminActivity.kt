package co.ke.magictortoise

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class AdminActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdminAdapter
    private val requestList = mutableListOf<AdminRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        database = FirebaseDatabase.getInstance().reference
        recyclerView = findViewById(R.id.rv_admin_requests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AdminAdapter(requestList) { request ->
            approveTask(request)
        }
        recyclerView.adapter = adapter

        loadPendingRequests()
    }

    private fun loadPendingRequests() {
        // We look specifically in advertiser_requests
        database.child("advertiser_requests").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                requestList.clear()
                for (data in snapshot.children) {
                    val req = data.getValue(AdminRequest::class.java)?.copy(id = data.key ?: "")
                    // Only show items that are actually 'pending'
                    if (req != null && req.status == "pending") {
                        requestList.add(req)
                    }
                }
                adapter.notifyDataSetChanged()
                
                if (requestList.isEmpty()) {
                    Toast.makeText(this@AdminActivity, "No pending tasks", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminActivity, "Database Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun approveTask(request: AdminRequest) {
        // This is how you make it LIVE: change status to "Active"
        database.child("advertiser_requests").child(request.id).child("status").setValue("Active")
            .addOnSuccessListener {
                Toast.makeText(this, "Verified! Task is now LIVE for users.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to approve.", Toast.LENGTH_SHORT).show()
            }
    }
}
