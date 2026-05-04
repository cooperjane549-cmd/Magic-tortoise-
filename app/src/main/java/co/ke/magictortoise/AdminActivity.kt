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
    private lateinit var adapter: AdminAdapter // We will create this next
    private val requestList = mutableListOf<AdminRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        database = FirebaseDatabase.getInstance().reference
        recyclerView = findViewById(R.id.rv_admin_requests)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Initialize adapter with a click listener to approve tasks
        adapter = AdminAdapter(requestList) { request ->
            approveTask(request)
        }
        recyclerView.adapter = adapter

        loadRequests()
    }

    private fun loadRequests() {
        // This watches the 'advertiser_requests' node
        database.child("advertiser_requests").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                requestList.clear()
                for (data in snapshot.children) {
                    val req = data.getValue(AdminRequest::class.java)?.copy(id = data.key ?: "")
                    if (req != null && req.status == "pending") {
                        requestList.add(req)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun approveTask(request: AdminRequest) {
        // When you click Approve, it marks it 'active' so users can see it
        database.child("advertiser_requests").child(request.id).child("status").setValue("active")
            .addOnSuccessListener {
                Toast.makeText(this, "Task is now LIVE!", Toast.LENGTH_SHORT).show()
            }
    }
}
