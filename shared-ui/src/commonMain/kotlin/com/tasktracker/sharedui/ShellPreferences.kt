package com.tasktracker.sharedui

import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shell-level preferences manager persisting themes, accent palettes, glass intensity, and user session profiles.
 */
class ShellPreferences(
    private val store: UserDefaultsStore,
    private val router: Router,
) {

    private val _themeScheme = MutableStateFlow(ThemeScheme.System)
    val themeScheme: StateFlow<ThemeScheme> = _themeScheme.asStateFlow()

    private val _accentPalette = MutableStateFlow(AccentPalette.Classic)
    val accentPalette: StateFlow<AccentPalette> = _accentPalette.asStateFlow()

    /** Glass material intensity fraction in `0f..1f`. */
    private val _glassIntensity = MutableStateFlow(1f)
    val glassIntensity: StateFlow<Float> = _glassIntensity.asStateFlow()

    private val _forceOffline = MutableStateFlow(false)
    val forceOffline: StateFlow<Boolean> = _forceOffline.asStateFlow()

    private var cachedIdentity: UserIdentity? = null

    private val _currentUserName = MutableStateFlow<String?>(null)
    val currentUserName: StateFlow<String?> = _currentUserName.asStateFlow()

    /** Signed-in user ID, or null if unauthenticated. */
    private val _currentUserID = MutableStateFlow<UserID?>(null)
    val currentUserID: StateFlow<UserID?> = _currentUserID.asStateFlow()

    /** Signed-in user role, defaulting to Member. */
    private val _currentUserRole = MutableStateFlow<UserRole?>(null)
    val currentUserRole: StateFlow<UserRole?> = _currentUserRole.asStateFlow()

    var lastVisitedProjectID: String? = null
        private set

    /** Loads stored shell preferences at launch. */
    suspend fun restore() {
        _themeScheme.value = ThemeScheme.fromStoredValue(
            runCatching { store.value(THEME_KEY) }.getOrNull(),
        )
        _accentPalette.value = AccentPalette.fromStoredValue(
            runCatching { store.value(ACCENT_PALETTE_KEY) }.getOrNull(),
        )
        _glassIntensity.value = runCatching {
            store.value(GLASS_INTENSITY_KEY)?.toFloatOrNull()
        }.getOrNull()?.coerceIn(0f, 1f) ?: 1f

        val offline = runCatching { store.value(FORCE_OFFLINE_KEY) }.getOrNull()
        _forceOffline.value = offline == TRUTHY

        val projectID = runCatching { store.value(router.lastProjectDefaultsKey) }.getOrNull()
        lastVisitedProjectID = projectID?.takeIf { it.isNotBlank() }
    }

    /** Refreshes current user identity and greeting name from store. */
    suspend fun refreshCurrentUser() {
        val profile = store.value<UserIdentity>(DefaultsKey.CachedProfile)
        cachedIdentity = profile
        _currentUserName.value = profile?.let { identity ->
            greetingFor(identity, store.value<UserDetails>(DefaultsKey.UserProfile))
        }
        _currentUserID.value = profile?.id
        _currentUserRole.value = profile?.role
    }

    /** Applies user details from settings port emission. */
    fun applyUserDetails(details: UserDetails) {
        val identity = cachedIdentity ?: return
        _currentUserName.value = greetingFor(identity, details)
    }

    private fun greetingFor(identity: UserIdentity, details: UserDetails?): String {
        val name = details?.fullName?.trim().orEmpty()
        if (details == null || name.isEmpty()) return identity.preferredGreeting

        val belongsToThisAccount = identity.email.isBlank() ||
                details.email.isBlank() ||
                details.email.equals(identity.email, ignoreCase = true)

        return if (belongsToThisAccount) name else identity.preferredGreeting
    }

    suspend fun setThemeScheme(scheme: ThemeScheme) {
        _themeScheme.value = scheme
        runCatching { store.set(scheme.wireValue, THEME_KEY) }
    }

    suspend fun setAccentPalette(palette: AccentPalette) {
        _accentPalette.value = palette
        runCatching { store.set(palette.wireValue, ACCENT_PALETTE_KEY) }
    }

    fun previewGlassIntensity(intensity: Float) {
        _glassIntensity.value = intensity.coerceIn(0f, 1f)
    }

    /** Updates state and persists glass intensity value. */
    suspend fun commitGlassIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        _glassIntensity.value = clamped
        runCatching { store.set(clamped.toString(), GLASS_INTENSITY_KEY) }
    }

    suspend fun setForceOffline(forced: Boolean) {
        _forceOffline.value = forced
        runCatching { store.set(forced.toString(), FORCE_OFFLINE_KEY) }
    }

    suspend fun rememberActiveProject(projectID: ProjectID?) {
        runCatching { store.set(projectID?.stringValue.orEmpty(), router.lastProjectDefaultsKey) }
    }

    companion object {
        const val THEME_KEY: String = ThemeScheme.defaultsKey
        const val ACCENT_PALETTE_KEY: String = "com.tasktracker.settings.accentPalette"
        const val GLASS_INTENSITY_KEY: String = "com.tasktracker.settings.glassIntensity"
        const val FORCE_OFFLINE_KEY: String = "com.tasktracker.settings.forceOffline"
        private const val TRUTHY = "true"
    }
}