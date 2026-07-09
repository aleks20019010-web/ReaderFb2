package com.nightread.app

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.nightread.app.ui.LibraryFragment
import com.nightread.app.ui.CustomToast
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Handle WindowInsets for Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val fragmentContainer = findViewById<FrameLayout>(R.id.fragment_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Apply top and bottom padding to the main content container
            fragmentContainer.setPadding(0, insets.top, 0, insets.bottom)
            // Apply top padding to the NavigationView
            navView.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }

        // Setup Navigation logic
        navView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nav_sync) {
                openSyncFragment()
            } else if (menuItem.itemId == R.id.nav_settings) {
                // Open SettingsActivity
                val intent = Intent(this, com.nightread.app.ui.SettingsActivity::class.java)
                startActivity(intent)
            } else {
                val filter = when (menuItem.itemId) {
                    R.id.nav_reading -> "reading"
                    R.id.nav_read -> "read"
                    else -> "all"
                }
                
                // Save selected menu in preferences
                getSharedPreferences("nav_prefs", MODE_PRIVATE).edit()
                    .putString("last_selected_filter", filter)
                    .apply()
                    
                openLibraryFragment(filter)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        if (savedInstanceState == null) {
            val lastFilter = getSharedPreferences("nav_prefs", MODE_PRIVATE)
                .getString("last_selected_filter", "all") ?: "all"
                
            val menuId = when (lastFilter) {
                "reading" -> R.id.nav_reading
                "read" -> R.id.nav_read
                else -> R.id.nav_library
            }
            navView.setCheckedItem(menuId)
            
            if (intent.getBooleanExtra("OPEN_SYNC", false)) {
                openSyncFragment()
                navView.setCheckedItem(R.id.nav_sync)
            } else {
                openLibraryFragment(lastFilter)
            }
        }

        // Inform user if previous sync was interrupted
        if (com.nightread.app.data.SyncSettingsManager.wasInterrupted(this)) {
            com.nightread.app.data.SyncSettingsManager.setInterruptedFlag(this, false)
            CustomToast.show(this, "Предыдущая фоновая синхронизация была прервана")
        }
    }

    private fun openLibraryFragment(filter: String) {
        val fragment = LibraryFragment.newInstance(filter)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun openSyncFragment() {
        val fragment = com.nightread.app.ui.YandexSyncFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }
}
