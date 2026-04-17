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
    
    private var adCount = 0
    private var shellBalance = 0.0
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}
        loadRewardedAd()

        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        val tvUserPhone = findViewById<TextView>(R.id.tvUserPhone)
        val btnWatchAd = findViewById<Button>(R.id.btnWatchAd)
        val btnBuy = findViewById<Button>(R.id.btnBuy)
        val etAmount = findViewById<EditText>(R.id.etAmount)

        // Setup background
        setupVideoBackground()

        // 1. Authenticate and Get Registered Data
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user?.uid ?: return@addOnSuccessListener
            
            // Look for User Info in Firebase
            db.child("users").child(uid).get().addOnSuccessListener { snapshot ->
                shellBalance = (snapshot.child("balance").value as? Number)?.toDouble() ?: 0.0
                val phone = snapshot.child("phone").value?.toString() ?: "No Number Linked"
                
                tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
                tvUserPhone.text = "Linked: $phone"
            }
        }

        btnWatchAd.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(this) { processReward() }
            } else {
                Toast.makeText(this, "Fetching Magic... Wait a second.", Toast.LENGTH_SHORT).show()
                loadRewardedAd()
            }
        }

        // 2. Withdrawal Logic (The Firebase "API")
        btnBuy.setOnClickListener {
            val amountStr = etAmount.text.toString()
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            if (amount < 5.0) {
                Toast.makeText(this, "Minimum withdrawal is KES 5.00", Toast.LENGTH_SHORT).show()
            } else if (amount > shellBalance) {
                Toast.makeText(this, "Insufficient Shell Balance", Toast.LENGTH_SHORT).show()
            } else {
                // SEND TO FIREBASE "API" (Withdrawal Queue)
                val request = mapOf(
                    "amount" to amount,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis()
                )
                
                db.child("withdrawals").child(uid).push().setValue(request).addOnSuccessListener {
                    // Subtract from local balance
                    shellBalance -= amount
                    db.child("users").child(uid).child("balance").setValue(shellBalance)
                    tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
                    etAmount.text.clear()
                    Toast.makeText(this, "Request Sent! Processing Airtime...", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupVideoBackground() {
        val videoView = findViewById<VideoView>(R.id.backgroundVideo)
        val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.magic_bg)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { it.isLooping = true; it.setVolume(0f, 0f); videoView.start() }
    }

    private fun loadRewardedAd() {
        RewardedAd.load(this, AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null }
        })
    }

    private fun processReward() {
        adCount++
        shellBalance += 0.07 // 15 ads = KES 1.05
        
        findViewById<TextView>(R.id.tvBalance).text = "KES ${String.format("%.2f", shellBalance)}"
        findViewById<ProgressBar>(R.id.adProgressBar).progress = adCount % 16
        
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid).child("balance").setValue(shellBalance)
        loadRewardedAd()
    }
}
