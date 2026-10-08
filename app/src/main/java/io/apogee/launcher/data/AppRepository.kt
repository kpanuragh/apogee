package io.apogee.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import io.apogee.launcher.util.IconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The launcher's view of installed apps. Backed by [LauncherApps] rather than
 * PackageManager so work-profile apps and package-visibility rules are handled for us.
 */
class AppRepository(private val context: Context, private val prefs: Prefs) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager
    private val iconLoader = IconLoader(context)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Every launchable app, sorted by label; includes apps the user has hidden. */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reloadIcons()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reloadIcons()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reloadIcons()
        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) = reloadIcons()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) = reloadIcons()
    }

    fun start() {
        launcherApps.registerCallback(callback)
        reload()
    }

    fun stop() {
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    /** Re-reads labels and components, keeping already-decoded icons. */
    fun reload() {
        scope.launch { _apps.value = load() }
    }

    /**
     * Re-reads everything including icons. Used when a package is installed, updated or
     * removed, since an update can change an app's icon as well as its label.
     */
    private fun reloadIcons() {
        iconLoader.clear()
        reload()
    }

    private suspend fun load(): List<AppInfo> = withContext(Dispatchers.IO) {
        val myUser = Process.myUserHandle()
        val result = ArrayList<AppInfo>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities = runCatching { launcherApps.getActivityList(null, user) }
                .getOrDefault(emptyList())
            for (activity in activities) {
                val icon = iconLoader.loadIcon(activity, user)
                result += AppInfo(
                    component = activity.componentName,
                    userSerial = serial,
                    label = activity.label?.toString()?.trim().orEmpty()
                        .ifEmpty { activity.componentName.packageName },
                    icon = icon.drawable,
                    iconColor = icon.dominantColor,
                    iconGeneration = icon.generation,
                    firstInstallTime = activity.firstInstallTime,
                    isWorkProfile = user != myUser,
                )
            }
        }
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        result
    }

    /** Apps to show in the app list: everything the user has not hidden. */
    fun visible(all: List<AppInfo> = _apps.value): List<AppInfo> {
        val hidden = prefs.hiddenApps
        return if (hidden.isEmpty()) all else all.filterNot { it.key in hidden }
    }

    fun find(component: ComponentName?, userSerial: Long): AppInfo? {
        if (component == null) return null
        return _apps.value.firstOrNull {
            it.component == component && it.userSerial == userSerial
        }
    }

    fun userFor(serial: Long): UserHandle =
        userManager.getUserForSerialNumber(serial) ?: Process.myUserHandle()

    /**
     * The app's own shortcuts, for the hold menu — the closest Android equivalent of a
     * Windows Phone jump list. Shortcuts arrived in API 25, and only the default launcher is
     * allowed to read them.
     */
    fun shortcutsFor(app: AppInfo): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) {
            return emptyList()
        }
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
            )
        return runCatching { launcherApps.getShortcuts(query, userFor(app.userSerial)) }
            .getOrNull()
            .orEmpty()
            .filter { it.isEnabled }
            .sortedBy { it.rank }
    }

    fun launcherApps(): LauncherApps = launcherApps
}
