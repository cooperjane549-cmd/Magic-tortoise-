package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardFragment : Fragment() {

    private var rewardedAd: RewardedAd? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    // YOUR IDS
    private val REWARD_ID = "ca-app-pub-2344867686796379/1476405830"
    private val NATIVE_ID = "ca-app-pub-3940256099942544/2247696110" // Use Test ID first to verify space

    private var balance = 0.0
    private var adCycle = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        val tvBalance = root.findViewById<TextView>(R.id.tvBalance)
        val tvProgress = root.findViewById<TextView>(R.id.tvAdProgress)
        val progressBar = root.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatch = root.findViewById<Button>(R.id.btnWatchAd)
        val cardOffers = root.findViewById<View>(R.id.cardOfferWalls)

        val uid = auth.currentUser?.uid ?: return root

        // FIREBASE SYNC & PROOF LISTENER
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                tvBalance.text = String.format("%.2f", balance)
                tvProgress.text = "Progress: $adCycle/35"
                progressBar.progress = adCycle
            }
            override fun onCancelled(p0: DatabaseError) {}
        })

        loadRewardedAd()
        loadNativeAd(root)

        btnWatch.setOnClickListener {
            rewardedAd?.show(requireActivity()) { 
                val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
                val reward = if (nextCycle <= 15) 0.067 else 0.05
                
                // Writing Proof to Firebase
                val updates = HashMap<String, Any>()
                updates["users/$uid/balance"] = balance + reward
                updates["users/$uid/adCycle"] = nextCycle
                db.updateChildren(updates).addOnSuccessListener { loadRewardedAd() }
            }
        }

        cardOffers.setOnClickListener {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(requireContext(), Uri.parse("https://your-offerwall-link.com/?uid=$uid"))
        }

        return root
    }

    private fun loadRewardedAd() {
        RewardedAd.load(requireContext(), REWARD_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
        })
    }

    private fun loadNativeAd(root: View) {
        val adLoader = AdLoader.Builder(requireContext(), NATIVE_ID)
            .forNativeAd { nativeAd ->
                val adView = root.findViewById<NativeAdView>(R.id.native_ad_view)
                adView.visibility = View.VISIBLE
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
}
