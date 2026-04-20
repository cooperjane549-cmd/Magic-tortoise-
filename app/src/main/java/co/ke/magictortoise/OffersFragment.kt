package co.ke.magictortoise

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class OffersFragment : Fragment(R.layout.fragment_offerwall) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference
    private var isPremium = false
    private var myReferralCode = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance().reference

        // Initialize UI Components
        val tvStatus = view.findViewById<TextView>(R.id.tvPremiumStatus)
        val tvMyCode = view.findViewById<TextView>(R.id.tvMyReferralCode)
        val btnShare = view.findViewById<Button>(R.id.btnShareCode)
        val etInput = view.findViewById<EditText>(R.id.etInputReferral)
        val btnClaim = view.findViewById<Button>(R.id.btnClaimReferral)
        val btnDaily = view.findViewById<Button>(R.id.btnDailyCheckIn)
        val rvLeaderboard = view.findViewById<RecyclerView>(R.id.rvLeaderboard)

        // Set up RecyclerView LayoutManager
        rvLeaderboard.layoutManager = LinearLayoutManager(context)

        val uid = auth.currentUser?.uid ?: return
        myReferralCode = uid.take(6).uppercase() 
        tvMyCode.text = myReferralCode

        // 1. Sync Premium Status (The KES 50 Gate)
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val totalSpent = snapshot.child("totalSpent").getValue(Double::class.java) ?: 0.0
                isPremium = totalSpent >= 50.0

                if (isPremium) {
                    tvStatus.text = "STATUS: PREMIUM ACTIVE"
                    tvStatus.setTextColor(resources.getColor(R.color.white, null))
                } else {
                    tvStatus.text = "STATUS: RESTRICTED"
                    tvStatus.setTextColor(resources.getColor(R.color.text_gray, null))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. Share Referral Code
        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join Magic Tortoise and earn airtime! My code: $myReferralCode")
            }
            startActivity(Intent.createChooser(shareIntent, "Share your code"))
        }

        // 3. Claim Referral Code
        btnClaim.setOnClickListener {
            val inputCode = etInput.text.toString().trim().uppercase()
            if (inputCode == myReferralCode) {
                Toast.makeText(context, "You cannot refer yourself!", Toast.LENGTH_SHORT).show()
            } else if (inputCode.length == 6) {
                db.child("users").child(uid).child("referredBy").setValue(inputCode)
                Toast.makeText(context, "Code applied! Bonus paid after 35 ads.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Invalid code format", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Daily Check-in Logic
        btnDaily.setOnClickListener {
            if (!isPremium) {
                Toast.makeText(context, "Please spend KES 50 in Market first!", Toast.LENGTH_LONG).show()
            } else {
                handleDailyCheckIn(uid)
            }
        }
    }

    private fun handleDailyCheckIn(uid: String) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val userRef = db.child("users").child(uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val lastCheck = mutableData.child("lastCheckIn").getValue(String::class.java) ?: ""
                
                if (lastCheck == today) {
                    return Transaction.abort() // Stop if already claimed today
                }

                val currentBalance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                mutableData.child("balance").value = currentBalance + 0.10
                mutableData.child("lastCheckIn").value = today
                
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    Toast.makeText(context, "Bonus claimed! +0.10 Shells", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Come back tomorrow!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}

