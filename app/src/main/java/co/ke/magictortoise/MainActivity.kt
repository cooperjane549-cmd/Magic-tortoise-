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
        loginAnonymously() 
        setupAdLogic()
        setupPurchaseLogic()
        setupReferralButton() // This was missing - now included
    }

    private fun loginAnonymously() {
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user?.uid ?: ""
            // Create a unique 6-digit code based on their User ID
            myReferralCode = uid.takeLast(6).uppercase() 
            
            // Register this user's code in the DB so others can use it
            db.child("users").child(uid).child("referralCode").setValue(myReferralCode)
            
            loadUserBalance(uid)
        }.addOnFailureListener {
            Toast.makeText(this, "Connection Failed. Check Internet.", Toast.LENGTH_SHORT).show()
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
                    checkAndRewardReferrer() 
                    adCount = 0
                    binding.adProgressBar.progress = 0
                }
            }
        }
    }

    private fun updateBalance(amount: Double, status: String) {
        val uid = auth.currentUser?.uid ?: return
        shellBalance += amount
        db.child("users").child(uid).child("balance").setValue(shellBalance)
        binding.tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
        binding.tvStageStatus.text = status
    }

    private fun setupReferralButton() {
        binding.btnApplyReferral.setOnClickListener {
            val enteredCode = binding.etReferralCode.text.toString().trim().uppercase()
            val myUid = auth.currentUser?.uid ?: return@setOnClickListener

            if (enteredCode == myReferralCode) {
                Toast.makeText(this, "You cannot refer yourself!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Look for the user who owns the entered code
            db.child("users").orderByChild("referralCode").equalTo(enteredCode).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val referrerUid = snapshot.children.first().key
                        // Save who referred the current user
                        db.child("users").child(myUid).child("referredBy").setValue(referrerUid)
                        
                        binding.btnApplyReferral.isEnabled = false
                        binding.etReferralCode.isEnabled = false
                        Toast.makeText(this, "Magic Code Applied!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid Code.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun checkAndRewardReferrer() {
        val uid = auth.currentUser?.uid ?: return
        
        db.child("users").child(uid).child("hasCompletedFirstCycle").get().addOnSuccessListener {
            val alreadyFinished = it.value as? Boolean ?: false
            
            if (!alreadyFinished) {
                db.child("users").child(uid).child("referredBy").get().addOnSuccessListener { refSnap ->
                    val referrerUid = refSnap.value as? String
                    if (referrerUid != null) {
                        rewardReferrer(referrerUid)
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
