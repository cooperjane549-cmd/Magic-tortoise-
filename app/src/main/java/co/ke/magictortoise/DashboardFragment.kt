package co.ke.magictortoise

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.random.Random

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
        val cardSpin = view.findViewById<CardView>(R.id.card_daily_spin)
        val cardScratch = view.findViewById<CardView>(R.id.card_scratch)

        tvLiveTicker?.isSelected = true 

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            
            // Sync Balance and Ad Cycle
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

            startTournamentCountdown(tvCountdown)
        }

        loadAd()

        btnWatchAd?.setOnClickListener { handleAdsWaterfall() }

        btnJoinTournament?.setOnClickListener {
            val bottomNav = findBottomNav(activity?.window?.decorView)
            bottomNav?.selectedItemId = R.id.nav_market
        }

        cardSpin?.setOnClickListener { showSpinDialog() }
        
        // Tightened: Scratch logic checks time first
        cardScratch?.setOnClickListener { checkScratchEligibility() }
    }

    private fun checkScratchEligibility() {
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid).child("lastScratch").get().addOnSuccessListener { snapshot ->
            val lastTime = snapshot.getValue(Long::class.java) ?: 0L
            val currentTime = System.currentTimeMillis()
            
            // 86,400,000 ms = 24 Hours
            if (currentTime >= (lastTime + 86400000L)) {
                showScratchDialog(uid)
            } else {
                Toast.makeText(context, "Tortoise says: Wait 24 hours to scratch again!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showScratchDialog(uid: String) {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val dialogView = layoutInflater.inflate(R.layout.dialog_scratch_card, null)
        builder.setView(dialogView)
        val dialog = builder.create()

        val btnClaim = dialogView.findViewById<Button>(R.id.btnClaimScratch)
        val tvResult = dialogView.findViewById<TextView>(R.id.tvScratchResult)
        val scratchOverlay = dialogView.findViewById<View>(R.id.scratchOverlay)

        val prize = 0.05
        tvResult?.text = "KES $prize"

        scratchOverlay?.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_MOVE) {
                v.alpha = v.alpha - 0.05f
                if (v.alpha <= 0.1f) {
                    v.visibility = View.GONE
                    btnClaim?.visibility = View.VISIBLE
                }
            }
            true
        }

        btnClaim?.setOnClickListener {
            updateBalanceAndTimestamp(uid, prize)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateBalanceAndTimestamp(uid: String, prize: Double) {
        val updates = hashMapOf<String, Any>(
            "balance" to ServerValue.increment(prize),
            "lastScratch" to ServerValue.TIMESTAMP
        )
        db.child("users").child(uid).updateChildren(updates)
    }

    private fun handleAdsWaterfall() {
        val uid = auth.currentUser?.uid ?: return
        if (rewardedAd != null) {
            rewardedAd?.show(requireActivity()) { updateAdReward(uid) }
        } else {
            showUnityAd(uid)
        }
    }

    private fun updateAdReward(uid: String) {
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                val adCycle = mutableData.child("adCycle").getValue(Int::class.java) ?: 0
                
                // Tightened: Fixed 0.02 Reward
                mutableData.child("balance").value = balance + 0.02
                mutableData.child("adCycle").value = if (adCycle >= 35) 1 else adCycle + 1
                return Transaction.success(mutableData)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) { if (isAdded) loadAd() }
        })
    }

    private fun showSpinDialog() {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val dialogView = layoutInflater.inflate(R.layout.dialog_spin_wheel, null)
        builder.setView(dialogView)
        val dialog = builder.create()

        val wheelImage = dialogView.findViewById<ImageView>(R.id.ivWheel)
        val btnSpinAction = dialogView.findViewById<Button>(R.id.btnSpinAction)

        btnSpinAction?.setOnClickListener {
            btnSpinAction.isEnabled = false
            val sectorIndex = Random.nextInt(16) 
            val targetRotation = (360f * 10) + (360f - (sectorIndex * 22.5f))

            wheelImage?.animate()
                ?.rotationBy(targetRotation)
                ?.setDuration(5000)
                ?.withEndAction {
                    val prize = when(sectorIndex) {
                        2, 5 -> 0.50
                        3, 7, 13 -> 0.10
                        4, 6 -> 0.20
                        8, 11, 12 -> 0.05
                        9, 10, 15 -> 0.01
                        else -> 0.0 
                    }
                    if (prize > 0) updateBalanceOnly(auth.currentUser?.uid ?: "", prize)
                    dialog.dismiss()
                }?.start()
        }
        dialog.show()
    }

    private fun updateBalanceOnly(uid: String, amount: Double) {
        db.child("users").child(uid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val current = data.getValue(Double::class.java) ?: 0.0
                data.value = current + amount
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })
    }

    private fun findBottomNav(view: View?): BottomNavigationView? {
        if (view is BottomNavigationView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findBottomNav(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun loadAd() {
        if (isAdLoading || context == null) return
        isAdLoading = true
        RewardedAd.load(requireContext(), AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad; isAdLoading = false }
            override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null; isAdLoading = false }
        })
    }

    private fun showUnityAd(uid: String) {
        UnityAds.show(requireActivity(), UNITY_REWARDED_ID, object : IUnityAdsShowListener {
            override fun onUnityAdsShowComplete(p0: String, state: UnityAds.UnityAdsShowCompletionState) {
                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) updateAdReward(uid)
            }
            override fun onUnityAdsShowFailure(p0: String, p1: UnityAds.UnityAdsShowError, p2: String) { loadAd() }
            override fun onUnityAdsShowStart(p0: String) {}
            override fun onUnityAdsShowClick(p0: String) {}
        })
    }

    private fun startTournamentCountdown(tvTimer: TextView?) {
        countdownTimer = object : CountDownTimer(14400000, 1000) {
            override fun onTick(ms: Long) {
                val h = (ms / 3600000) % 24
                val m = (ms / 60000) % 60
                val s = (ms / 1000) % 60
                tvTimer?.text = String.format("%02d:%02d:%02d", h, m, s)
            }
            override fun onFinish() { tvTimer?.text = "LIVE!" }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownTimer?.cancel()
    }
}
