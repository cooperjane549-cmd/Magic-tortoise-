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
    private var balance = 0.0
    private var adCycle = 0

    // YOUR ACTUAL REWARD ID
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        val tvBalance = root.findViewById<TextView>(R.id.tvBalance)
        val tvProgress = root.findViewById<TextView>(R.id.tvAdProgress)
        val progressBar = root.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatch = root.findViewById<Button>(R.id.btnWatchAd)

        val uid = auth.currentUser?.uid ?: return root

        // REAL-TIME FIREBASE LINK
        db.child("users").child(uid).addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                
                tvBalance.text = String.format("%.2f", balance)
                tvProgress.text = "Daily Progress: $adCycle/35"
                progressBar.progress = adCycle
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        loadAd()

        btnWatch.setOnClickListener {
            rewardedAd?.let { ad ->
                ad.show(requireActivity()) { 
                    updateReward(uid) 
                }
            } ?: Toast.makeText(context, "Magic loading...", Toast.LENGTH_SHORT).show()
        }

        return root
    }

    private fun loadAd() {
        RewardedAd.load(requireContext(), AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null }
        })
    }

    private fun updateReward(uid: String) {
        val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
        val rewardAmount = if (nextCycle <= 15) 0.067 else 0.05
        
        val updates = mapOf(
            "balance" to (balance + rewardAmount),
            "adCycle" to nextCycle
        )
        
        db.child("users").child(uid).updateChildren(updates)
        loadAd() // Preload next
    }
}
