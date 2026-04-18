package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

class MarketFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var currentAmount = 100.0
    private var userBalance = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // This MUST match the name of your XML file (fragment_market)
        val root = inflater.inflate(R.layout.fragment_market, container, false)

        // 1. Link the UI components from your XML
        val tvAmount = root.findViewById<TextView>(R.id.tvPurchaseAmount)
        val tvBal = root.findViewById<TextView>(R.id.tvRemainingBalance)
        val btnPlus = root.findViewById<ImageButton>(R.id.btnPlus)
        val btnMinus = root.findViewById<ImageButton>(R.id.btnMinus)
        val swipe = root.findViewById<Slider>(R.id.swipeConfirm)
        val rbMpesa = root.findViewById<RadioButton>(R.id.rbMpesa)

        val uid = auth.currentUser?.uid ?: return root

        // 2. Real-time Balance Sync (The "Magic" part)
        db.child("users").child(uid).child("balance").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userBalance = snapshot.getValue(Double::class.java) ?: 0.0
                tvBal.text = "Balance: ${String.format("%.2f", userBalance)} SHELLS"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. Amount Adjustment Logic
        btnPlus.setOnClickListener {
            currentAmount += 10.0
            tvAmount.text = String.format("%.2f", currentAmount)
        }

        btnMinus.setOnClickListener {
            if (currentAmount > 10.0) {
                currentAmount -= 10.0
                tvAmount.text = String.format("%.2f", currentAmount)
            }
        }

        // 4. Swipe to Confirm Logic
        swipe.addOnChangeListener { _, value, _ ->
            if (value >= 95f) {
                processPurchase(uid, rbMpesa.isChecked)
                swipe.value = 0f // Reset slider after use
            }
        }

        return root
    }

    private fun processPurchase(uid: String, isMpesa: Boolean) {
        val finalPrice = if (isMpesa) currentAmount * 0.98 else currentAmount
        
        // Safety check if using Shells
        if (!isMpesa && userBalance < currentAmount) {
            Toast.makeText(context, "Insufficient Shells!", Toast.LENGTH_SHORT).show()
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

        // Pushing the "Bug" to Firebase as requested
        db.child("buy_queue").push().setValue(request).addOnSuccessListener {
            Toast.makeText(context, "Magic Order Placed!", Toast.LENGTH_LONG).show()
        }
    }
}
