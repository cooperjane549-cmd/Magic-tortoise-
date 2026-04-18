package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MarketFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_market, container, false)

        val etAmount = root.findViewById<EditText>(R.id.etCustomAmount)
        val btnBuy = root.findViewById<Button>(R.id.btnBuyAirtime)

        btnBuy.setOnClickListener {
            val amountStr = etAmount.text.toString()
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (amount < 10.0) {
                Toast.makeText(context, "Minimum purchase is KES 10", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Calculate 2% Discount
            val discountedPrice = amount * 0.98
            
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            
            // Create Firebase Purchase Request for Daraja
            val purchaseRequest = mapOf(
                "uid" to uid,
                "airtimeValue" to amount,
                "payableAmount" to discountedPrice,
                "status" to "awaiting_stk",
                "timestamp" to System.currentTimeMillis()
            )

            db.child("buy_queue").push().setValue(purchaseRequest).addOnSuccessListener {
                Toast.makeText(context, "Requesting KES $discountedPrice via STK Push...", Toast.LENGTH_LONG).show()
                etAmount.text.clear()
            }
        }

        return root
    }
}
