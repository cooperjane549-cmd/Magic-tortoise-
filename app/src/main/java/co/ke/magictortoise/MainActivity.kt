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

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private var adCount = 0
    private var shellBalance = 0.0
    private var myReferralCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupVideoBackground()
        loginAnonymously() 
        setupAdLogic()
        setupPurchaseLogic()
        setupReferralButton()
    }

    private fun loginAnonymously() {
        auth.signInAnonymously().addOnSuccessListener { result ->
            val user = result.user
            if (user != null) {
                val uid = user.uid
                myReferralCode = uid.takeLast(6).uppercase() 
                db.child("users").child(uid).child("referralCode").setValue(myReferralCode)
                loadUserBalance(uid)
            }
        }
    }

    private fun loadUserBalance(uid: String) {
        db.child("users").child(uid).child("balance").get().addOnSuccessListener { snapshot ->
            val value = snapshot.value
            if (value is Number) {
                shellBalance = value.toDouble()
                binding.tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
            }
        }
    }

    private fun setupAdLogic() {
        binding.btnWatchAd.setOnClickListener {
            adCount += 1
            binding.adProgressBar.progress = adCount
            
            if (adCount == 15) {
                updateBalance(1.0, "Stage 2: 10 ads for KES 0.50")
            } else if (adCount == 25) {
                updateBalance(0.5, "Stage 3: 10 ads for KES 0.50 bonus")
            } else if (adCount == 35) {
                updateBalance(0.5, "Cycle Complete! KES 2.0 earned.")
                checkAndRewardReferrer() 
                adCount = 0
                binding.adProgressBar.progress = 0
            }
        }
    }

    private fun updateBalance(amount: Double, status: String) {
        val user = auth.currentUser ?: return
        shellBalance += amount
        db.child("users").child(user.uid).child("balance").setValue(shellBalance)
        binding.tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
        binding.tvStageStatus.text = status
    }

    private fun setupReferralButton() {
        binding.btnApplyReferral.setOnClickListener {
            val inputCode = binding.etReferralCode.text.toString().trim().uppercase()
            val user = auth.currentUser ?: return@setOnClickListener

            if (inputCode == myReferralCode) {
                Toast.makeText(this, "You cannot refer yourself!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.child("users").orderByChild("referralCode").equalTo(inputCode).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val referrerId = snapshot.children.firstOrNull()?.key
                        if (referrerId != null) {
                            db.child("users").child(user.uid).child("referredBy").setValue(referrerId)
                            binding.btnApplyReferral.isEnabled = false
                            binding.etReferralCode.isEnabled = false
                            Toast.makeText(this, "Magic Code Applied!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Invalid Code.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun checkAndRewardReferrer() {
        val user = auth.currentUser ?: return
        db.child("users").child(user.uid).child("hasCompletedFirstCycle").get().addOnSuccessListener { snapshot ->
            val finished = snapshot.value as? Boolean ?: false
            if (!finished) {
                db.child("users").child(user.uid).child("referredBy").get().addOnSuccessListener { refSnap ->
                    val refUid = refSnap.value as? String
                    if (refUid != null) {
                        rewardReferrer(refUid)
                        db.child("users").child(user.uid).child("hasCompletedFirstCycle").setValue(true)
                    }
                }
            }
        }
    }

    private fun rewardReferrer(refUid: String) {
        db.child("users").child(refUid).child("balance").get().addOnSuccessListener { snapshot ->
            val current = (snapshot.value as? Number)?.toDouble() ?: 0.0
            db.child("users").child(refUid).child("balance").setValue(current + 1.0)
        }
    }

    private fun setupPurchaseLogic() {
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val inputStr = s.toString()
                val amt = inputStr.toDoubleOrNull() ?: 0.0
                val total = amt * 0.98
                binding.tvPayAmount.text = "You pay: KES ${String.format("%.2f", total)}"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupVideoBackground() {
        val path = "android.resource://" + packageName + "/" + R.raw.magic_bg
        binding.backgroundVideo.setVideoURI(Uri.parse(path))
        binding.backgroundVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            binding.backgroundVideo.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
