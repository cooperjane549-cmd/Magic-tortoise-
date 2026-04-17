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

        // Initialize Ads
        MobileAds.initialize(this) {}
        loadRewardedAd()

        // UI References
        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        val tvStageStatus = findViewById<TextView>(R.id.tvStageStatus)
        val adProgressBar = findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = findViewById<Button>(R.id.btnWatchAd)
        val backgroundVideo = findViewById<VideoView>(R.id.backgroundVideo)

        // Video Background
        val path = "android.resource://" + packageName + "/" + R.raw.magic_bg
        backgroundVideo.setVideoURI(Uri.parse(path))
        backgroundVideo.setOnPreparedListener { it.isLooping = true; it.setVolume(0f,0f); backgroundVideo.start() }

        // Fetch User Data
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user?.uid ?: return@addOnSuccessListener
            db.child("users").child(uid).child("balance").get().addOnSuccessListener {
                shellBalance = (it.value as? Number)?.toDouble() ?: 0.0
                tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
            }
        }

        btnWatchAd.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(this) { rewardItem ->
                    // THE BRAKES: Only triggered if ad is finished
                    processReward()
                }
            } else {
                Toast.makeText(this, "Ad is loading, try again...", Toast.LENGTH_SHORT).show()
                loadRewardedAd()
            }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
        })
    }

    private fun processReward() {
        adCount++
        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        val tvStageStatus = findViewById<TextView>(R.id.tvStageStatus)
        val adProgressBar = findViewById<ProgressBar>(R.id.adProgressBar)

        // Real Math Logic
        if (adCount <= 15) {
            shellBalance += 0.07 // 15 ads approx KES 1.00
            tvStageStatus.text = "Stage 1: Progress $adCount/15"
        } else {
            shellBalance += 0.05
            tvStageStatus.text = "Bonus Stage active!"
        }

        adProgressBar.progress = adCount
        tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
        
        // Sync to Firebase
        val uid = auth.currentUser?.uid
        if (uid != null) db.child("users").child(uid).child("balance").setValue(shellBalance)
        
        loadRewardedAd() // Load next ad immediately
    }
}
