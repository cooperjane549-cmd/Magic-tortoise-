package co.ke.magictortoise

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.ke.magictortoise.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private var adCount = 0
    private var shellBalance = 0.0
    private var myReferralCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupVideoBackground()
        loginAnonymously() // Start the cloud connection
        setupAdLogic()
        setupPurchaseLogic()
    }

    private fun loginAnonymously() {
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user?.uid ?: ""
            myReferralCode = uid.takeLast(6).uppercase() // Create a short code
            loadUserBalance(uid)
        }
    }

    private fun loadUserBalance(uid: String) {
        db.child("users").child(uid).child("balance").get().addOnSuccessListener {
            shellBalance = (it.value as? Double) ?: 0.0
            binding.tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
        }
    }

    private fun setupAdLogic() {
        binding.btnWatchAd.setOnClickListener {
            adCount++
            binding.adProgressBar.progress = adCount
            
            when (adCount) {
                15 -> updateBalance(1.0, "Stage 2: 10 ads for KES 0.50")
                25 -> updateBalance(0.5, "Stage 3: 10 ads for KES 0.50 bonus")
                35 -> {
                    updateBalance(0.5, "Cycle Complete! KES 2.0 earned.")
                    checkAndRewardReferrer() // The "Magic" trigger
                    adCount = 0
                    binding.adProgressBar.progress = 0
                }
            }
        }
    }

    private fun updateBalance(amount: Double, status: String) {
        val uid = auth.currentUser?.uid ?: return
        shellBalance += amount
        // Save to Firebase immediately so they don't lose money
        db.child("users").child(uid).child("balance").setValue(shellBalance)
        binding.tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
        binding.tvStageStatus.text = status
    }

    private fun checkAndRewardReferrer() {
        val uid = auth.currentUser?.uid ?: return
        
        // Check if this is the user's first time finishing the 35 ads
        db.child("users").child(uid).child("hasCompletedFirstCycle").get().addOnSuccessListener {
            val alreadyFinished = it.value as? Boolean ?: false
            
            if (!alreadyFinished) {
                // Look up who referred this user
                db.child("users").child(uid).child("referredBy").get().addOnSuccessListener { refSnap ->
                    val referrerUid = refSnap.value as? String
                    if (referrerUid != null) {
                        // PAY THE REFERRER KES 1.00
                        rewardReferrer(referrerUid)
                        // Mark this user as "Finished" so referrer isn't paid twice
                        db.child("users").child(uid).child("hasCompletedFirstCycle").setValue(true)
                    }
                }
            }
        }
    }

    private fun rewardReferrer(referrerUid: String) {
        db.child("users").child(referrerUid).child("balance").get().addOnSuccessListener {
            val oldBalance = (it.value as? Double) ?: 0.0
            db.child("users").child(referrerUid).child("balance").setValue(oldBalance + 1.0)
            Toast.makeText(this, "Referral Bonus Processed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPurchaseLogic() {
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toDoubleOrNull() ?: 0.0
                val pay = input * 0.98
                binding.tvPayAmount.text = "You pay: KES ${String.format("%.2f", pay)}"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupVideoBackground() {
        val videoPath = "android.resource://" + packageName + "/" + R.raw.magic_bg
        val uri = Uri.parse(videoPath)
        binding.backgroundVideo.setVideoURI(uri)
        binding.backgroundVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            binding.backgroundVideo.start()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.backgroundVideo.start()
    }
}
