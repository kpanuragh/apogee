package io.apogee.launcher.util

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import androidx.annotation.RequiresApi
import android.view.View
import android.widget.Toast
import io.apogee.launcher.R
import io.apogee.launcher.data.AppInfo

/** Starting apps, and the handful of system screens the launcher links out to. */
object Launch {

    /**
     * The zoom-from-tile transition. Windows 10 Mobile flips the tile away and scales the app
     * up from it; a scale-up from the tile's bounds is the closest the platform gives us.
     */
    private fun optionsFor(source: View?): Bundle? {
        val view = source ?: return null
        if (view.width == 0 || view.height == 0) return null
        return ActivityOptions
            .makeScaleUpAnimation(view, 0, 0, view.width, view.height)
            .toBundle()
    }

    fun app(
        context: Context,
        app: AppInfo,
        user: UserHandle,
        source: View?,
        launcherApps: LauncherApps,
    ) {
        runCatching {
            launcherApps.startMainActivity(app.component, user, rectOf(source), optionsFor(source))
        }.onFailure {
            fallbackLaunch(context, app, source)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun shortcut(
        context: Context,
        shortcut: ShortcutInfo,
        source: View?,
        launcherApps: LauncherApps,
    ) {
        runCatching {
            launcherApps.startShortcut(shortcut, rectOf(source), optionsFor(source))
        }.onFailure {
            Toast.makeText(context, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun rectOf(view: View?): android.graphics.Rect? {
        if (view == null || view.width == 0) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return android.graphics.Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height,
        )
    }

    private fun fallbackLaunch(context: Context, app: AppInfo, source: View?) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent, optionsFor(source))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun appInfo(context: Context, app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivitySafely(intent)
    }

    fun uninstall(context: Context, app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivitySafely(intent)
    }

    fun wallpaperPicker(context: Context) {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        context.startActivitySafely(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun systemSettings(context: Context) {
        context.startActivitySafely(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Where the user picks their home app, so Apogee can be made the default. */
    fun homeChooser(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.ACTION_HOME_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        context.startActivitySafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun notificationAccess(context: Context) {
        context.startActivitySafely(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun Context.startActivitySafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
    }
}
