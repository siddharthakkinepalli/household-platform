package com.household.app.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager

/**
 * LegacyFragmentHost — embeds an existing Android Fragment inside a Composable.
 *
 * Used by AppNavHost to host all non-Home screens while the Fragment→Compose migration
 * is in progress. Once a Fragment is fully rewritten in Compose, replace its entry here
 * with the Composable directly.
 *
 * Fragment tag is derived from route so each destination gets a stable container ID.
 * The container view ID is stored in rememberSaveable so the SAME id is reused when
 * the composable is recreated (e.g. on back-stack restore). Without this, each recreation
 * generates a new id while the Fragment still holds a reference to the old id — causing
 * "No view found for id" crashes and broken navigation after the first visit.
 */
@Composable
fun LegacyFragmentHost(
    route: String,
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier
) {
    val fragmentClass = routeToFragmentClass(route)
    val tag = "legacy_$route"

    // Stable ID: same value survives recomposition and nav back-stack restore
    val containerId = rememberSaveable { android.view.View.generateViewId() }

    AndroidView(
        modifier = modifier,
        factory  = { context ->
            FragmentContainerView(context).apply {
                id = containerId
            }
        },
        update = { containerView ->
            val existing = fragmentManager.findFragmentByTag(tag)
            if (existing == null && fragmentClass != null) {
                fragmentManager.beginTransaction()
                    .replace(containerView.id, fragmentClass.newInstance(), tag)
                    .commitNow()
            }
        }
    )
}

/**
 * Maps Screen.route values to the existing Fragment classes.
 * Update this map when a Fragment is replaced with a Composable (remove its entry).
 */
private fun routeToFragmentClass(route: String): Class<out androidx.fragment.app.Fragment>? {
    return when (route) {
        "wallet" -> com.household.app.ui.fragments.WalletFragment::class.java
        "meals"  -> com.household.app.ui.fragments.MealsFragment::class.java
        "docs"   -> com.household.app.ui.fragments.DocumentsFragment::class.java
        "family" -> com.household.app.ui.fragments.FamilyFragment::class.java
        else     -> null
    }
}
