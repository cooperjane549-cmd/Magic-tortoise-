package co.ke.magictortoise

import android.os.Bundle
import android.util.Log
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
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds

class DashboardFragment : Fragment() {

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"
    private val UNITY_REWARDED_ID = "Rewarded_Android" // Ensure this matches your Unity Dashboard

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvBalance = view.findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = view.findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = view.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = view.findViewById<Button>(R.id.btnWatchAd)
        val cardOfferWalls = view.findViewById<View>(R.id.cardOfferWalls)
        val ivLocalBanner = view.findViewById<ImageView>(R.id.ivLocalBanner)
        val cardLocalAd = view.findViewById<View>(R.id.cardLocalAd)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                    val adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                    
                    tvBalance?.text = String.format("%.2f", balance)
                    tvAdProgress?.text = "Progress: $adCycle/35"
                    adProgressBar?.progress = adCycle
                }
                override fun onCancelled(p0: DatabaseError) {}
            })
            loadLocalClientBanner(ivLocalBanner, cardLocalAd)
        }

        loadAd() // Pre-load AdMob

        btnWatchAd?.setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- WATERFALL LOGIC ---
            if (rewardedAd != null) {
                // Try AdMob First
                rewardedAd?.show(requireActivity()) { 
                    updateRewardInFirebase(uid) 
                }
            } else {
                // If AdMob fails, try Unity Second
                showUnityAd(uid)
            }
        }
    }

    private fun showUnityAd(uid: String) {
        UnityAds.show(requireActivity(), UNITY_REWARDED_ID, object : IUnityAdsShowListener {
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                    updateRewardInFirebase(uid)
                }
            }
            override fun onUnityAdsShowFailure(p0: String, p1: UnityAds.UnityAdsShowError, p2: String) {
                Toast.makeText(context, "No ads available, try in 5 seconds", Toast.LENGTH_SHORT).show()
                loadAd() // Try reloading AdMob
            }
            override fun onUnityAdsShowStart(p0: String) {}
            override fun onUnityAdsShowClick(p0: String) {}
        })
    }

    private fun loadAd() {
        val currentContext = context ?: return
        if (isAdLoading) return
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(currentContext, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                isAdLoading = false
            }
            override fun onAdFailedToLoad(e: LoadAdError) {
                rewardedAd = null
                isAdLoading = false
            }
        })
    }

    private fun updateRewardInFirebase(uid: String) {
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                val adCycle = mutableData.child("adCycle").getValue(Int::class.java) ?: 0

                val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
                val rewardAmount = if (nextCycle <= 15) 0.067 else 0.05

                mutableData.child("balance").value = balance + rewardAmount
                mutableData.child("adCycle").value = nextCycle
                
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed && isAdded) {
                    Toast.makeText(context, "Reward Added!", Toast.LENGTH_SHORT).show()
                    rewardedAd = null
                    loadAd() // Pre-load next AdMob ad
                }
            }
        })
    }

    private fun loadLocalClientBanner(imageView: ImageView?, card: View?) {
        db.child("settings").child("localBannerUrl").get().addOnSuccessListener { snapshot ->
            val url = snapshot.getValue(String::class.java)
            if (url != null && isAdded) { /* Future Glide logic */ }
        }
    }
}
