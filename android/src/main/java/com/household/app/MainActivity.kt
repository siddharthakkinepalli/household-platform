package com.household.app

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_UI = "ui_prefs"
        private const val KEY_SIDE_NAV_EXPANDED = "side_nav_expanded"
    }

    private lateinit var navController: NavController
    private lateinit var sideNav: NavigationRailView
    private lateinit var toggleButton: AppCompatImageButton
    private var isSideNavExpanded: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Setup left-side navigation rail
        sideNav = findViewById(R.id.side_navigation)
        sideNav.setupWithNavController(navController)

        toggleButton = findViewById(R.id.button_toggle_side_nav)
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        val storedExpanded = prefs.getBoolean(KEY_SIDE_NAV_EXPANDED, true)
        isSideNavExpanded = if (shouldAutoCollapseInLandscape()) false else storedExpanded

        applySideNavState(toggleButton, animate = false)

        toggleButton.setOnClickListener {
            isSideNavExpanded = !isSideNavExpanded
            prefs.edit().putBoolean(KEY_SIDE_NAV_EXPANDED, isSideNavExpanded).apply()
            applySideNavState(toggleButton, animate = true)
        }
    }

    private fun applySideNavState(toggleButton: AppCompatImageButton, animate: Boolean) {
        val expandedWidth = resources.getDimensionPixelSize(R.dimen.nav_rail_width)
        val collapsedWidth = resources.getDimensionPixelSize(R.dimen.nav_rail_width_collapsed)
        val targetWidth = if (isSideNavExpanded) expandedWidth else collapsedWidth

        sideNav.labelVisibilityMode = if (isSideNavExpanded) {
            NavigationBarView.LABEL_VISIBILITY_LABELED
        } else {
            NavigationBarView.LABEL_VISIBILITY_UNLABELED
        }

        toggleButton.setImageResource(
            if (isSideNavExpanded) R.drawable.ic_nav_chevron_left else R.drawable.ic_nav_chevron_right
        )
        toggleButton.contentDescription = if (isSideNavExpanded) {
            "Collapse navigation panel"
        } else {
            "Expand navigation panel"
        }

        val layoutParams = sideNav.layoutParams
        val currentWidth = layoutParams.width.takeIf { it > 0 } ?: expandedWidth
        if (!animate || currentWidth == targetWidth) {
            layoutParams.width = targetWidth
            sideNav.layoutParams = layoutParams
            return
        }

        ValueAnimator.ofInt(currentWidth, targetWidth).apply {
            duration = 180L
            addUpdateListener { animator ->
                layoutParams.width = animator.animatedValue as Int
                sideNav.layoutParams = layoutParams
            }
            start()
        }
    }

    private fun shouldAutoCollapseInLandscape(): Boolean {
        val config = resources.configuration
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp < 540
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
