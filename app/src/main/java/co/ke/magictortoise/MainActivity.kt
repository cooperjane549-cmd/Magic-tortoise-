package co.ke.magictortoise

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds

// EXPLICIT IMPORTS - If these red-line, your files are in the wrong folder
import co.ke.magictortoise.DashboardFragment
import co.ke.magictortoise.MarketFragment
import co.ke.magictortoise.OffersFragment
import co.ke.magictortoise.SupportFragment

class MainActivity : AppCompatActivity() {

    private val unityGameID = "6094869"
    private val testMode = false 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Ads
        MobileAds.initialize(this) {}
        UnityAds.initialize(applicationContext, unityGameID, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() { Log.d("UNITY_ADS", "Unity Complete") }
            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                Log.e("UNITY_ADS", "Unity Failed: $message")
            }
        })

        // 2. Navigation
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
}
