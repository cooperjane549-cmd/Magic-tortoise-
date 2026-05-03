package co.ke.magictortoise.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import co.ke.magictortoise.R
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var tvBalance: TextView
    private var currentBalance: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) return view

        database = FirebaseDatabase.getInstance().reference
        tvBalance = view.findViewById(R.id.tv_dashboard_balance)

        // Listen for Balance Updates
        val balanceRef = database.child("users").child(userId).child("balance")
        balanceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentBalance = snapshot.getValue(Double::class.java) ?: 0.0
                tvBalance.text = String.format("KES %.2f", currentBalance)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Setup Data Buttons
        view.findViewById<MaterialCardView>(R.id.btn_buy_20mb).setOnClickListener {
            handlePurchase(20, 10.0, userId)
        }
        view.findViewById<MaterialCardView>(R.id.btn_buy_250mb).setOnClickListener {
            handlePurchase(250, 50.0, userId)
        }
        view.findViewById<MaterialCardView>(R.id.btn_buy_1gb).setOnClickListener {
            handlePurchase(1000, 180.0, userId)
        }

        // Ad Button (Example)
        view.findViewById<MaterialCardView>(R.id.btn_watch_ad).setOnClickListener {
            Toast.makeText(context, "Loading Ad...", Toast.LENGTH_SHORT).show()
            // Your AdMob Logic Here
        }

        return view
    }

    private fun handlePurchase(mb: Int, price: Double, userId: String) {
        if (currentBalance >= price) {
            // 1. Deduct Balance
            val newBalance = currentBalance - price
            database.child("users").child(userId).child("balance").setValue(newBalance)

            // 2. Create a request for your Firebase Cloud Function to call the Data API
            val requestRef = database.child("data_requests").push()
            val requestData = mapOf(
                "userId" to userId,
                "amountMB" to mb,
                "status" to "pending",
                "phone" to FirebaseAuth.getInstance().currentUser?.phoneNumber // Ensure user has phone
            )
            requestRef.setValue(requestData)

            Toast.makeText(context, "Purchase Successful! Injecting $mb MB...", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Insufficient Balance. Convert Airtime to add cash!", Toast.LENGTH_LONG).show()
        }
    }
}
