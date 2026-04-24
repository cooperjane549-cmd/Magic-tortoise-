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
     * Call this function when a user joins or a tournament starts.
     */
    fun showTournamentOverlay(jackpotAmount: String) {
        if (tournamentOverlay != null) return // Already showing

        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        val inflater = LayoutInflater.from(this)
        tournamentOverlay = inflater.inflate(R.layout.layout_tournament_overlay, rootLayout, false)

        val cardQuiz = tournamentOverlay?.findViewById<CardView>(R.id.cardQuizWindow)
        val ivFloating = tournamentOverlay?.findViewById<ImageView>(R.id.ivFloatingTortoise)
        val btnMinimize = tournamentOverlay?.findViewById<Button>(R.id.btnMinimize)
        val tvQuestion = tournamentOverlay?.findViewById<TextView>(R.id.tvLiveQuestion)

        tvQuestion?.text = "Tournament Active!\nJackpot: KES $jackpotAmount\nWaiting for question..."

        // Add overlay to the screen
        rootLayout.addView(tournamentOverlay)

        // MINIMIZE TO FLOATING TORTOISE
        btnMinimize?.setOnClickListener {
            cardQuiz?.visibility = View.GONE
            tournamentOverlay?.setBackgroundColor(Color.TRANSPARENT)
            ivFloating?.visibility = View.VISIBLE
            Toast.makeText(this, "Tournament minimized. Click tortoise to return!", Toast.LENGTH_SHORT).show()
        }

        // RESTORE FROM FLOATING TORTOISE
        ivFloating?.setOnClickListener {
            ivFloating.visibility = View.GONE
            tournamentOverlay?.setBackgroundColor(Color.parseColor("#CC000000"))
            cardQuiz?.visibility = View.VISIBLE
        }
    }

    /**
     * Function to remove overlay when tournament ends
     */
    fun closeTournamentOverlay() {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        tournamentOverlay?.let {
            rootLayout.removeView(it)
            tournamentOverlay = null
        }
    }
}
