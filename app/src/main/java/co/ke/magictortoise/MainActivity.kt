package co.ke.magictortoise

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds

class MainActivity : AppCompatActivity() {

    // Ads Configuration
    private val unityGameID = "6094869"
    private val testMode = false 

    // Tournament Overlay View References
    private var tournamentOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MagicTortoise) 
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Ad Systems
        MobileAds.initialize(this) {}
        UnityAds.initialize(applicationContext, unityGameID, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() { Log.d("UNITY_ADS", "Unity Complete") }
            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                Log.e("UNITY_ADS", "Unity Failed: $message")
            }
        })

        // 2. Navigation Logic
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> DashboardFragment()
                R.id.nav_market -> MarketFragment()
                R.id.nav_offers -> OffersFragment()
                R.id.nav_support -> SupportFragment()
                else -> DashboardFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    /**
     * TOURNAMENT OVERLAY ENGINE
     * Fixed to prevent crashes when called from fragments.
     */
    fun showTournamentOverlay(jackpotAmount: String) {
        // Ensure we run this on the UI thread to prevent transaction crashes
        runOnUiThread {
            if (tournamentOverlay != null) {
                // If it's already showing, just update the text instead of inflating again
                val tvQuestion = tournamentOverlay?.findViewById<TextView>(R.id.tvLiveQuestion)
                tvQuestion?.text = "Tournament Joined!\nJackpot: KES $jackpotAmount\nWaiting for question..."
                return@runOnUiThread
            }

            val rootLayout = findViewById<ViewGroup>(android.R.id.content)
            val inflater = LayoutInflater.from(this)
            
            try {
                // Inflate the overlay
                tournamentOverlay = inflater.inflate(R.layout.layout_tournament_overlay, rootLayout, false)

                val cardQuiz = tournamentOverlay?.findViewById<CardView>(R.id.cardQuizWindow)
                val ivFloating = tournamentOverlay?.findViewById<ImageView>(R.id.ivFloatingTortoise)
                val btnMinimize = tournamentOverlay?.findViewById<Button>(R.id.btnMinimize)
                val tvQuestion = tournamentOverlay?.findViewById<TextView>(R.id.tvLiveQuestion)
                val btnJoin = tournamentOverlay?.findViewById<Button>(R.id.btnJoinTournamentFinal)
                val btnClose = tournamentOverlay?.findViewById<ImageButton>(R.id.btnCloseTournament)

                // ALIGNMENT: Since they already joined to trigger this, hide the join button
                btnJoin?.visibility = View.GONE
                btnClose?.visibility = View.GONE // Minimize is used instead for the active HUD

                tvQuestion?.text = "Tournament Active!\nJackpot: KES $jackpotAmount\nWaiting for start..."

                rootLayout.addView(tournamentOverlay)

                // MINIMIZE TO FLOATING TORTOISE
                btnMinimize?.setOnClickListener {
                    cardQuiz?.visibility = View.GONE
                    tournamentOverlay?.setBackgroundColor(Color.TRANSPARENT)
                    ivFloating?.visibility = View.VISIBLE
                }

                // RESTORE FROM FLOATING TORTOISE
                ivFloating?.setOnClickListener {
                    ivFloating.visibility = View.GONE
                    tournamentOverlay?.setBackgroundColor(Color.parseColor("#CC000000"))
                    cardQuiz?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("OVERLAY_ERROR", "Failed to show overlay: ${e.message}")
                Toast.makeText(this, "Joined Tournament!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun closeTournamentOverlay() {
        runOnUiThread {
            val rootLayout = findViewById<ViewGroup>(android.R.id.content)
            tournamentOverlay?.let {
                rootLayout.removeView(it)
                tournamentOverlay = null
            }
        }
    }
}
