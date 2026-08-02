package com.nightread.app.readium

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nightread.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

class ReadiumReaderFragment : Fragment() {

    private var publication: Publication? = null
    private var initialLocator: Locator? = null

    private var navigatorFragment: EpubNavigatorFragment? = null

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator

    var onTapListener: (() -> Unit)? = null
    var onExternalLinkListener: ((AbsoluteUrl) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_readium_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (publication != null && navigatorFragment == null) {
            setupNavigator(savedInstanceState)
        }
    }

    fun initPublication(pub: Publication, initialLoc: Locator? = null) {
        this.publication = pub
        this.initialLocator = initialLoc
        if (isAdded && view != null && navigatorFragment == null) {
            setupNavigator(null)
        }
    }

    private fun setupNavigator(savedInstanceState: Bundle?) {
        val pub = publication ?: return
        if (childFragmentManager.findFragmentByTag("epub_navigator") != null) {
            navigatorFragment = childFragmentManager.findFragmentByTag("epub_navigator") as? EpubNavigatorFragment
            return
        }

        val factory = EpubNavigatorFactory(pub)

        val listener = object : EpubNavigatorFragment.Listener {
            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                onExternalLinkListener?.invoke(url)
            }
            override fun onTap(point: PointF): Boolean {
                onTapListener?.invoke()
                return true
            }
        }

        val fragmentFactory = factory.createFragmentFactory(
            initialLocator = initialLocator,
            listener = listener
        )

        childFragmentManager.fragmentFactory = fragmentFactory

        if (savedInstanceState == null) {
            val navFragment = fragmentFactory.instantiate(
                requireContext().classLoader,
                EpubNavigatorFragment::class.java.name
            ) as EpubNavigatorFragment

            childFragmentManager.beginTransaction()
                .replace(R.id.readiumFragmentContainer, navFragment, "epub_navigator")
                .commitNowAllowingStateLoss()

            navigatorFragment = navFragment

            lifecycleScope.launch {
                navFragment.currentLocator.collect { loc ->
                    _currentLocator.value = loc
                }
            }
        } else {
            navigatorFragment = childFragmentManager.findFragmentByTag("epub_navigator") as? EpubNavigatorFragment
        }
    }

    fun updatePreferences(
        themeMode: String,
        fontSizeMultiplier: Double = 1.0,
        fontFamilyName: String? = null
    ) {
        val nav = navigatorFragment ?: return
        val theme = when (themeMode.lowercase()) {
            "dark" -> Theme.DARK
            "sepia" -> Theme.SEPIA
            else -> Theme.LIGHT
        }

        val prefs = EpubPreferences(
            theme = theme,
            fontSize = fontSizeMultiplier
        )
        nav.submitPreferences(prefs)
    }

    fun go(locator: Locator): Boolean {
        return navigatorFragment?.go(locator, animated = true) ?: false
    }

    suspend fun applyDecorations(decorations: List<Decoration>, group: String) {
        navigatorFragment?.applyDecorations(decorations, group)
    }

    suspend fun checkSelection(): Locator? {
        return navigatorFragment?.currentSelection()?.locator
    }
}
