package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MarketFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var currentAmount = 100.0
    private var userBalance = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_market, container, false)

        // Connect to your XML IDs
        val tvAmount = root.findViewById<TextView>(R.id.tvPurchaseAmount)
        val tvBal = root.findViewById<TextView>(R.id.tvRemainingBalance)
        val btnPlus = root.findViewById<ImageButton>(R.id.btnPlus)
        val btnMinus = root.findViewById<ImageButton>(R.id.btnMinus)
        val swipeConfirm = root.findViewById<Slider>(R.id.swipeConfirm)
        val rbMpesa = root.findViewById<RadioButton>(R.id.rbMpesa)

        val uid = auth.currentUser?.uid ?: return root

        // Sync Balance from Firebase
        db.child("users").child(uid).child("balance").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userBalance = snapshot.getValue(Double::class.java) ?: 0.0
                tvBal?.text = "Balance: ${String.format("%.2f", userBalance)} SHELLS"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Plus Button
        btnPlus?.setOnClickListener {
            currentAmount += 10.0
            tvAmount?.text = String.format("%.2f", currentAmount)
        }

        // Minus Button
        btnMinus?.setOnClickListener {
            if (currentAmount > 10.0) {
                currentAmount -= 10.0
                tvAmount?.text = String.format("%.2f", currentAmount)
            }
        }

        // Slider (Swipe to Confirm) Logic
        swipeConfirm?.addOnChangeListener { _, value, _ ->
            if (value >= 95f) {
                // If swiped to the end, process order
                val isMpesa = rbMpesa?.isChecked ?: true
                processOrder(uid, isMpesa)
                
                // Reset slider back to start
                swipeConfirm.value = 0f
            }
        }

        return root
    }

    private fun processOrder(uid: String, isMpesa: Boolean) {
        if (!isMpesa && userBalance < currentAmount) {
            Toast.makeText(context, "Insufficient Shells!", Toast.LENGTH_SHORT).show()
            return
        }

        val order = mapOf(
            "uid" to uid,
            "amount" to currentAmount,
            "method" to if (isMpesa) "MPESA" else "SHELLS",
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP
        )

        db.child("buy_queue").push().setValue(order).addOnSuccessListener {
            Toast.makeText(context, "Magic Order Sent Successfully!", Toast.LENGTH_LONG).show()
        }
    }
}
