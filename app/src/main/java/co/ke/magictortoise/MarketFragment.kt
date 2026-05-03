package co.ke.magictortoise.fragments

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_market, container, false)
        
        database = FirebaseDatabase.getInstance().reference
        etRef = view.findViewById(R.id.et_payment_ref)

        // Tap to Copy Till Number
        view.findViewById<TextView>(R.id.tv_till_info).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("Till Number", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Till Number 3043489 copied!", Toast.LENGTH_SHORT).show()
        }

        // Feature Hype Clickers (Shows instructions)
        view.findViewById<View>(R.id.card_airtime_to_cash).setOnClickListener {
            Toast.makeText(context, "Sell airtime at 70% value. Send to 0712345678", Toast.LENGTH_LONG).show()
        }

        // Submit for Verification
        view.findViewById<Button>(R.id.btn_submit_verification).setOnClickListener {
            val refCode = etRef.text.toString().trim()
            if (refCode.isNotEmpty()) {
                submitPayment(refCode)
            } else {
                Toast.makeText(context, "Please paste your M-Pesa message or code", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun submitPayment(code: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        val paymentData = mapOf(
            "refCode" to code,
            "userId" to userId,
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP
        )

        // Sending to a node called "verifications" for your Admin to see
        database.child("verifications").push().setValue(paymentData)
            .addOnSuccessListener {
                etRef.text.clear()
                Toast.makeText(context, "Submitted! We are verifying your payment.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Submission failed. Check internet.", Toast.LENGTH_SHORT).show()
            }
    }
}
