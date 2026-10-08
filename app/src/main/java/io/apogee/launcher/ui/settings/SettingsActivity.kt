package io.apogee.launcher.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import io.apogee.launcher.ApogeeApp
import io.apogee.launcher.BuildConfig
import io.apogee.launcher.R
import io.apogee.launcher.data.Prefs
import io.apogee.launcher.util.AccentPalette
import io.apogee.launcher.util.Launch
import io.apogee.launcher.util.applySystemBarPadding
import io.apogee.launcher.util.dp

/**
 * Settings, laid out the way Windows 10 Mobile lays out its own: lowercase headers, flat rows,
 * no cards. Built in code because every row is a one-off.
 */
class SettingsActivity : AppCompatActivity() {

    private val app by lazy { ApogeeApp.from(this) }
    private val prefs get() = app.prefs
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        container = findViewById(R.id.settings_container)
        findViewById<ScrollView>(R.id.settings_scroll)
            .applySystemBarPadding(top = true, bottom = true)
        build()
    }

    override fun onResume() {
        super.onResume()
        // Notification access may have been granted while we were away.
        rebuild()
    }

    private fun rebuild() {
        // Keep the title, drop the generated rows.
        while (container.childCount > 1) container.removeViewAt(1)
        build()
    }

    private fun build() {
        header(R.string.settings_appearance)
        accentGrid()
        themeRow()
        switchRow(
            R.string.pref_transparent_tiles,
            R.string.pref_transparent_tiles_summary,
            prefs.transparentTiles,
        ) { prefs.transparentTiles = it }
        switchRow(
            R.string.pref_monochrome,
            R.string.pref_monochrome_summary,
            prefs.monochromeIcons,
        ) { prefs.monochromeIcons = it }
        switchRow(
            R.string.pref_app_colours,
            R.string.pref_app_colours_summary,
            prefs.colorFromIcon,
        ) { prefs.colorFromIcon = it }

        header(R.string.settings_start)
        switchRow(
            R.string.pref_show_more_tiles,
            R.string.pref_show_more_tiles_summary,
            prefs.showMoreTiles,
        ) { prefs.showMoreTiles = it }
        actionRow(getString(R.string.pref_wallpaper), null) { Launch.wallpaperPicker(this) }
        actionRow(
            getString(R.string.pref_reset_layout),
            getString(R.string.pref_reset_layout_summary),
        ) {
            app.tiles.reset(app.apps.apps.value)
            Toast.makeText(this, R.string.layout_reset, Toast.LENGTH_SHORT).show()
        }

        header(R.string.settings_behaviour)
        switchRow(R.string.pref_live_tiles, R.string.pref_live_tiles_summary, prefs.liveTiles) {
            prefs.liveTiles = it
        }
        switchRow(R.string.pref_tilt, R.string.pref_tilt_summary, prefs.tiltOnPress) {
            prefs.tiltOnPress = it
        }
        badgeRow()
        hiddenApps()

        header(R.string.settings_about)
        actionRow(
            getString(R.string.pref_set_default),
            getString(R.string.pref_set_default_summary),
        ) { Launch.homeChooser(this) }
        actionRow(getString(R.string.pref_version), BuildConfig.VERSION_NAME, null)
    }

    // ---------------------------------------------------------------- row builders

    private fun header(textRes: Int) {
        val view = TextView(this).apply {
            text = getString(textRes)
            setTextColor(prefs.accent)
            textSize = 15f
            letterSpacing = 0.06f
            setPadding(dp(20f), dp(26f), dp(20f), dp(8f))
        }
        container.addView(view, matchWidth())
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20f), dp(12f), dp(20f), dp(12f))
        setBackgroundResource(android.R.drawable.list_selector_background)
    }

    private fun labelColumn(title: String, summary: String?): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 17f
                    setTextColor(textPrimary())
                },
            )
            if (!summary.isNullOrEmpty()) {
                addView(
                    TextView(context).apply {
                        text = summary
                        textSize = 13f
                        setTextColor(textSecondary())
                    },
                )
            }
        }

    private fun switchRow(
        titleRes: Int,
        summaryRes: Int,
        initial: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val row = row()
        val labels = labelColumn(getString(titleRes), getString(summaryRes))
        row.addView(
            labels,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val toggle = SwitchCompat(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(toggle)
        row.setOnClickListener { toggle.toggle() }
        container.addView(row, matchWidth())
    }

    private fun actionRow(title: String, summary: String?, onClick: (() -> Unit)?) {
        val row = row()
        row.addView(
            labelColumn(title, summary),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (onClick != null) {
            row.setOnClickListener { onClick() }
        } else {
            row.isClickable = false
            row.background = null
        }
        container.addView(row, matchWidth())
    }

    private fun themeRow() {
        val options = listOf(
            R.string.pref_theme_dark to AppCompatDelegate.MODE_NIGHT_YES,
            R.string.pref_theme_light to AppCompatDelegate.MODE_NIGHT_NO,
            R.string.pref_theme_system to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        )
        val row = row()
        row.orientation = LinearLayout.VERTICAL
        row.addView(labelColumn(getString(R.string.pref_theme), null), matchWidth())
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10f), 0, 0)
        }
        for ((labelRes, mode) in options) {
            val chip = TextView(this).apply {
                text = getString(labelRes)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                val selected = prefs.nightMode == mode
                setBackgroundColor(if (selected) prefs.accent else Color.TRANSPARENT)
                setTextColor(
                    if (selected) AccentPalette.contrastOn(prefs.accent) else textPrimary(),
                )
                setOnClickListener {
                    prefs.nightMode = mode
                    AppCompatDelegate.setDefaultNightMode(mode)
                    rebuild()
                }
            }
            strip.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(6f) },
            )
        }
        row.addView(strip, matchWidth())
        row.isClickable = false
        row.background = null
        container.addView(row, matchWidth())
    }

    /** The twenty accent swatches, in a wrapping grid of flat squares. */
    private fun accentGrid() {
        container.addView(
            TextView(this).apply {
                text = getString(R.string.pref_accent)
                textSize = 17f
                setTextColor(textPrimary())
                setPadding(dp(20f), 0, dp(20f), dp(2f))
            },
            matchWidth(),
        )
        val label = TextView(this).apply {
            text = getString(R.string.pref_accent_summary)
            textSize = 13f
            setTextColor(textSecondary())
            setPadding(dp(20f), 0, dp(20f), dp(10f))
        }
        container.addView(label, matchWidth())

        val perRow = 5
        var current: LinearLayout? = null
        AccentPalette.ALL.forEachIndexed { index, accent ->
            if (index % perRow == 0) {
                current = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(20f), 0, dp(20f), dp(6f))
                }
                container.addView(current, matchWidth())
            }
            val swatch = FrameLayout(this).apply {
                setBackgroundColor(accent.color)
                contentDescription = accent.name
                if (prefs.accent == accent.color) {
                    addView(
                        ImageView(context).apply {
                            setImageResource(R.drawable.ic_check)
                            setColorFilter(AccentPalette.contrastOn(accent.color))
                        },
                        FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER),
                    )
                }
                setOnClickListener {
                    prefs.accent = accent.color
                    rebuild()
                }
            }
            current?.addView(
                swatch,
                LinearLayout.LayoutParams(0, dp(52f), 1f).apply { marginEnd = dp(6f) },
            )
        }
    }

    private fun badgeRow() {
        val granted = isNotificationAccessGranted()
        val row = row()
        row.addView(
            labelColumn(
                getString(R.string.pref_badges),
                if (granted) {
                    getString(R.string.pref_badges_summary)
                } else {
                    getString(R.string.pref_badges_grant)
                },
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val toggle = SwitchCompat(this).apply {
            isChecked = prefs.badgesEnabled && granted
            setOnCheckedChangeListener { button, checked ->
                if (checked && !isNotificationAccessGranted()) {
                    button.isChecked = false
                    Launch.notificationAccess(this@SettingsActivity)
                } else {
                    prefs.badgesEnabled = checked
                }
            }
        }
        row.addView(toggle)
        row.setOnClickListener { toggle.toggle() }
        container.addView(row, matchWidth())
    }

    private fun hiddenApps() {
        val hidden = prefs.hiddenApps
        if (hidden.isEmpty()) return
        header(R.string.pref_hidden_apps)
        val byKey = app.apps.apps.value.associateBy { it.key }
        for (key in hidden.sorted()) {
            val info = byKey[key]
            actionRow(
                info?.label ?: key.substringAfterLast('/'),
                getString(R.string.unhide_app),
            ) {
                prefs.setHidden(key, false)
                rebuild()
            }
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun textPrimary(): Int =
        if (isNight()) Color.WHITE else Color.BLACK

    private fun textSecondary(): Int =
        if (isNight()) 0xB3FFFFFF.toInt() else 0x99000000.toInt()

    private fun isNight(): Boolean =
        resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}
