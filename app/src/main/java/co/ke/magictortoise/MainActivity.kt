package co.ke.magictortoise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This links to the XML you just shared
        setContentView(R.layout.activity_main)

        // 1. IMPORTANT: Initialize Ads so "Watch Ads" doesn't fail silently
        MobileAds.initialize(this) { status -> }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // 2. Set the default screen (Dashboard)
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
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
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        // We use the ID "nav_host_fragment" from your ConstraintLayout XML
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
