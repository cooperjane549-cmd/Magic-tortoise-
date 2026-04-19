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
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    // This is your Ad Unit ID (The one with the /)
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        val tvBalance = root.findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = root.findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = root.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = root.findViewById<Button>(R.id.btnWatchAd)
        val cardOfferWalls = root.findViewById<View>(R.id.cardOfferWalls)

        val uid = auth.currentUser?.uid ?: return root

        // Firebase Sync: Updates your shells and progress bar automatically
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return // Safety check
                val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                val adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                
                tvBalance?.text = String.format("%.2f", balance)
                tvAdProgress?.text = "Progress: $adCycle/35"
                adProgressBar?.progress = adCycle
            }
            override fun onCancelled(p0: DatabaseError) {}
        })

        // Start loading the ad as soon as the screen opens
        loadAd()

        // THE WATCH AD BUTTON
        btnWatchAd?.setOnClickListener {
            if (rewardedAd != null) {
                // We use activity? to ensure the "Mansion" (MainActivity) is hosting the ad
                activity?.let { myActivity ->
                    rewardedAd?.show(myActivity) { rewardItem ->
                        updateRewardInFirebase(uid)
                    }
                }
            } else {
                // If it's glittery but nothing happens, this Toast will now tell you why
                Toast.makeText(context, "Magic is still fetching an ad... please try again in 5 seconds.", Toast.LENGTH_SHORT).show()
                if (!isAdLoading) loadAd()
            }
        }

        cardOfferWalls?.setOnClickListener {
            Toast.makeText(context, "Premium Offer Walls Loading...", Toast.LENGTH_SHORT).show()
        }

        return root
    }

    private fun loadAd() {
        // Safe check: Don't load if already loading or if screen is closed
        val currentContext = context ?: return
        if (isAdLoading) return
        
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(currentContext, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                isAdLoading = false
                // Success Notification
                activity?.runOnUiThread {
                    Toast.makeText(context, "Ad Ready to Play!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAdFailedToLoad(e: LoadAdError) {
                rewardedAd = null
                isAdLoading = false
                // DIAGNOSTIC: This tells you the REAL reason for the "glittering"
                activity?.runOnUiThread {
                    android.util.Log.e("ADS_ERROR", e.message)
                }
            }
        })
    }

    private fun updateRewardInFirebase(uid: String) {
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                val adCycle = mutableData.child("adCycle").getValue(Int::class.java) ?: 0

                // Logic: 0.067 for first 15 ads, 0.05 after that
                val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
                val rewardAmount = if (nextCycle <= 15) 0.067 else 0.05

                mutableData.child("balance").value = balance + rewardAmount
                mutableData.child("adCycle").value = nextCycle
                
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    Toast.makeText(context, "Magic Shells Collected!", Toast.LENGTH_SHORT).show()
                    rewardedAd = null
                    loadAd() // Prepare the next ad
                }
            }
        })
    }
}
