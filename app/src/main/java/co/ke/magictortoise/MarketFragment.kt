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

        val etAmount = root.findViewById<EditText>(R.id.etBuyAmount)
        val btnBuy = root.findViewById<Button>(R.id.btnPurchase)

        btnBuy.setOnClickListener {
            val amountStr = etAmount.text.toString()
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (amount < 10.0) {
                Toast.makeText(context, "Minimum is KES 10", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val request = mapOf("uid" to uid, "amount" to amount, "status" to "pending")
            db.child("buy_queue").push().setValue(request).addOnSuccessListener {
                Toast.makeText(context, "STK Push Sent!", Toast.LENGTH_SHORT).show()
                etAmount.text.clear()
            }
        }
        return root
    }
}
