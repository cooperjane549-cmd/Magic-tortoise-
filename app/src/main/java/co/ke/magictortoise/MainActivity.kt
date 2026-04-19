package co.ke.magictortoise

import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Force the Material Theme before anything else loads
        setTheme(R.style.Theme_MagicTortoise) 
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 2. Initialize Ads engine
        try {
            MobileAds.initialize(this) { }
        } catch (e: Exception) {
            // Prevent crash if AdMob is acting up
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // 3. Set default screen
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
        // We use a safe check to ensure the ID "nav_host_fragment" exists
        val containerId = R.id.nav_host_fragment
        
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(containerId, fragment)
            .commitAllowingStateLoss() // Safer for low-RAM devices
    }
}
