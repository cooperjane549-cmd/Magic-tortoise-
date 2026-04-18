package co.ke.magictortoise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Links to your ConstraintLayout XML
        setContentView(R.layout.activity_main)

        // 1. Initialize Ads Engine immediately
        MobileAds.initialize(this) { initializationStatus ->
            // Optional: You can log here to see if ads are ready
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // 2. Set the default screen (Dashboard) only if it's the first launch
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, DashboardFragment())
                .commit()
        }

        // 3. Setup Navigation
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> DashboardFragment()
                R.id.nav_market -> MarketFragment()
                R.id.nav_offers -> OffersFragment()
                R.id.nav_support -> SupportFragment()
                else -> DashboardFragment()
            }
            
            // Call our safe replacement function
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        // Using the ID "nav_host_fragment" from your activity_main.xml
        // Adding 'addToBackStack(null)' can help prevent crashes during quick taps
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
