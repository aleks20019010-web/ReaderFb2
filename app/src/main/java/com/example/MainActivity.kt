package com.example

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.ui.LibraryFragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Setup Navigation logic
        navView.setNavigationItemSelectedListener { menuItem ->
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
            openLibraryFragment(lastFilter)
        }
    }

    private fun openLibraryFragment(filter: String) {
        val fragment = LibraryFragment.newInstance(filter)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }
}
