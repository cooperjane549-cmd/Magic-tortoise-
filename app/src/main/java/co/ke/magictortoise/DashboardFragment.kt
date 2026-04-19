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

class DashboardFragment : Fragment() {

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    // STAGE 1: Just inflate the layout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    // STAGE 2: Wire the logic (Everything goes here now)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvBalance = view.findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = view.findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = view.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = view.findViewById<Button>(R.id.btnWatchAd)
        val cardOfferWalls = view.findViewById<View>(R.id.cardOfferWalls)

        val uid = auth.currentUser?.uid ?: return

        // DIAGNOSTIC TEST: If you don't see this, the Fragment isn't loading!
        Toast.makeText(context, "Tortoise Logic Online", Toast.LENGTH_SHORT).show()

        // Firebase Sync: Always listening for Shell updates
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                val adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                
                tvBalance?.text = String.format("%.2f", balance)
                tvAdProgress?.text = "Progress: $adCycle/35"
                adProgressBar?.progress = adCycle
            }
            override fun onCancelled(p0: DatabaseError) {
                Log.e("FIREBASE_ERROR", p0.message)
            }
        })

        // Pre-load the ad
        loadAd()

        // Button: Watch Ad
        btnWatchAd?.setOnClickListener {
            if (rewardedAd != null) {
                activity?.let { myActivity ->
                    rewardedAd?.show(myActivity) { rewardItem ->
                        updateRewardInFirebase(uid)
                    }
                }
            } else {
                Toast.makeText(context, "Magic is still fetching... please wait.", Toast.LENGTH_SHORT).show()
                if (!isAdLoading) loadAd()
            }
        }

        // Card: Offer Walls
        cardOfferWalls?.setOnClickListener {
            Toast.makeText(context, "Offer Walls coming soon!", Toast.LENGTH_SHORT).show()
        }
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
                Log.d("ADS", "Ad loaded successfully")
            }
            override fun onAdFailedToLoad(e: LoadAdError) {
                rewardedAd = null
                isAdLoading = false
                Log.e("ADS", "Ad failed to load: ${e.message}")
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
                if (committed) {
                    rewardedAd = null
                    loadAd()
                }
            }
        })
    }
}
