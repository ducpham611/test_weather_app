package com.vnweather.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.vnweather.app.R
import com.vnweather.app.WeatherApp
import com.vnweather.app.data.local.SettingsStore
import com.vnweather.app.databinding.ActivitySettingsBinding
import com.vnweather.app.ui.widget.WeatherWidgetProvider
import com.vnweather.app.ui.widget.WidgetRefreshScheduler
import com.vnweather.app.util.Diagnostics
import com.vnweather.app.util.LocaleHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Units, language, refresh interval, theme, and a link to the About screen. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings: SettingsStore by lazy { (application as WeatherApp).settings }

    private val refreshOptions = listOf(15, 30, 45, 60)
    private val backgroundOptions = listOf(1L, 3L, 6L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings)

        setupModel()
        setupApiKey()
        setupTemperature()
        setupWind()
        setupLanguage()
        setupTheme()
        setupAnimatedBackground()
        setupRefresh()

        binding.rowAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        binding.rowDiagnostics.setOnClickListener { runDiagnostics() }
    }

    private fun setupModel() {
        binding.radioModelEcmwf.isChecked = settings.weatherModel == SettingsStore.MODEL_ECMWF
        binding.radioModelGfs.isChecked = settings.weatherModel == SettingsStore.MODEL_GFS
        binding.radioModelTomorrow.isChecked = settings.weatherModel == SettingsStore.MODEL_TOMORROW
        binding.groupModel.setOnCheckedChangeListener { _, id ->
            // The cache is keyed per model, so the next load refetches instead
            // of showing the other provider's numbers.
            settings.weatherModel = when (id) {
                R.id.radioModelGfs -> SettingsStore.MODEL_GFS
                R.id.radioModelTomorrow -> SettingsStore.MODEL_TOMORROW
                else -> SettingsStore.MODEL_ECMWF
            }
            updateApiKeyHint()
        }
    }

    /**
     * Key entry for providers that require one. Saved as you type so leaving
     * the screen cannot lose it, and trimmed because pasted keys often carry
     * a trailing space or newline.
     */
    private fun setupApiKey() {
        binding.editApiKey.setText(settings.tomorrowApiKey)
        binding.editApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                settings.tomorrowApiKey = s?.toString().orEmpty()
            }
        })
        updateApiKeyHint()
    }

    private fun updateApiKeyHint() {
        val needsKey = settings.weatherModel == SettingsStore.MODEL_TOMORROW
        binding.textApiKeyNote.setText(
            if (needsKey) R.string.api_key_note_required else R.string.api_key_note
        )
    }

    private fun setupTemperature() {
        binding.radioCelsius.isChecked = settings.temperatureUnit == SettingsStore.UNIT_CELSIUS
        binding.radioFahrenheit.isChecked = settings.temperatureUnit == SettingsStore.UNIT_FAHRENHEIT
        binding.groupTemperature.setOnCheckedChangeListener { _, id ->
            settings.temperatureUnit = if (id == R.id.radioFahrenheit) {
                SettingsStore.UNIT_FAHRENHEIT
            } else {
                SettingsStore.UNIT_CELSIUS
            }
        }
    }

    private fun setupWind() {
        binding.radioKmh.isChecked = settings.windUnit == SettingsStore.UNIT_KMH
        binding.radioMs.isChecked = settings.windUnit == SettingsStore.UNIT_MS
        binding.groupWind.setOnCheckedChangeListener { _, id ->
            settings.windUnit = if (id == R.id.radioMs) {
                SettingsStore.UNIT_MS
            } else {
                SettingsStore.UNIT_KMH
            }
        }
    }

    private fun setupLanguage() {
        val current = LocaleHelper.currentLanguageTag()
        when {
            current.startsWith(LocaleHelper.LANG_VIETNAMESE) -> binding.radioVietnamese.isChecked = true
            current.startsWith(LocaleHelper.LANG_ENGLISH) -> binding.radioEnglish.isChecked = true
            else -> binding.radioSystemLanguage.isChecked = true
        }
        binding.groupLanguage.setOnCheckedChangeListener { _, id ->
            val tag = when (id) {
                R.id.radioVietnamese -> LocaleHelper.LANG_VIETNAMESE
                R.id.radioEnglish -> LocaleHelper.LANG_ENGLISH
                else -> LocaleHelper.LANG_SYSTEM
            }
            LocaleHelper.applyLanguage(tag)
            // The widget cannot read AppCompat's locale on API < 33, so keep a
            // copy it can read, then redraw it in the new language.
            settings.languageTag = tag
            WeatherWidgetProvider.refreshAll(this)
        }
    }

    private fun setupTheme() {
        when (settings.nightMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.radioLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.radioDark.isChecked = true
            else -> binding.radioSystemTheme.isChecked = true
        }
        binding.groupTheme.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            settings.nightMode = mode
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun setupAnimatedBackground() {
        binding.groupBackground.check(
            when (settings.backgroundStyle) {
                SettingsStore.BG_VN -> R.id.radioBgVn
                SettingsStore.BG_PLAIN -> R.id.radioBgPlain
                else -> R.id.radioBgBreezy
            }
        )
        binding.groupBackground.setOnCheckedChangeListener { _, id ->
            // MainActivity re-reads this in onResume and swaps the
            // background, so the change is visible as soon as you go back.
            settings.backgroundStyle = when (id) {
                R.id.radioBgVn -> SettingsStore.BG_VN
                R.id.radioBgPlain -> SettingsStore.BG_PLAIN
                else -> SettingsStore.BG_BREEZY
            }
        }
    }

    private fun setupRefresh() {
        binding.spinnerRefresh.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            refreshOptions.map { getString(R.string.minutes_format, it) }
        )
        binding.spinnerRefresh.setSelection(
            refreshOptions.indexOf(settings.refreshMinutes).coerceAtLeast(0)
        )
        binding.spinnerRefresh.onItemSelectedListener = simpleListener { position ->
            settings.refreshMinutes = refreshOptions[position]
        }

        binding.spinnerBackground.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            backgroundOptions.map { getString(R.string.hours_format, it.toInt()) }
        )
        binding.spinnerBackground.setSelection(
            backgroundOptions.indexOf(settings.backgroundRefreshHours).coerceAtLeast(0)
        )
        binding.spinnerBackground.onItemSelectedListener = simpleListener { position ->
            settings.backgroundRefreshHours = backgroundOptions[position]
            WidgetRefreshScheduler.schedule(this, settings.backgroundRefreshHours)
        }
    }

    private fun simpleListener(onSelected: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) = onSelected(position)

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

    /** Runs a live connection self-test and shows the raw result. */
    private fun runDiagnostics() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.diagnostics)
            .setMessage(R.string.diagnostics_running)
            .setCancelable(true)
            .show()

        lifecycleScope.launch {
            val report = Diagnostics.run(this@SettingsActivity)
            dialog.dismiss()
            androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                .setTitle(R.string.diagnostics)
                .setMessage(report)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.copy) { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("diagnostics", report)
                    )
                }
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
