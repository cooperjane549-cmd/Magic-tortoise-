package co.ke.magictortoise.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
    
    private var priceDaily: Double = 20.0
    private var priceHourly: Double = 15.0
    private var priceHeavy: Double = 99.0
    private var adRewardAmount: Double = 0.02

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        database = FirebaseDatabase.getInstance().reference
        remoteConfig = Firebase.remoteConfig
        
        tvBalance = view.findViewById(R.id.tv_dashboard_balance)
        tvAdRewardText = view.findViewById(R.id.tv_ad_reward_text)

        // SECRET ENTRANCE: Long press the balance to open Admin
        tvBalance.setOnLongClickListener {
            showAdminPasswordDialog()
            true
        }

        setupRemoteConfig()
        listenForBalance()
        loadNativeAd(view)
        loadRewardedAd()

        view.findViewById<MaterialCardView>(R.id.btn_buy_daily)?.setOnClickListener {
            handlePurchase(300, priceDaily, "Daily")
        }
        
        view.findViewById<MaterialCardView>(R.id.btn_buy_hourly)?.setOnClickListener {
            handlePurchase(1000, priceHourly, "1-Hour")
        }
        
        view.findViewById<MaterialCardView>(R.id.btn_buy_heavy)?.setOnClickListener {
            handlePurchase(4000, priceHeavy, "Daily Heavy")
        }

        view.findViewById<MaterialCardView>(R.id.btn_watch_ad)?.setOnClickListener {
            showAdAndEarn()
        }

        return view
    }

    // NEW FUNCTION: The Secret Password Popup
    private fun showAdminPasswordDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Admin Verification")
        builder.setMessage("Enter the secret code to access the Admin Panel.")

        val input = EditText(requireContext())
        input.hint = "Password"
        // This makes the password appear as dots (••••)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("Verify") { _, _ ->
            val code = input.text.toString()
            if (code == "0008") { // <--- CHANGE THIS to your preferred PIN
                Toast.makeText(context, "Access Granted", Toast.LENGTH_SHORT).show()
                // Logic to open AdminActivity will go here
            } else {
                Toast.makeText(context, "Wrong Code!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun setupRemoteConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0 
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val d = remoteConfig.getDouble("price_daily_300mb")
                if (d > 0) priceDaily = d
                
                val h = remoteConfig.getDouble("price_hourly_1gb")
                if (h > 0) priceHourly = h
                
                val hv = remoteConfig.getDouble("price_daily_4gb")
                if (hv > 0) priceHeavy = hv
                
                val rw = remoteConfig.getDouble("ad_reward_value")
                if (rw > 0) adRewardAmount = rw
                
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
        val adLoader = AdLoader.Builder(requireContext(), "ca-app-pub-2344867686796379/2582924164") 
            .forNativeAd { nativeAd ->
                val adView = layoutInflater.inflate(R.layout.layout_native_ad, null) as NativeAdView
                adView.headlineView = adView.findViewById(R.id.ad_headline)
                (adView.headlineView as TextView).text = nativeAd.headline
                adView.bodyView = adView.findViewById(R.id.ad_body)
                (adView.bodyView as TextView).text = nativeAd.body
                adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
                (adView.callToActionView as Button).text = nativeAd.callToAction
                adView.iconView = adView.findViewById(R.id.ad_app_icon)
                if (nativeAd.icon != null) {
                    adView.iconView?.visibility = View.VISIBLE
                    (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
                } else {
                    adView.iconView?.visibility = View.GONE
                }
                adView.setNativeAd(nativeAd)
                val container = view.findViewById<FrameLayout>(R.id.native_ad_container)
                container?.removeAllViews()
                container?.addView(adView)
                container?.visibility = View.VISIBLE
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    view.findViewById<FrameLayout>(R.id.native_ad_container)?.visibility = View.GONE
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
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
                database.child("users").child(userId).child("balance").setValue(currentBalance + adRewardAmount)
                Toast.makeText(context, "Earned KES $adRewardAmount", Toast.LENGTH_SHORT).show()
                loadRewardedAd() 
            }
        } else {
            Toast.makeText(context, "Ad loading... Please wait.", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    private fun handlePurchase(mb: Int, price: Double, type: String) {
        if (price <= 0.0) {
            Toast.makeText(context, "Syncing prices, try again...", Toast.LENGTH_SHORT).show()
            return
        }
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
            database.child("users").child(userId).child("balance").setValue(newBalance)
                .addOnSuccessListener {
                    val request = mapOf(
                        "mb" to mb, 
                        "type" to type, 
                        "price" to price, 
                        "status" to "pending",
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                    database.child("data_requests").child(userId).push().setValue(request)
                    Toast.makeText(context, "Success! $mb MB requested.", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(context, "Insufficient Balance", Toast.LENGTH_SHORT).show()
        }
    }
}
