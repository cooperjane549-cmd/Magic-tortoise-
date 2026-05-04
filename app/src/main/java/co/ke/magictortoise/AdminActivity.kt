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
    private val DB_URL = "https://magic-tortoise-default-rtdb.firebaseio.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val recyclerView = findViewById<RecyclerView>(R.id.rv_admin_requests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AdminAdapter(requestList) { request -> approveRequest(request) }
        recyclerView.adapter = adapter

        fetchData()
    }

    private fun fetchData() {
        val rootRef = FirebaseDatabase.getInstance(DB_URL).reference
        val nodes = listOf("advertiser_requests", "exchange_offers")

        for (node in nodes) {
            rootRef.child(node).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Remove old items from this specific node to prevent duplicates
                    requestList.removeAll { it.type == node || (it.type.isEmpty() && node == "advertiser_requests") }

                    for (child in snapshot.children) {
                        val req = child.getValue(AdminRequest::class.java)
                        if (req != null && req.status == "pending") {
                            req.id = child.key ?: ""
                            req.type = node
                            requestList.add(req)
                        }
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@AdminActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun approveRequest(request: AdminRequest) {
        val node = if (request.type.isNotEmpty()) request.type else "advertiser_requests"
        
        FirebaseDatabase.getInstance(DB_URL).reference
            .child(node).child(request.id).child("status").setValue("Active")
            .addOnSuccessListener {
                Toast.makeText(this, "Approved successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
