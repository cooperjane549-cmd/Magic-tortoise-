package co.ke.magictortoise

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import co.ke.magictortoise.R

class MainActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private var adCount = 0
    private var shellBalance = 0.0
    private var myReferralCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvBalance: TextView = findViewById(R.id.tvBalance)
        val tvStageStatus: TextView = findViewById(R.id.tvStageStatus)
        val tvPayAmount: TextView = findViewById(R.id.tvPayAmount)
        val adProgressBar: ProgressBar = findViewById(R.id.adProgressBar)
        val backgroundVideo: VideoView = findViewById(R.id.backgroundVideo)
        val btnWatchAd: Button = findViewById(R.id.btnWatchAd)
        val btnApplyReferral: Button = findViewById(R.id.btnApplyReferral)
        val etReferralCode: EditText = findViewById(R.id.etReferralCode)
        val etAmount: EditText = findViewById(R.id.etAmount)

        // setup Video
        val path = "android.resource://" + packageName + "/" + R.raw.magic_bg
        backgroundVideo.setVideoURI(Uri.parse(path))
        backgroundVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            backgroundVideo.start()
        }

        // Login
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user?.uid ?: ""
            if (uid.isNotEmpty()) {
                myReferralCode = uid.takeLast(6).uppercase() 
                db.child("users").child(uid).child("referralCode").setValue(myReferralCode)
                
                db.child("users").child(uid).child("balance").get().addOnSuccessListener { snapshot ->
                    val bal = (snapshot.value as? Number)?.toDouble() ?: 0.0
                    shellBalance = bal
                    tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
                }
            }
        }

        btnWatchAd.setOnClickListener {
            adCount++
            adProgressBar.progress = adCount
            
            if (adCount == 15) {
                shellBalance += 1.0
                tvStageStatus.text = "Stage 2: 10 ads for KES 0.50"
            } else if (adCount == 25) {
                shellBalance += 0.5
                tvStageStatus.text = "Stage 3: 10 ads for KES 0.50 bonus"
            } else if (adCount == 35) {
                shellBalance += 0.5
                tvStageStatus.text = "Cycle Complete! KES 2.0 earned."
                adCount = 0
                adProgressBar.progress = 0
            }
            
            val uid = auth.currentUser?.uid
            if (uid != null) {
                db.child("users").child(uid).child("balance").setValue(shellBalance)
                tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
            }
        }

        btnApplyReferral.setOnClickListener {
            val code = etReferralCode.text.toString().trim().uppercase()
            val uid = auth.currentUser?.uid ?: ""

            if (code == myReferralCode) {
                Toast.makeText(this, "Self-referral blocked.", Toast.LENGTH_SHORT).show()
            } else if (code.isNotEmpty()) {
                db.child("users").orderByChild("referralCode").equalTo(code).get()
                    .addOnSuccessListener { snapshot ->
                        val referrerId = snapshot.children.firstOrNull()?.key
                        if (referrerId != null) {
                            db.child("users").child(uid).child("referredBy").setValue(referrerId)
                            btnApplyReferral.isEnabled = false
                            Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val amt = s.toString().toDoubleOrNull() ?: 0.0
                tvPayAmount.text = "You pay: KES ${String.format("%.2f", amt * 0.98)}"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}
