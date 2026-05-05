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
import co.ke.magictortoise.AdminActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase

class DashboardFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var tvBalance: TextView
    private lateinit var tvAdRewardText: TextView
    
    // Dynamic Price Views
    private lateinit var tvPriceDaily: TextView
    private lateinit var tvPriceHourly: TextView
    private lateinit var tvPriceHeavy: TextView

    private var currentBalance: Double = 0.0
    private var mRewardedAd: RewardedAd? = null
    
    // Default prices (Will be overwritten by Firebase)
    private var priceDaily: Double = 20.0
    private var priceHourly: Double = 15.0
    private var priceHeavy: Double = 99.0
    private var adRewardAmount: Double = 0.02

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        database = FirebaseDatabase.getInstance().reference
        
        tvBalance = view.findViewById(R.id.tv_dashboard_balance)
        tvAdRewardText = view.findViewById(R.id.tv_ad_reward_text)
        
        // Find Dynamic Price Labels in your XML
        tvPriceDaily = view.findViewById(R.id.tv_price_daily)
        tvPriceHourly = view.findViewById(R.id.tv_price_hourly)
        tvPriceHeavy = view.findViewById(R.id.tv_price_heavy)

        // SECRET ENTRANCE: Long press balance for Admin
        tvBalance.setOnLongClickListener {
            showAdminPasswordDialog()
            true
        }

        // DEPOSIT FEATURE: Click "Deposit Cash" to show M-Pesa instructions
        view.findViewById<TextView>(R.id.tv_deposit_cash)?.setOnClickListener {
            showDepositDialog()
        }

        listenForDynamicPrices()
        listenForBalance()
        loadNativeAd(view)
        loadRewardedAd()

        // Button Logic with "Insufficient Balance" Protection
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

    // FEATURE: Dynamic Prices from Firebase (No more hardcoding!)
    private fun listenForDynamicPrices() {
        database.child("app_settings").child("prices")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    priceDaily = snapshot.child("daily_300mb").getValue(Double::class.java) ?: 20.0
                    priceHourly = snapshot.child("hourly_1gb").getValue(Double::class.java) ?: 15.0
                    priceHeavy = snapshot.child("heavy_4gb").getValue(Double::class.java) ?: 99.0
                    adRewardAmount = snapshot.child("ad_reward").getValue(Double::class.java) ?: 0.02

                    // Update UI text dynamically
                    tvPriceDaily.text = "KES ${priceDaily.toInt()}"
                    tvPriceHourly.text = "KES ${priceHourly.toInt()}"
                    tvPriceHeavy.text = "KES ${priceHeavy.toInt()}"
                    tvAdRewardText.text = String.format("WATCH AD & EARN KES %.2f", adRewardAmount)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // FEATURE: M-Pesa Deposit System
    private fun showDepositDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Deposit to Wallet")
        builder.setMessage("1. Send money to Till: 3043489\n2. Copy the M-Pesa Code\n3. Paste it below:")

        val input = EditText(requireContext())
        input.hint = "Paste M-Pesa Code here"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        builder.setView(input)

        builder.setPositiveButton("Submit") { _, _ ->
            val mpesaCode = input.text.toString().trim().uppercase()
            if (mpesaCode.length >= 8) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                val depositRequest = mapOf(
                    "userId" to userId,
                    "mpesaCode" to mpesaCode,
                    "status" to "pending",
                    "type" to "Cash Deposit",
                    "timestamp" to ServerValue.TIMESTAMP
                )
                database.child("deposit_requests").push().setValue(depositRequest)
                Toast.makeText(context, "M-Pesa code submitted for verification", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Invalid M-Pesa Code", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun handlePurchase(mb: Int, price: Double, type: String) {
        if (currentBalance < price) {
            // INSUFFICIENT BALANCE PROTECTION
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Insufficient Balance")
            builder.setMessage("You need KES $price to buy this bundle. Your current balance is KES $currentBalance.")
            builder.setPositiveButton("Deposit Now") { _, _ -> showDepositDialog() }
            builder.setNegativeButton("Close", null)
            builder.show()
        } else {
            // CONFIRM PURCHASE
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Confirm Bundle")
            builder.setMessage("Buy $mb MB for KES $price?")
            builder.setPositiveButton("Buy") { _, _ -> processTransaction(mb, price, type) }
            builder.setNegativeButton("Cancel", null)
            builder.show()
        }
    }

    private fun processTransaction(mb: Int, price: Double, type: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val newBalance = currentBalance - price
        
        database.child("users").child(userId).child("balance").setValue(newBalance)
            .addOnSuccessListener {
                val request = mapOf(
                    "userId" to userId,
                    "mb" to mb, 
                    "type" to type, 
                    "price" to price, 
                    "status" to "pending",
                    "nodeSource" to "data_requests",
                    "timestamp" to ServerValue.TIMESTAMP
                )
                database.child("data_requests").push().setValue(request)
                Toast.makeText(context, "Request sent! Processing your data...", Toast.LENGTH_LONG).show()
            }
    }

    // ... (listenForBalance, loadNativeAd, loadRewardedAd, showAdAndEarn, showAdminPasswordDialog remain same)
    
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
            override fun onAdFailedToLoad(adError: LoadAdError) { mRewardedAd = null }
            override fun onAdLoaded(rewardedAd: RewardedAd) { mRewardedAd = rewardedAd }
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

    private fun showAdminPasswordDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Admin Verification")
        val input = EditText(requireContext())
        input.hint = "Enter PIN"
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        builder.setView(input)
        builder.setPositiveButton("Verify") { _, _ ->
            if (input.text.toString() == "0008") { 
                startActivity(Intent(requireContext(), AdminActivity::class.java))
            } else {
                Toast.makeText(context, "Wrong Code!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }
}
