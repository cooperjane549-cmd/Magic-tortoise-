package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class DashboardFragment : Fragment() {

    private var rewardedAd: RewardedAd? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private var shellBalance = 0.0
    private var currentAds = 0
    private val REWARDED_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)

        val tvBalance = root.findViewById<TextView>(R.id.tvBalance)
        val tvAdCount = root.findViewById<TextView>(R.id.tvAdCount)
        val progressBar = root.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = root.findViewById<Button>(R.id.btnWatchAd)

        // 1. Sync data from Firebase
        val uid = auth.currentUser?.uid ?: return root
        db.child("users").child(uid).get().addOnSuccessListener {
            shellBalance = (it.child("balance").value as? Number)?.toDouble() ?: 0.0
            currentAds = (it.child("currentCycleAds").value as? Number)?.toInt() ?: 0
            updateUI(tvBalance, tvAdCount, progressBar)
        }

        // 2. Load Ad
        loadAd()

        btnWatchAd.setOnClickListener {
            rewardedAd?.show(requireActivity()) { reward ->
                calculateRewardAndSync(uid, tvBalance, tvAdCount, progressBar)
            } ?: Toast.makeText(context, "Magic loading... try again", Toast.LENGTH_SHORT).show()
        }

        return root
    }

    private fun loadAd() {
        RewardedAd.load(requireContext(), REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
        })
    }

    private fun calculateRewardAndSync(uid: String, bal: TextView, count: TextView, bar: ProgressBar) {
        currentAds++
        
        // Tiered Logic: 35 Ads total for KES 2.00
        val increment = when {
            currentAds <= 15 -> 0.067 // Tier 1: 15 ads -> KES 1.00
            currentAds <= 25 -> 0.05  // Tier 2: 10 ads -> KES 0.50
            currentAds <= 35 -> 0.05  // Tier 3: 10 ads -> KES 0.50
            else -> {
                currentAds = 1 // Reset cycle
                0.067
            }
        }

        shellBalance += increment
        
        // Sync to Firebase
        val updates = mapOf(
            "balance" to shellBalance,
            "currentCycleAds" to currentAds
        )
        db.child("users").child(uid).updateChildren(updates)
        
        updateUI(bal, count, bar)
        loadAd() // Recursive load for next ad
    }

    private fun updateUI(bal: TextView, count: TextView, bar: ProgressBar) {
        bal.text = "KES ${String.format("%.2f", shellBalance)}"
        count.text = "$currentAds/35 Ads Watched"
        bar.progress = currentAds
    }
}
