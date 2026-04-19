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
import com.google.firebase.database.*

class DashboardFragment : Fragment() {

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        val btnWatchAd = root.findViewById<Button>(R.id.btnWatchAd)

        // Force an ad load as soon as the view is created
        loadAd(root)

        btnWatchAd?.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(requireActivity()) { 
                    Toast.makeText(it.context, "Reward Earned!", Toast.LENGTH_SHORT).show()
                    rewardedAd = null
                    loadAd(root)
                }
            } else {
                // This notification MUST show now
                Toast.makeText(root.context, "Ad is loading... try in 3 seconds", Toast.LENGTH_SHORT).show()
                if (!isAdLoading) loadAd(root)
            }
        }

        return root
    }

    private fun loadAd(view: View) {
        if (isAdLoading) return
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(view.context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                isAdLoading = false
                // This will tell us the ad is actually ready
                Toast.makeText(view.context, "Magic Ready!", Toast.LENGTH_SHORT).show()
            }

            override fun onAdFailedToLoad(e: LoadAdError) {
                rewardedAd = null
                isAdLoading = false
                // This tells us EXACTLY why ads aren't working (e.g., Network Error, No Fill)
                Toast.makeText(view.context, "Ad Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}
