package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MarketFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var shellBalance = 0.0
    private var userPhone = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_market, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etUserNumber = view.findViewById<EditText>(R.id.etUserNumber)
        val btnSaveNumber = view.findViewById<Button>(R.id.btnSaveNumber)
        val etBuyAmount = view.findViewById<EditText>(R.id.etBuyAmount)
        val btnBuyMpesa = view.findViewById<Button>(R.id.btnBuyWithMpesa)
        val tvShells = view.findViewById<TextView>(R.id.tvCurrentShells)
        val etRedeemAmount = view.findViewById<EditText>(R.id.etRedeemAmount)
        val btnRedeem = view.findViewById<Button>(R.id.btnRedeemAirtime)

        val uid = auth.currentUser?.uid ?: return

        // 1. Sync User Data
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                shellBalance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                userPhone = snapshot.child("phone").getValue(String::class.java) ?: ""
                
                tvShells.text = "Available: ${String.format("%.2f", shellBalance)} Shells"
                if (userPhone.isNotEmpty()) etUserNumber.setText(userPhone)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. Save Phone Number
        btnSaveNumber.setOnClickListener {
            val phone = etUserNumber.text.toString().trim()
            if (phone.length < 10) {
                Toast.makeText(context, "Invalid number format", Toast.LENGTH_SHORT).show()
            } else {
                db.child("users").child(uid).child("phone").setValue(phone)
                Toast.makeText(context, "Payout number updated!", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Market: Buy with M-Pesa
        btnBuyMpesa.setOnClickListener {
            val amountStr = etBuyAmount.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toInt()
                if (amount in 5..500) {
                    if (userPhone.isEmpty()) {
                        Toast.makeText(context, "Please save your number first", Toast.LENGTH_SHORT).show()
                    } else {
                        val toPay = amount * 0.98
                        Toast.makeText(context, "Initiating STK Push for KES $toPay", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "M-Pesa Buy: 5 - 500 KES", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Redeem: Shells to Airtime
        btnRedeem.setOnClickListener {
            val redeemStr = etRedeemAmount.text.toString()
            if (redeemStr.isEmpty()) {
                Toast.makeText(context, "Enter amount to redeem", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amountToRedeem = redeemStr.toDouble()

            if (amountToRedeem < 3.0) {
                Toast.makeText(context, "Minimum redeem is KES 3.00", Toast.LENGTH_SHORT).show()
            } else if (shellBalance < amountToRedeem) {
                Toast.makeText(context, "Insufficient Shells!", Toast.LENGTH_SHORT).show()
            } else if (userPhone.isEmpty()) {
                Toast.makeText(context, "Save your phone number first!", Toast.LENGTH_SHORT).show()
            } else {
                processRedeem(uid, amountToRedeem)
            }
        }
    }

    private fun processRedeem(uid: String, amount: Double) {
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val current = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                if (current < amount) return Transaction.abort()
                mutableData.child("balance").value = current - amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(err: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {
                if (committed) {
                    val request = mapOf(
                        "uid" to uid,
                        "phone" to userPhone,
                        "amount" to amount,
                        "type" to "REDEEM_SHELLS",
                        "status" to "pending"
                    )
                    db.child("airtime_requests").push().setValue(request)
                    Toast.makeText(context, "Redeem of $amount Shells successful!", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
}
