package co.ke.magictortoise

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import co.ke.magictortoise.fragments.DashboardFragment
import co.ke.magictortoise.fragments.MarketFragment
import co.ke.magictortoise.fragments.OffersFragment
// Add this specific import because SupportFragment is in the root folder
import co.ke.magictortoise.SupportFragment 

import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds

class MainActivity : AppCompatActivity() {

    private val unityGameID = "6094869"
    private val testMode = false 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Ad Systems
        MobileAds.initialize(this) {}
        UnityAds.initialize(applicationContext, unityGameID, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() { 
                Log.d("UNITY_ADS", "Unity Initialization Complete") 
            }
            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                Log.e("UNITY_ADS", "Unity Initialization Failed: $message")
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
                // Now we link the SupportFragment here
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
