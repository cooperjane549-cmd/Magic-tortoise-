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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardFragment : Fragment() {

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    
    // Using your Ad IDs
    private val AD_UNIT_ID = "ca-app-pub-2344867686796379/1476405830"

    private var balance = 0.0
    private var adCycle = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        // Match IDs exactly to your XML
        val tvBalance = root.findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = root.findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = root.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = root.findViewById<Button>(R.id.btnWatchAd)
        val cardOfferWalls = root.findViewById<View>(R.id.cardOfferWalls)

        val uid = auth.currentUser?.uid ?: return root

        // Firebase Sync
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                
                // Update your UI
                tvBalance.text = String.format("%.2f", balance)
                tvAdProgress.text = "Progress: $adCycle/35"
                adProgressBar.progress = adCycle
            }
            override fun onCancelled(p0: DatabaseError) {}
        })

        loadAd()

        // WATCH AD BUTTON
        btnWatchAd.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(requireActivity()) { 
                    updateReward(uid) 
                    Toast.makeText(requireContext(), "Reward added!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Fetching magic ad... please try again in 5s", Toast.LENGTH_SHORT).show()
                loadAd()
            }
        }

        // OFFER WALL BUTTON
        cardOfferWalls.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Premium Offers...", Toast.LENGTH_SHORT).show()
            val url = "https://your-offerwall-link.com/?uid=$uid"
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(requireContext(), Uri.parse(url))
        }

        return root
    }

    private fun loadAd() {
        if (isAdLoading) return
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

    private fun updateReward(uid: String) {
        val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
        val rewardAmount = if (nextCycle <= 15) 0.067 else 0.05
        
        val updates = HashMap<String, Any>()
        updates["balance"] = balance + rewardAmount
        updates["adCycle"] = nextCycle
        
        db.child("users").child(uid).updateChildren(updates).addOnSuccessListener {
            rewardedAd = null
            loadAd() 
        }
    }
}
