package co.ke.magictortoise

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class OfferwallFragment : Fragment(R.layout.fragment_offerwall) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var isPremium = false
    private var myReferralCode = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStatus = view.findViewById<TextView>(R.id.tvPremiumStatus)
        val tvMyCode = view.findViewById<TextView>(R.id.tvMyReferralCode)
        val btnShare = view.findViewById<Button>(R.id.btnShareCode)
        val etInput = view.findViewById<EditText>(R.id.etInputReferral)
        val btnClaim = view.findViewById<Button>(R.id.btnClaimReferral)
        val btnDaily = view.findViewById<Button>(R.id.btnDailyCheckIn)

        val uid = auth.currentUser?.uid ?: return
        myReferralCode = uid.take(6).uppercase() // First 6 chars of UID
        tvMyCode.text = myReferralCode

        // 1. Sync Premium Status & Referral Info
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val totalSpent = snapshot.child("totalSpent").getValue(Double::class.java) ?: 0.0
                isPremium = totalSpent >= 50.0

                if (isPremium) {
                    tvStatus.text = "STATUS: PREMIUM ACTIVE"
                    tvStatus.setTextColor(resources.getColor(R.color.white, null))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. Share Referral Code
        btnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, "Join Magic Tortoise and earn airtime! Use my code: $myReferralCode")
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        // 3. Claim Referral (Welcome Bonus)
        btnClaim.setOnClickListener {
            val code = etInput.text.toString().trim().uppercase()
            if (code == myReferralCode) {
                Toast.makeText(context, "You cannot refer yourself!", Toast.LENGTH_SHORT).show()
            } else if (code.length == 6) {
                db.child("users").child(uid).child("referredBy").setValue(code)
                Toast.makeText(context, "Code saved! Bonus released after 35 ads.", Toast.LENGTH_LONG).show()
            }
        }

        // 4. Daily Check-in (Locked by Premium Gate)
        btnDaily.setOnClickListener {
            if (!isPremium) {
                Toast.makeText(context, "Buy KES 50 airtime to unlock Daily Bonus!", Toast.LENGTH_SHORT).show()
            } else {
                processDailyCheckIn(uid)
            }
        }
    }

    private fun processDailyCheckIn(uid: String) {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val ref = db.child("users").child(uid)

        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val last = data.child("lastCheckIn").getValue(String::class.java) ?: ""
                if (last == today) return Transaction.abort()

                val bal = data.child("balance").getValue(Double::class.java) ?: 0.0
                data.child("balance").value = bal + 0.10
                data.child("lastCheckIn").value = today
                return Transaction.success(data)
            }

            override fun onComplete(err: DatabaseError?, comm: Boolean, snap: DataSnapshot?) {
                if (comm) Toast.makeText(context, "0.10 Shells Claimed!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Already claimed today!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
