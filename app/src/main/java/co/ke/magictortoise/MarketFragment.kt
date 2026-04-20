package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import okhttp3.*
import okhttp3.FormBody
import java.io.IOException

class MarketFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var shellBalance = 0.0
    private var userPhone = ""
    
    // OkHttp client for talking to your PHP server
    private val client = OkHttpClient()

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

        // 1. Sync User Data (STAYS THE SAME)
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

        // 2. Save Phone Number (STAYS THE SAME)
        btnSaveNumber.setOnClickListener {
            val phone = etUserNumber.text.toString().trim()
            if (phone.length < 10) {
                Toast.makeText(context, "Invalid number format", Toast.LENGTH_SHORT).show()
            } else {
                db.child("users").child(uid).child("phone").setValue(phone)
                Toast.makeText(context, "Payout number updated!", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Market: Buy with M-Pesa (NOW TRIGGERING PHP)
        btnBuyMpesa.setOnClickListener {
            val amountStr = etBuyAmount.text.toString()
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toInt()
                if (amount in 5..500) {
                    if (userPhone.isEmpty()) {
                        Toast.makeText(context, "Please save your number first", Toast.LENGTH_SHORT).show()
                    } else {
                        // Triggers your PHP script on the free server
                        triggerMpesaPush(userPhone, amount.toString())
                    }
                } else {
                    Toast.makeText(context, "M-Pesa Buy: 5 - 500 KES", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Redeem: Shells to Airtime (STAYS THE SAME)
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

    private fun triggerMpesaPush(phone: String, amount: String) {
        // Convert 07... to 254...
        val formattedPhone = if (phone.startsWith("0")) "254" + phone.substring(1) else phone
        
        val formBody = FormBody.Builder()
            .add("phone", formattedPhone)
            .add("amount", amount)
            .build()

        val request = Request.Builder()
            .url("https://magictortoise.xo.je/stk_push.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "STK Push Failed: Server Unreachable", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                activity?.runOnUiThread {
                    if (responseData?.contains("MerchantRequestID") == true) {
                        Toast.makeText(context, "PIN Prompt Sent to $formattedPhone", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "M-Pesa Busy. Try Again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
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
                    Toast.makeText(context, "Redeem request sent!", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
}
