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
    
    // YOUR IDs
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"
    private val NATIVE_ID = "ca-app-pub-3940256099942544/2247696110" 

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

        // FIREBASE SYNC
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                tvBalance.text = String.format("%.2f", balance)
                tvProgress.text = "Daily Progress: $adCycle/35"
                progressBar.progress = adCycle
            }
            override fun onCancelled(p0: DatabaseError) {}
        })

        loadAd()
        loadNativeAd(root)

        btnWatch.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(requireActivity()) { 
                    updateReward(uid) 
                }
            } else {
                // BETTER NOTIFICATION: Tells user it's actually working/loading
                Toast.makeText(requireContext(), "Magic is loading... please wait 5 seconds", Toast.LENGTH_SHORT).show()
                loadAd() // Try to reload if it was null
            }
        }

        cardOffers?.setOnClickListener {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(requireContext(), Uri.parse("https://your-offerwall-link.com/?uid=$uid"))
        }

        return root
    }

    private fun loadAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(requireContext(), AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { 
                rewardedAd = ad 
            }
            override fun onAdFailedToLoad(e: LoadAdError) { 
                rewardedAd = null
                // LOGGING THE ERROR: This will tell you in Logcat WHY it failed
                android.util.Log.e("ADMOB_ERROR", e.message)
            }
        })
    }

    private fun loadNativeAd(root: View) {
        val adLoader = AdLoader.Builder(requireContext(), NATIVE_ID)
            .forNativeAd { nativeAd ->
                val adView = root.findViewById<NativeAdView>(R.id.native_ad_view)
                if (adView != null) {
                    adView.visibility = View.VISIBLE
                    adView.headlineView = adView.findViewById(R.id.ad_headline)
                    adView.bodyView = adView.findViewById(R.id.ad_body)
                    adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)

                    (adView.headlineView as? TextView)?.text = nativeAd.headline
                    (adView.bodyView as? TextView)?.text = nativeAd.body
                    (adView.callToActionView as? Button)?.text = nativeAd.callToAction
                    adView.setNativeAd(nativeAd)
                }
            }.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun updateReward(uid: String) {
        val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
        val rewardAmount = if (nextCycle <= 15) 0.067 else 0.05
        
        val updates = HashMap<String, Any>()
        updates["balance"] = (balance + rewardAmount)
        updates["adCycle"] = nextCycle
        
        db.child("users").child(uid).updateChildren(updates).addOnSuccessListener {
            Toast.makeText(requireContext(), "Shells Collected!", Toast.LENGTH_SHORT).show()
            rewardedAd = null // Reset so we load a fresh one
            loadAd() 
        }
    }
}
