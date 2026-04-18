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
        // Ensure this layout name matches your file name exactly
        val root = inflater.inflate(R.layout.fragment_market, container, false)

        val tvAmount = root.findViewById<TextView>(R.id.tvPurchaseAmount)
        val tvBal = root.findViewById<TextView>(R.id.tvRemainingBalance)
        val btnPlus = root.findViewById<ImageButton>(R.id.btnPlus)
        val btnMinus = root.findViewById<ImageButton>(R.id.btnMinus)
        val swipe = root.findViewById<Slider>(R.id.swipeConfirm)
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

        // Amount Control
        btnPlus?.setOnClickListener {
            currentAmount += 10.0
            tvAmount?.text = String.format("%.2f", currentAmount)
        }

        btnMinus?.setOnClickListener {
            if (currentAmount > 10.0) {
                currentAmount -= 10.0
                tvAmount?.text = String.format("%.2f", currentAmount)
            }
        }

        // Swipe Logic
        swipe?.addOnChangeListener { _, value, _ ->
            if (value >= 95f) {
                processPurchase(uid, rbMpesa?.isChecked ?: true)
                swipe.value = 0f // Reset slider
            }
        }

        return root
    }

    private fun processPurchase(uid: String, isMpesa: Boolean) {
        val finalPrice = if (isMpesa) currentAmount * 0.98 else currentAmount
        
        if (!isMpesa && userBalance < currentAmount) {
            Toast.makeText(requireContext(), "Insufficient Shells!", Toast.LENGTH_SHORT).show()
            return
        }

        val request = mapOf(
            "uid" to uid,
            "amount" to currentAmount,
            "priceCharged" to finalPrice,
            "method" to if (isMpesa) "MPESA" else "SHELLS",
            "timestamp" to System.currentTimeMillis(),
            "status" to "pending"
        )

        db.child("buy_queue").push().setValue(request).addOnSuccessListener {
            Toast.makeText(requireContext(), "Magic Order Sent!", Toast.LENGTH_LONG).show()
        }
    }
}
