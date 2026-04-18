package co.ke.magictortoise

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
    
    private var adsWatched = 0
    private var shellBalance = 0.0
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}
        loadRewardedAd()

        val tvBalance = findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = findViewById<Button>(R.id.btnWatchAd)
        val btnBuy = findViewById<Button>(R.id.btnBuy)
        val etAmount = findViewById<EditText>(R.id.etAmount)

        // Load User Data from Firebase
        val uid = auth.currentUser?.uid ?: "anonymous"
        db.child("users").child(uid).get().addOnSuccessListener {
            shellBalance = (it.child("balance").value as? Number)?.toDouble() ?: 0.0
            adsWatched = (it.child("adsWatched").value as? Number)?.toInt() ?: 0
            
            updateUI(tvBalance, tvAdProgress, adProgressBar)
        }

        btnWatchAd.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(this) { processReward(tvBalance, tvAdProgress, adProgressBar) }
            } else {
                Toast.makeText(this, "Searching for magic... wait.", Toast.LENGTH_SHORT).show()
                loadRewardedAd()
            }
        }

        btnBuy.setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (amount < 3.0) {
                Toast.makeText(this, "Minimum is KES 3.00", Toast.LENGTH_SHORT).show()
            } else if (amount > shellBalance) {
                Toast.makeText(this, "Not enough shells!", Toast.LENGTH_SHORT).show()
            } else {
                // Send Request to Firebase Withdrawals API
                val withdrawalRequest = mapOf(
                    "uid" to uid,
                    "amount" to amount,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )
                db.child("withdrawals").push().setValue(withdrawalRequest).addOnSuccessListener {
                    shellBalance -= amount
                    db.child("users").child(uid).child("balance").setValue(shellBalance)
                    updateUI(tvBalance, tvAdProgress, adProgressBar)
                    etAmount.text.clear()
                    Toast.makeText(this, "Magic is happening! Airtime processing.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUI(balance: TextView, progressText: TextView, bar: ProgressBar) {
        balance.text = "KES ${String.format("%.2f", shellBalance)}"
        progressText.text = "Ads Watched: $adsWatched/15"
        bar.progress = adsWatched % 16
    }

    private fun loadRewardedAd() {
        RewardedAd.load(this, AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
        })
    }

    private fun processReward(balance: TextView, progressText: TextView, bar: ProgressBar) {
        adsWatched++
        shellBalance += 0.07 // Payout per ad
        
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid).child("balance").setValue(shellBalance)
        db.child("users").child(uid).child("adsWatched").setValue(adsWatched)
        
        updateUI(balance, progressText, bar)
        loadRewardedAd()
    }
}
