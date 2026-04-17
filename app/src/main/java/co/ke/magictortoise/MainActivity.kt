package co.ke.magictortoise

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.ke.magictortoise.R // THIS WAS THE MISSING KEY
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private var adCount = 0
    private var shellBalance = 0.0
    private var myReferralCode = ""

    private lateinit var tvBalance: TextView
    private lateinit var tvStageStatus: TextView
    private lateinit var tvPayAmount: TextView
    private lateinit var adProgressBar: ProgressBar
    private lateinit var backgroundVideo: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvBalance = findViewById(R.id.tvBalance)
        tvStageStatus = findViewById(R.id.tvStageStatus)
        tvPayAmount = findViewById(R.id.tvPayAmount)
        adProgressBar = findViewById(R.id.adProgressBar)
        backgroundVideo = findViewById(R.id.backgroundVideo)

        val btnWatchAd: Button = findViewById(R.id.btnWatchAd)
        val btnApplyReferral: Button = findViewById(R.id.btnApplyReferral)
        val etReferralCode: EditText = findViewById(R.id.etReferralCode)
        val etAmount: EditText = findViewById(R.id.etAmount)

        setupVideoBackground()
        loginAnonymously() 

        btnWatchAd.setOnClickListener {
            adCount += 1
            adProgressBar.progress = adCount
            
            val statusMessage = when (adCount) {
                15 -> {
                    updateBalance(1.0)
                    "Stage 2: 10 ads for KES 0.50"
                }
                25 -> {
                    updateBalance(0.5)
                    "Stage 3: 10 ads for KES 0.50 bonus"
                }
                35 -> {
                    updateBalance(0.5)
                    checkAndRewardReferrer()
                    val cycleMsg = "Cycle Complete! KES 2.0 earned."
                    adCount = 0
                    adProgressBar.progress = 0
                    cycleMsg
                }
                else -> null
            }
            if (statusMessage != null) {
                tvStageStatus.text = statusMessage
            }
        }

        btnApplyReferral.setOnClickListener {
            val code = etReferralCode.text.toString().trim().uppercase()
            val user = auth.currentUser
            
            if (user == null) return@setOnClickListener

            if (code == myReferralCode) {
                Toast.makeText(this, "You cannot refer yourself!", Toast.LENGTH_SHORT).show()
            } else {
                db.child("users").orderByChild("referralCode").equalTo(code).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val referrerId = snapshot.children.firstOrNull()?.key
                            if (referrerId != null) {
                                db.child("users").child(user.uid).child("referredBy").setValue(referrerId)
                                btnApplyReferral.isEnabled = false
                                etReferralCode.isEnabled = false
                                Toast.makeText(this, "Magic Code Applied!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Invalid Code.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val inputStr = s.toString()
                val amt = inputStr.toDoubleOrNull() ?: 0.0
                val total = amt * 0.98
                val display = "You pay: KES ${String.format("%.2f", total)}"
                tvPayAmount.text = display
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loginAnonymously() {
        auth.signInAnonymously().addOnSuccessListener { result ->
            val user = result.user ?: return@addOnSuccessListener
            val uid = user.uid
            myReferralCode = uid.takeLast(6).uppercase() 
            db.child("users").child(uid).child("referralCode").setValue(myReferralCode)
            
            db.child("users").child(uid).child("balance").get().addOnSuccessListener { snapshot ->
                val bal = snapshot.value
                if (bal is Number) {
                    shellBalance = bal.toDouble()
                    val balText = "KES ${String.format("%.2f", shellBalance)}"
                    tvBalance.text = balText
                }
            }
        }
    }

    private fun updateBalance(amount: Double) {
        val user = auth.currentUser ?: return
        shellBalance += amount
        db.child("users").child(user.uid).child("balance").setValue(shellBalance)
        val balText = "KES ${String.format("%.2f", shellBalance)}"
        tvBalance.text = balText
    }

    private fun checkAndRewardReferrer() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        db.child("users").child(uid).child("hasCompletedFirstCycle").get().addOnSuccessListener { snap ->
            if (snap.value != true) {
                db.child("users").child(uid).child("referredBy").get().addOnSuccessListener { refSnap ->
                    val rUid = refSnap.value as? String
                    if (rUid != null) {
                        db.child("users").child(rUid).child("balance").get().addOnSuccessListener { bSnap ->
                            val current = (bSnap.value as? Number)?.toDouble() ?: 0.0
                            db.child("users").child(rUid).child("balance").setValue(current + 1.0)
                            db.child("users").child(uid).child("hasCompletedFirstCycle").setValue(true)
                        }
                    }
                }
            }
        }
    }

    private fun setupVideoBackground() {
        try {
            val path = "android.resource://" + packageName + "/" + R.raw.magic_bg
            backgroundVideo.setVideoURI(Uri.parse(path))
            backgroundVideo.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                backgroundVideo.start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onResume() {
        super.onResume()
        backgroundVideo.start()
    }
}
