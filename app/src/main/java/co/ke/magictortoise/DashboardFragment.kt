package co.ke.magictortoise

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
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
    private val UNITY_REWARDED_ID = "Rewarded_Android"
    private var countdownTimer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvBalance = view.findViewById<TextView>(R.id.tvBalance)
        val tvAdProgress = view.findViewById<TextView>(R.id.tvAdProgress)
        val adProgressBar = view.findViewById<ProgressBar>(R.id.adProgressBar)
        val btnWatchAd = view.findViewById<Button>(R.id.btnWatchAd)
        val tvCountdown = view.findViewById<TextView>(R.id.tvCountdown)
        val tvLiveTicker = view.findViewById<TextView>(R.id.tvLiveTicker)
        val btnJoinTournament = view.findViewById<Button>(R.id.btnJoinTournament)

        // Enable Marquee for Live Ticker
        tvLiveTicker.isSelected = true 

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            
            // Sync Balance and Progress
            db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val balance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                    val adCycle = snapshot.child("adCycle").getValue(Int::class.java) ?: 0
                    
                    tvBalance?.text = String.format("%.2f", balance)
                    tvAdProgress?.text = "Target: $adCycle/35"
                    adProgressBar?.progress = adCycle
                }
                override fun onCancelled(p0: DatabaseError) {}
            })

            // Sync Live Ticker from Firebase Settings
            db.child("settings").child("liveTickerText").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ticker = snapshot.getValue(String::class.java) ?: "🔥 Start earning today with Magic Tortoise!"
                    tvLiveTicker.text = ticker
                }
                override fun onCancelled(p0: DatabaseError) {}
            })

            startTournamentCountdown(tvCountdown)
        }

        loadAd()

        btnWatchAd?.setOnClickListener { handleAdsWaterfall() }

        btnJoinTournament?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://your-pesapal-link.com"))
            startActivity(intent)
        }

        view.findViewById<CardView>(R.id.card_daily_spin).setOnClickListener {
            Toast.makeText(context, "Spinning Wheel...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAdsWaterfall() {
        val uid = auth.currentUser?.uid ?: return
        if (rewardedAd != null) {
            rewardedAd?.show(requireActivity()) { updateRewardInFirebase(uid) }
        } else {
            showUnityAd(uid)
        }
    }

    private fun showUnityAd(uid: String) {
        UnityAds.show(requireActivity(), UNITY_REWARDED_ID, object : IUnityAdsShowListener {
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) updateRewardInFirebase(uid)
            }
            override fun onUnityAdsShowFailure(p0: String, p1: UnityAds.UnityAdsShowError, p2: String) {
                loadAd()
            }
            override fun onUnityAdsShowStart(p0: String) {}
            override fun onUnityAdsShowClick(p0: String) {}
        })
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
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                val adCycle = mutableData.child("adCycle").getValue(Int::class.java) ?: 0
                val nextCycle = if (adCycle >= 35) 1 else adCycle + 1
                val rewardAmount = if (nextCycle <= 15) 0.067 else 0.05
                mutableData.child("balance").value = balance + rewardAmount
                mutableData.child("adCycle").value = nextCycle
                return Transaction.success(mutableData)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {
                if (isAdded) loadAd()
            }
        })
    }

    private fun startTournamentCountdown(tvTimer: TextView) {
        val millisInFuture: Long = 14400000 // 4 Hours
        countdownTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = (millisUntilFinished / 3600000) % 24
                val mins = (millisUntilFinished / 60000) % 60
                val secs = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format("%02d:%02d:%02d", hours, mins, secs)
            }
            override fun onFinish() { tvTimer.text = "LIVE!" }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownTimer?.cancel()
    }
}
