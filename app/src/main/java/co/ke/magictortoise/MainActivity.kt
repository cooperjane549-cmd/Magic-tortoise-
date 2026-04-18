package co.ke.magictortoise

import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private var rewardedAd: RewardedAd? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private var shellBalance = 0.0
    private var adSessionCount = 0 // Tracks the 0/15
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // RESTORED: Background Video Setup
        setupVideoBackground()

        MobileAds.initialize(this) {}
        loadRewardedAd()

        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = findViewById<Button>(R.id.btnWatchAd)
        val btnBuy = findViewById<Button>(R.id.btnBuy)
        val etAmount = findViewById<EditText>(R.id.etAmount)

        // Sync with Firebase
        val uid = auth.currentUser?.uid ?: "anon"
        db.child("users").child(uid).get().addOnSuccessListener {
            shellBalance = (it.child("balance").value as? Number)?.toDouble() ?: 0.0
            updateDisplay(tvBalance, tvAdProgress, adProgressBar)
        }

        btnWatchAd.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(this) { processReward(tvBalance, tvAdProgress, adProgressBar) }
            } else {
                Toast.makeText(this, "Ad is loading...", Toast.LENGTH_SHORT).show()
                loadRewardedAd()
            }
        }

        // REDEEM Logic with KES 3 Minimum
        btnBuy.setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (amount < 3.0) {
                Toast.makeText(this, "Minimum withdrawal is KES 3.00", Toast.LENGTH_SHORT).show()
            } else if (amount > shellBalance) {
                Toast.makeText(this, "Insufficient Shell Balance", Toast.LENGTH_SHORT).show()
            } else {
                // Submit to Firebase Withdrawal Queue
                val request = mapOf(
                    "uid" to uid,
                    "amount" to amount,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis()
                )
                db.child("withdrawals").push().setValue(request).addOnSuccessListener {
                    shellBalance -= amount
                    db.child("users").child(uid).child("balance").setValue(shellBalance)
                    updateDisplay(tvBalance, tvAdProgress, adProgressBar)
                    etAmount.text.clear()
                    Toast.makeText(this, "Redemption Sent to Admin!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupVideoBackground() {
        val videoView = findViewById<VideoView>(R.id.backgroundVideo)
        val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.magic_bg)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { 
            it.isLooping = true
            it.setVolume(0f, 0f)
            videoView.start() 
        }
    }

    private fun updateDisplay(balance: TextView, progressTxt: TextView, bar: ProgressBar) {
        balance.text = "KES ${String.format("%.2f", shellBalance)}"
        progressTxt.text = "Ads Progress: ${adSessionCount % 15}/15"
        bar.progress = adSessionCount % 16
    }

    private fun loadRewardedAd() {
        RewardedAd.load(this, AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
        })
    }

    private fun processReward(balance: TextView, progressTxt: TextView, bar: ProgressBar) {
        adSessionCount++
        shellBalance += 0.07 // KES 1.05 per 15 ads
        
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid).child("balance").setValue(shellBalance)
        
        updateDisplay(balance, progressTxt, bar)
        loadRewardedAd()
    }
}
