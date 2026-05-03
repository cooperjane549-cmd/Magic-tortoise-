package co.ke.magictortoise.fragments

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
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

class DashboardFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var tvBalance: TextView
    private lateinit var btnWatchAd: MaterialCardView
    private lateinit var tvAdReward: TextView
    
    // Dynamic Prices
    private var priceDaily: Double = 20.0
    private var priceHourly: Double = 15.0
    private var priceLarge: Double = 99.0
    private var adRewardAmount: Double = 0.02 // Default safe value
    private var currentBalance: Double = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        database = FirebaseDatabase.getInstance().reference
        remoteConfig = FirebaseRemoteConfig.getInstance()
        
        tvBalance = view.findViewById(R.id.tv_dashboard_balance)
        btnWatchAd = view.findViewById(R.id.btn_watch_ad)
        tvAdReward = view.findViewById(R.id.tv_ad_reward_text) // Create this in XML inside the button

        setupRemoteConfig()
        listenForBalance()
        loadNativeAd(view)

        // Setup Buttons with dynamic values
        view.findViewById<MaterialCardView>(R.id.btn_buy_daily).setOnClickListener {
            handlePurchase(300, priceDaily, "Daily")
        }
        view.findViewById<MaterialCardView>(R.id.btn_buy_hourly).setOnClickListener {
            handlePurchase(1000, priceHourly, "1-Hour")
        }
        view.findViewById<MaterialCardView>(R.id.btn_buy_heavy).setOnClickListener {
            handlePurchase(4000, priceLarge, "Daily Heavy")
        }

        btnWatchAd.setOnClickListener {
            // Trigger your Interstitial or Rewarded Ad here
            // On complete: database.child("users").child(uid).child("balance").setValue(currentBalance + adRewardAmount)
            Toast.makeText(context, "Showing Ad for KES $adRewardAmount", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun setupRemoteConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // Check for price changes every hour
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                priceDaily = remoteConfig.getDouble("price_daily_300mb")
                priceHourly = remoteConfig.getDouble("price_hourly_1gb")
                priceLarge = remoteConfig.getDouble("price_daily_4gb")
                adRewardAmount = remoteConfig.getDouble("ad_reward_value")
                
                tvAdReward.text = "WATCH AD & EARN KES $adRewardAmount"
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
        val adLoader = AdLoader.Builder(requireContext(), "ca-app-pub-3940256099942544/2247696110") // Test ID
            .forNativeAd { nativeAd ->
                val adView = layoutInflater.inflate(R.layout.layout_native_ad, null) as NativeAdView
                adView.findViewById<TextView>(R.id.ad_headline).text = nativeAd.headline
                adView.findViewById<TextView>(R.id.ad_body).text = nativeAd.body
                adView.findViewById<Button>(R.id.ad_call_to_action).text = nativeAd.callToAction
                adView.setNativeAd(nativeAd)
                
                val container = view.findViewById<FrameLayout>(R.id.native_ad_container)
                container.removeAllViews()
                container.addView(adView)
            }.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun handlePurchase(mb: Int, price: Double, type: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (currentBalance >= price) {
            val newBalance = currentBalance - price
            database.child("users").child(userId).child("balance").setValue(newBalance)
            
            // Log the request for your manual/API injection
            val request = mapOf("mb" to mb, "type" to type, "price" to price, "status" to "pending")
            database.child("data_requests").child(userId).push().setValue(request)
            
            Toast.makeText(context, "Success! $mb MB ($type) requested.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Low Balance! Watch ads or convert airtime.", Toast.LENGTH_SHORT).show()
        }
    }
}
