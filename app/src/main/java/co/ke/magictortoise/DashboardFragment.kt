package co.ke.magictortoise.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import co.ke.magictortoise.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase

class DashboardFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var tvBalance: TextView
    private lateinit var tvAdRewardText: TextView
    private var currentBalance: Double = 0.0
    private var mRewardedAd: RewardedAd? = null
    
    // Dynamic Prices
    private var priceDaily: Double = 20.0
    private var priceHourly: Double = 15.0
    private var priceHeavy: Double = 99.0
    private var adRewardAmount: Double = 0.02 // Protected profit rate

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        database = FirebaseDatabase.getInstance().reference
        remoteConfig = Firebase.remoteConfig
        
        tvBalance = view.findViewById(R.id.tv_dashboard_balance)
        tvAdRewardText = view.findViewById(R.id.tv_ad_reward_text)

        setupRemoteConfig()
        listenForBalance()
        loadNativeAd(view)
        loadRewardedAd()

        // Setup Buttons
        view.findViewById<MaterialCardView>(R.id.btn_buy_daily)?.setOnClickListener {
            handlePurchase(300, priceDaily, "Daily")
        }
        view.findViewById<MaterialCardView>(R.id.btn_buy_hourly)?.setOnClickListener {
            handlePurchase(1000, priceHourly, "1-Hour")
        }
        view.findViewById<MaterialCardView>(R.id.btn_buy_heavy)?.setOnClickListener {
            handlePurchase(4000, priceHeavy, "Daily Heavy")
        }

        // Ad Button Logic
        view.findViewById<MaterialCardView>(R.id.btn_watch_ad)?.setOnClickListener {
            showAdAndEarn()
        }

        return view
    }

    private fun setupRemoteConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                priceDaily = remoteConfig.getDouble("price_daily_300mb")
                priceHourly = remoteConfig.getDouble("price_hourly_1gb")
                priceHeavy = remoteConfig.getDouble("price_daily_4gb")
                adRewardAmount = remoteConfig.getDouble("ad_reward_value")
                
                // Update UI text for ads dynamically
                tvAdRewardText.text = String.format("WATCH AD & EARN KES %.2f", adRewardAmount)
            }
        }
    }

    private fun listenForBalance() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database.child("users").child(userId).child("balance")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentBalance = snapshot.getValue(Double::class.java) ?: 0.0
                    tvBalance.text = String.format("KES %.2f", currentBalance)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadNativeAd(view: View) {
        // Test ID for now to ensure it loads; replace with your Native ID later
        val adLoader = AdLoader.Builder(requireContext(), "ca-app-pub-3940256099942544/2247696110") 
            .forNativeAd { nativeAd ->
                val adView = layoutInflater.inflate(R.layout.layout_native_ad, null) as NativeAdView
                // Map components like Headline/Body/Ad Attribution here
                adView.setNativeAd(nativeAd)
                val container = view.findViewById<FrameLayout>(R.id.native_ad_container)
                container?.removeAllViews()
                container?.addView(adView)
            }.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        // Using your specific Ad ID
        RewardedAd.load(requireContext(), "ca-app-pub-2344867686796379/1476405830", adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mRewardedAd = null
            }
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                mRewardedAd = rewardedAd
            }
        })
    }

    private fun showAdAndEarn() {
        if (mRewardedAd != null) {
            mRewardedAd?.show(requireActivity()) { _ ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@show
                // Update balance with the 0.02 KES reward
                database.child("users").child(userId).child("balance").setValue(currentBalance + adRewardAmount)
                Toast.makeText(context, "Earned KES $adRewardAmount", Toast.LENGTH_SHORT).show()
                loadRewardedAd() // Prepare the next ad
            }
        } else {
            Toast.makeText(context, "Ad loading... Please wait.", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    private fun handlePurchase(mb: Int, price: Double, type: String) {
        // Confirmation Dialog to prevent accidental clicks
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Confirm Bundle Purchase")
        builder.setMessage("Purchase $mb MB ($type) for KES $price?")
        builder.setPositiveButton("Buy") { _, _ ->
            processTransaction(mb, price, type)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun processTransaction(mb: Int, price: Double, type: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (currentBalance >= price) {
            val newBalance = currentBalance - price
            // Deduct the money from the user's account
            database.child("users").child(userId).child("balance").setValue(newBalance)
                .addOnSuccessListener {
                    // Log the request for fulfillment
                    val request = mapOf(
                        "mb" to mb, 
                        "type" to type, 
                        "price" to price, 
                        "status" to "pending",
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                    database.child("data_requests").child(userId).push().setValue(request)
                    Toast.makeText(context, "Purchase successful!", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(context, "Insufficient Balance", Toast.LENGTH_SHORT).show()
        }
    }
}
