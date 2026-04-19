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
    
    // Using your Ad Unit ID
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        // Connect to your XML IDs
        val tvBalance = root.findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = root.findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = root.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = root.findViewById<Button>(R.id.btnWatchAd)
        val cardOfferWalls = root.findViewById<View>(R.id.cardOfferWalls)

        val uid = auth.currentUser?.uid ?: return root

        // Firebase: Listen for Balance & Progress
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                val adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                
                tvBalance?.text = String.format("%.2f", balance)
                tvAdProgress?.text = "Progress: $adCycle/35"
                adProgressBar?.progress = adCycle
            }
            override fun onCancelled(p0: DatabaseError) {}
        })

        // Pre-load the first ad
        loadAd()

        // Button: Watch Ad
        btnWatchAd?.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(requireActivity()) { rewardItem ->
                    updateRewardInFirebase(uid)
                    Toast.makeText(context, "Magic Reward Collected!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Magic is fetching an ad... please wait.", Toast.LENGTH_SHORT).show()
                if (!isAdLoading) loadAd()
            }
        }

        // Card: Offer Walls
        cardOfferWalls?.setOnClickListener {
            Toast.makeText(context, "Premium Offer Walls Loading...", Toast.LENGTH_SHORT).show()
            // Add your Offerwall link logic here when ready
        }

        return root
    }

    private fun loadAd() {
        if (isAdLoading || context == null) return
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(requireContext(), AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
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
                var balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                var adCycle = mutableData.child("adCycle").getValue(Int::class.java) ?: 0

                val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
                val rewardAmount = if (nextCycle <= 15) 0.067 else 0.05

                mutableData.child("balance").value = balance + rewardAmount
                mutableData.child("adCycle").value = nextCycle
                
                return Transaction.success(mutableData)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {
                rewardedAd = null
                loadAd() // Load next ad immediately
            }
        })
    }
}
