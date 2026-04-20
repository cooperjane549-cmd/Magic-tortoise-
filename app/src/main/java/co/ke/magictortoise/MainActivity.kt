package co.ke.magictortoise

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds

class MainActivity : AppCompatActivity() {

    // Unity Game ID
    private val unityGameID = "6094869"
    private val testMode = false // Set to true if you are still testing

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MagicTortoise) 
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Google Ads
        MobileAds.initialize(this) {}

        // Initialize Unity Ads
        UnityAds.initialize(applicationContext, unityGameID, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                Log.d("UNITY_ADS", "Unity Initialization Complete")
            }
            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                Log.e("UNITY_ADS", "Unity Initialization Failed: $message")
            }
        })

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, DashboardFragment())
                .commit()
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
