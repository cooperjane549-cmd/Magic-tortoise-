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
    private val TILL_NUMBER = "3043489"
    private val CASH_PHONE = "07XXXXXXXX" // Replace this later

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_market, container, false)
        
        database = FirebaseDatabase.getInstance().reference
        etRef = view.findViewById(R.id.et_payment_ref)

        // Feature 1: Airtime to Cash
        view.findViewById<View>(R.id.card_airtime_to_cash).setOnClickListener {
            showInstructionDialog(
                "Convert Airtime to Cash",
                "1. Dial *456# or use your SIM toolkit.\n2. Send your airtime to $CASH_PHONE.\n3. After you receive the Safaricom confirmation, copy the message and paste it below."
            )
        }

        // Feature 2: Bonga to Cash
        view.findViewById<View>(R.id.card_bonga_to_cash).setOnClickListener {
            showInstructionDialog(
                "Convert Bonga to Cash",
                "1. Dial *126# and select 'Transfer Bonga Points'.\n2. Transfer points to $CASH_PHONE.\n3. Paste the confirmation SMS in the box below to claim your cash."
            )
        }

        // Feature 3: Buy Cheap Airtime
        view.findViewById<View>(R.id.card_buy_airtime).setOnClickListener {
            showInstructionDialog(
                "Buy Discounted Airtime",
                "1. Pay via M-Pesa to Till Number: $TILL_NUMBER.\n2. You pay 2% less (e.g., pay 98 for 100).\n3. Paste the M-Pesa message below to receive your airtime instantly."
            )
        }

        // Submit for Verification
        view.findViewById<Button>(R.id.btn_submit_verification).setOnClickListener {
            val code = etRef.text.toString().trim()
            if (code.isNotEmpty()) {
                submitToFirebase(code)
            } else {
                Toast.makeText(context, "Please paste your message first", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun showInstructionDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext(), R.style.Theme_MaterialComponents_Dialog_Alert)
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

        // Push to a node called "verifications"
        database.child("verifications").push().setValue(request)
            .addOnSuccessListener {
                etRef.text.clear()
                showInstructionDialog("Success!", "Your request has been sent for manual approval. Check your balance in 5-15 minutes.")
            }
            .addOnFailureListener { e ->
                // This shows the actual error from Firebase to help us debug
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
