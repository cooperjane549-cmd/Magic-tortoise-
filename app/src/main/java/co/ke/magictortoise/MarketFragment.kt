package co.ke.magictortoise.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import co.ke.magictortoise.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class MarketFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private val myHustleNumber = "0712345678" // FIXME: PUT YOUR ACTUAL WORK SIM NUMBER HERE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_market, container, false)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance().getReference("conversions")

        // Bind Views
        val etTransId = view.findViewById<EditText>(R.id.et_trans_id)
        val btnVerify = view.findViewById<Button>(R.id.btn_verify)
        val tvNumber = view.findViewById<TextView>(R.id.tv_hustle_number)

        // UI Setup
        tvNumber.text = "Send to: $myHustleNumber (Tap to copy)"

        // 1. Logic: Copy Number to Clipboard
        tvNumber.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Hustle Number", myHustleNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Number $myHustleNumber copied!", Toast.LENGTH_SHORT).show()
        }

        // 2. Logic: Handle Submission
        btnVerify.setOnClickListener {
            val transId = etTransId.text.toString().trim().uppercase()
            val userId = FirebaseAuth.getInstance().currentUser?.uid

            if (transId.isEmpty()) {
                etTransId.error = "Ref code is required"
                return@setOnClickListener
            }

            if (userId != null) {
                submitToFirebase(userId, transId, etTransId)
            } else {
                Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun submitToFirebase(userId: String, transId: String, editText: EditText) {
        // Create the Data Object
        val claim = HashMap<String, Any>()
        claim["userId"] = userId
        claim["transactionId"] = transId
        claim["status"] = "pending" // You will change this to 'approved' manually in console
        claim["timestamp"] = ServerValue.TIMESTAMP

        // Path: conversions / pending_claims / [TRANS_ID]
        database.child("pending_claims").child(transId).setValue(claim)
            .addOnSuccessListener {
                Toast.makeText(context, "Submitted! We will verify $transId shortly.", Toast.LENGTH_LONG).show()
                editText.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to submit. Check internet.", Toast.LENGTH_SHORT).show()
            }
    }
}
