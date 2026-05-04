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
    // Your exact Database URL from the screenshot
    private val DB_URL = "https://magic-tortoise-default-rtdb.firebaseio.com/"

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
        val rootRef = FirebaseDatabase.getInstance(DB_URL).reference
        
        // We listen to the two main "Market" nodes
        val nodes = listOf("advertiser_requests", "exchange_offers")

        for (node in nodes) {
            rootRef.child(node).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Filter existing items from this specific node before adding new ones
                    val iterator = requestList.iterator()
                    while (iterator.hasNext()) {
                        if (nodes.contains(node)) iterator.remove() 
                    }

                    for (child in snapshot.children) {
                        val req = child.getValue(AdminRequest::class.java)
                        if (req != null && req.status == "pending") {
                            req.id = child.key ?: ""
                            // If type is missing, we label it by the folder name
                            if (req.type.isEmpty()) req.type = node
                            
                            if (!requestList.any { it.id == req.id }) {
                                requestList.add(req)
                            }
                        }
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@AdminActivity, "Firebase Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun approveRequest(request: AdminRequest) {
        // Determine which node to update based on the request content
        val node = if (request.socialLink.isNotEmpty()) "advertiser_requests" else "exchange_offers"
        
        FirebaseDatabase.getInstance(DB_URL).reference
            .child(node).child(request.id).child("status").setValue("Active")
            .addOnSuccessListener {
                Toast.makeText(this, "Request Approved!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Approval Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
