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
    private val requestList = mutableListOf<AdminRequest>()
    private lateinit var adapter: AdminAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        // FIXED: Pointing specifically to your Database URL from the screenshot
        val databaseUrl = "https://magic-tortoise-default-rtdb.firebaseio.com/"
        database = FirebaseDatabase.getInstance(databaseUrl).getReference("advertiser_requests")

        recyclerView = findViewById(R.id.rv_admin_requests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AdminAdapter(requestList) { request ->
            approveRequest(request)
        }
        recyclerView.adapter = adapter

        fetchData()
    }

    private fun fetchData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                requestList.clear()
                for (child in snapshot.children) {
                    val request = child.getValue(AdminRequest::class.java)
                    if (request != null) {
                        // Manually setting the ID from the Firebase key
                        val updatedRequest = request.copy(id = child.key ?: "")
                        
                        // Check if status is pending (lowercase must match Firebase)
                        if (updatedRequest.status == "pending") {
                            requestList.add(updatedRequest)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
                
                if (requestList.isEmpty()) {
                    Toast.makeText(this@AdminActivity, "No pending requests found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // This will now give you a specific error if the URL or UID is still wrong
                Toast.makeText(this@AdminActivity, "Firebase Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun approveRequest(request: AdminRequest) {
        database.child(request.id).child("status").setValue("Active")
            .addOnSuccessListener {
                Toast.makeText(this, "Task is now LIVE!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
