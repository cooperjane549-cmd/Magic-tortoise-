package co.ke.magictortoise.fragments

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import co.ke.magictortoise.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MarketFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var etRef: EditText
    
    // Configurable IDs
    private val TILL_NUMBER = "3043489"
    private val CASH_PHONE = "07XXXXXXXX" // The number where they send airtime/bonga

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_market, container, false)
        
        database = FirebaseDatabase.getInstance().reference
        etRef = view.findViewById(R.id.et_payment_ref)

        // Box 1: Airtime to Cash - "Turn Excess Airtime into Real Money"
        view.findViewById<View>(R.id.card_airtime_to_cash).setOnClickListener {
            showInstructionDialog(
                "Convert Airtime to Cash",
                "1. Dial *456# and select 'Transfer Airtime'.\n2. Send airtime to $CASH_PHONE.\n3. Copy the Safaricom confirmation SMS and paste it in the box below to get your cash!"
            )
        }

        // Box 2: Bonga to Cash - "Liquidate Your Points Instantly"
        view.findViewById<View>(R.id.card_bonga_to_cash).setOnClickListener {
            showInstructionDialog(
                "Convert Bonga to Cash",
                "1. Dial *126# and select 'Transfer Bonga Points'.\n2. Transfer points to $CASH_PHONE.\n3. Paste the confirmation SMS in the box below to claim your cash immediately!"
            )
        }

        // Box 3: Buy Airtime - "Get More for Less (2% Discount)"
        view.findViewById<View>(R.id.card_buy_airtime).setOnClickListener {
            showInstructionDialog(
                "Buy Discounted Airtime",
                "1. Go to M-PESA > Lipa na M-PESA > Buy Goods.\n2. Enter Till Number: $TILL_NUMBER.\n3. You pay 2% less (Pay 98 for 100 Airtime).\n4. Paste the M-PESA message below to receive your airtime."
            )
        }

        // Submit Button Logic
        view.findViewById<Button>(R.id.btn_submit_verification).setOnClickListener {
            val code = etRef.text.toString().trim()
            if (code.isNotEmpty()) {
                submitToFirebase(code)
            } else {
                Toast.makeText(context, "Please paste your confirmation message first", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun showInstructionDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("I UNDERSTAND") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun submitToFirebase(message: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        val request = mapOf(
            "userId" to userId,
            "message" to message,
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP
        )

        // Saves to 'verifications' node for your manual approval
        database.child("verifications").push().setValue(request)
            .addOnSuccessListener {
                etRef.text.clear()
                Toast.makeText(context, "Success! Your payment is being verified.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Submission failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
