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

        database = FirebaseDatabase.getInstance().reference.child("advertiser_requests")
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
                        request.id = child.key ?: ""
                        // Only show if status is pending
                        if (request.status == "pending") {
                            requestList.add(request)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
                if(requestList.isEmpty()) {
                    Toast.makeText(this@AdminActivity, "No pending requests found", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun approveRequest(request: AdminRequest) {
        database.child(request.id).child("status").setValue("Active")
            .addOnSuccessListener {
                Toast.makeText(this, "Task is now LIVE!", Toast.LENGTH_SHORT).show()
            }
    }
}
