package co.ke.magictortoise

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class OffersFragment : Fragment() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val WITHDRAWAL_FEE = 5.0 // Your revenue fee

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_offers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnDeposit = view.findViewById<Button>(R.id.btnDeposit)
        val btnWithdraw = view.findViewById<Button>(R.id.btnWithdraw)
        val tvReferral = view.findViewById<TextView>(R.id.tvReferralCode)
        val btnInvite = view.findViewById<Button>(R.id.btnInvite)

        val uid = auth.currentUser?.uid ?: return
        val myReferralCode = uid.take(6).uppercase()
        tvReferral.text = "YOUR CODE: $myReferralCode"

        // 1. DEPOSIT: Open PesaPal Link
        btnDeposit.setOnClickListener {
            val url = "https://store.pesapal.com/magictortoise"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        // 2. WITHDRAWAL: Show Pop-up
        btnWithdraw.setOnClickListener {
            showWithdrawDialog(uid)
        }

        // 3. REFERRAL: Share Link
        btnInvite.setOnClickListener {
            val shareText = "Win daily on Magic Tortoise! Use my code $myReferralCode to join: https://t.me/magictortoise" 
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Invite via"))
        }
    }

    private fun showWithdrawDialog(uid: String) {
        val etAmount = EditText(context).apply { 
            hint = "Amount to Withdraw"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }

        AlertDialog.Builder(context)
            .setTitle("Withdraw Cash")
            .setMessage("Note: A KES $WITHDRAWAL_FEE processing fee applies.")
            .setView(etAmount)
            .setPositiveButton("Withdraw") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (amount < 50.0) {
                    Toast.makeText(context, "Min withdrawal is 50/-", Toast.LENGTH_SHORT).show()
                } else {
                    processWithdrawal(uid, amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processWithdrawal(uid: String, amount: Double) {
        val totalToDeduct = amount + WITHDRAWAL_FEE
        
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                if (balance < totalToDeduct) return Transaction.abort()
                
                mutableData.child("balance").value = balance - totalToDeduct
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    val request = mapOf(
                        "uid" to uid,
                        "amount" to amount,
                        "fee" to WITHDRAWAL_FEE,
                        "status" to "pending",
                        "time" to ServerValue.TIMESTAMP
                    )
                    db.child("withdrawals").push().setValue(request)
                    Toast.makeText(context, "Request Sent! You will receive KES $amount", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Insufficient balance (need amount + 5/- fee)", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
