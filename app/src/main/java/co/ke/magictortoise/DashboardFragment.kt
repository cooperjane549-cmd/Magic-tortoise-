package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class DashboardFragment : Fragment() {

    private var rewardedAd: RewardedAd? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var shellBalance = 0.0
    private var adCycle = 0

    // AD UNIT IDs (Currently using Test IDs so you can see them immediately)
    private val REWARDED_ID = "ca-app-pub-2344867686796379/1476405830"
    private val NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        val tvBalance = root.findViewById<TextView>(R.id.tvBalance)
        val tvAdCount = root.findViewById<TextView>(R.id.tvAdCount)
        val progressBar = root.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = root.findViewById<Button>(R.id.btnWatchAd)

        // Firebase Sync
        val uid = auth.currentUser?.uid ?: return root
        db.child("users").child(uid).get().addOnSuccessListener {
            shellBalance = (it.child("balance").value as? Number)?.toDouble() ?: 0.0
            adCycle = (it.child("adCycle").value as? Number)?.toInt() ?: 0
            updateUI(tvBalance, tvAdCount, progressBar)
        }

        // LOAD ADS
        loadRewardedAd()
        loadNativeAd(root)

        btnWatchAd.setOnClickListener {
            rewardedAd?.show(requireActivity()) { 
                processReward(uid, tvBalance, tvAdCount, progressBar) 
            } ?: Toast.makeText(context, "Ad magic is loading...", Toast.LENGTH_SHORT).show()
        }

        return root
    }

    private fun loadRewardedAd() {
        RewardedAd.load(requireContext(), REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
        })
    }

    private fun loadNativeAd(root: View) {
        val adLoader = AdLoader.Builder(requireContext(), NATIVE_ID)
            .forNativeAd { nativeAd ->
                val adView = root.findViewById<NativeAdView>(R.id.native_ad_view)
                adView.headlineView = adView.findViewById(R.id.ad_headline)
                adView.bodyView = adView.findViewById(R.id.ad_body)
                adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)

                (adView.headlineView as TextView).text = nativeAd.headline
                (adView.bodyView as TextView).text = nativeAd.body
                (adView.callToActionView as Button).text = nativeAd.callToAction
                adView.setNativeAd(nativeAd)
            }.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun processReward(uid: String, bal: TextView, count: TextView, bar: ProgressBar) {
        adCycle++
        // Tiered Math: 15 (KES 1) + 10 (KES 0.5) + 10 (KES 0.5)
        val amount = when {
            adCycle <= 15 -> 0.067
            adCycle <= 25 -> 0.05
            adCycle <= 35 -> 0.05
            else -> { adCycle = 1; 0.067 }
        }
        shellBalance += amount
        db.child("users").child(uid).updateChildren(mapOf("balance" to shellBalance, "adCycle" to adCycle))
        updateUI(bal, count, bar)
        loadRewardedAd() // Prepare next ad
    }

    private fun updateUI(bal: TextView, count: TextView, bar: ProgressBar) {
        bal.text = "KES ${String.format("%.2f", shellBalance)}"
        count.text = "Progress: $adCycle/35 Ads"
        bar.progress = adCycle
    }
}
