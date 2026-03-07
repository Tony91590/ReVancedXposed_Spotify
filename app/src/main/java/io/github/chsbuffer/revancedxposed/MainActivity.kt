package io.github.chsbuffer.revancedxposed

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    companion object {
        const val PACKAGE_NAME = "io.github.chsbuffer.revancedxposed"
        const val PREF_FILE = "spotify_lyrics_mod_prefs"
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

        // 1. BIND CORE MODULES
        bindSwitch(R.id.switchPremium, "enable_premium", true)
        bindSwitch(R.id.switchAudioAds, "enable_audio_ads_block", true)
        bindSwitch(R.id.switchVisualAds, "enable_visual_ads_block", true)
        bindSwitch(R.id.switchUiFixes, "enable_ui_fixes", true)

        // 2. BIND PRIVACY & SPOOFING
        bindSwitch(R.id.switchTelemetry, "enable_telemetry_block", true)
        bindSwitch(R.id.switchSpoofer, "enable_device_spoofer", true)
        bindSwitch(R.id.switchSanitize, "enable_sanitize_links", true)

        // 3. BIND INTERFACE INJECTIONS
        bindSwitch(R.id.switchBehindTheTracks, "enable_behind_the_tracks", true)
        bindSwitch(R.id.switchLiveRadar, "enable_live_radar", true)
        bindSwitch(R.id.switchFrostedNav, "enable_frosted_nav", true)
        bindSwitch(R.id.switchNavbarPinner, "enable_navbar_pinner", true)
        bindSwitch(R.id.switchWidgets, "enable_widget_fix", true)
        bindSwitch(R.id.switchWhoSampled, "enable_who_sampled", true)

        // 4. BIND BEAUTIFUL LYRICS
        bindSwitch(R.id.switchLyrics, "enable_lyrics", true)
        bindSwitch(R.id.switchIsland, "enable_island", true)
        bindSwitch(R.id.switchBackground, "enable_background", true)
        bindSwitch(R.id.switchSweep, "enable_sweep", true)
        bindSwitch(R.id.switchRhymes, "enable_rhyme_highlight", true)

        // 5. BIND THEME ENGINE
        bindSwitch(R.id.switchAmoled, "enable_amoled_theme", false)
        bindSwitch(R.id.switchTheme, "enable_custom_theme", false)
        bindSwitch(R.id.switchChroma, "enable_chroma_canvas", false)

        setupLyricsSliders()
        setupThemeColors()

        findViewById<Button>(R.id.btnApply).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:com.spotify.music")
                startActivity(intent)
                Toast.makeText(this, "Force Stop Spotify to apply changes", Toast.LENGTH_LONG)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }

        fixPermissions()
    }

    private fun bindSwitch(id: Int, key: String, default: Boolean) {
        val switch = findViewById<Switch>(id)
        switch.isChecked = prefs.getBoolean(key, default)
        switch.setOnCheckedChangeListener { _, isChecked -> saveAndBroadcast(key, isChecked) }
    }

    private fun setupLyricsSliders() {
        val seekTextSize = findViewById<SeekBar>(R.id.seekTextSize)
        val tvTextSizeVal = findViewById<TextView>(R.id.tvTextSizeVal)
        val currentTextSize = prefs.getInt("text_size", 26)
        seekTextSize.max = 20
        seekTextSize.progress = currentTextSize - 16
        tvTextSizeVal.text = "${currentTextSize} sp"

        seekTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 16
                tvTextSizeVal.text = "${value} sp"
                if (fromUser) saveAndBroadcast("text_size", value)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        val seekSpeed = findViewById<SeekBar>(R.id.seekSpeed)
        val tvOrbSpeedVal = findViewById<TextView>(R.id.tvOrbSpeedVal)
        val currentSpeed = prefs.getInt("anim_speed", 5)
        seekSpeed.max = 10
        seekSpeed.progress = currentSpeed
        tvOrbSpeedVal.text = "${currentSpeed / 5.0}x"

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOrbSpeedVal.text = "${progress / 5.0}x"
                if (fromUser) saveAndBroadcast("anim_speed", progress)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    private fun setupThemeColors() {
        val viewColorPreview = findViewById<View>(R.id.viewColorPreview)
        val seekRed = findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = findViewById<SeekBar>(R.id.seekBlue)

        val savedColor = prefs.getInt("custom_theme_color", Color.parseColor("#1ED760"))
        seekRed.progress = Color.red(savedColor)
        seekGreen.progress = Color.green(savedColor)
        seekBlue.progress = Color.blue(savedColor)
        viewColorPreview.setBackgroundColor(savedColor)

        val colorChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newColor = Color.rgb(seekRed.progress, seekGreen.progress, seekBlue.progress)
                viewColorPreview.setBackgroundColor(newColor)
                if (fromUser) saveAndBroadcast("custom_theme_color", newColor)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        }
        seekRed.setOnSeekBarChangeListener(colorChangeListener)
        seekGreen.setOnSeekBarChangeListener(colorChangeListener)
        seekBlue.setOnSeekBarChangeListener(colorChangeListener)
    }

    @SuppressLint("SetWorldReadable")
    private fun saveAndBroadcast(key: String, value: Any) {
        val editor = prefs.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
        }
        editor.apply()

        // Fix permissions AFTER apply, because Android sometimes recreates the file on save
        fixPermissions()

        // 🎯 FIX: Direct the broadcast specifically to Spotify's process!
        // Without setPackage(), modern Android completely blocks the broadcast.
        val intent = Intent("${PACKAGE_NAME}.LIVE_UPDATE")
        intent.setPackage("com.spotify.music")
        when (value) {
            is Boolean -> intent.putExtra(key, value)
            is Int -> intent.putExtra(key, value)
        }
        sendBroadcast(intent)
    }

    @SuppressLint("SetWorldReadable")
    private fun fixPermissions() {
        try {
            // 🎯 FIX: Linux requires execute (+x) and read (+r) on ALL parent folders
            // for another app (like Spotify) to traverse down to the XML file.
            val dataDir = File(applicationInfo.dataDir)
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            val prefsFile = File(sharedPrefsDir, "$PREF_FILE.xml")

            dataDir.setExecutable(true, false)
            dataDir.setReadable(true, false)

            sharedPrefsDir.setExecutable(true, false)
            sharedPrefsDir.setReadable(true, false)

            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false)
                prefsFile.setExecutable(true, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}