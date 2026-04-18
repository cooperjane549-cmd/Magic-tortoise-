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

class MarketFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var currentAmount = 100.0
    private var userBalance = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_market, container, false)

        val tvAmount = root.findViewById<TextView>(R.id.tvPurchaseAmount)
        val tvBal = root.findViewById<TextView>(R.id.tvRemainingBalance)
        val swipe = root.findViewById<Slider>(R.id.swipeConfirm)
        val rbMpesa = root.findViewById<RadioButton>(R.id.rbMpesa)

        // Sync Balance for UI
        val uid = auth.currentUser?.uid ?: return root
        db.child("users").child(uid).child("balance").get().addOnSuccessListener {
            userBalance = it.getValue(Double::class.java) ?: 0.0
            tvBal.text = "Balance: ${String.format("%.2f", userBalance)} SHELLS"
        }

        root.findViewById<ImageButton>(R.id.btnPlus).setOnClickListener {
            currentAmount += 10.0
            tvAmount.text = String.format("%.2f", currentAmount)
        }

        root.findViewById<ImageButton>(R.id.btnMinus).setOnClickListener {
            if (currentAmount > 10.0) {
                currentAmount -= 10.0
                tvAmount.text = String.format("%.2f", currentAmount)
            }
        }

        // Swipe Action
        swipe.addOnChangeListener { _, value, _ ->
            if (value >= 95f) {
                processPurchase(uid, rbMpesa.isChecked)
                swipe.value = 0f // Reset slider
            }
        }

        return root
    }

    private fun processPurchase(uid: String, isMpesa: Boolean) {
        val finalPrice = if (isMpesa) currentAmount * 0.98 else currentAmount
        
        if (!isMpesa && userBalance < currentAmount) {
            Toast.makeText(context, "Insufficient Shells", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Request Sent Successfully", Toast.LENGTH_SHORT).show()
        }
    }
}
