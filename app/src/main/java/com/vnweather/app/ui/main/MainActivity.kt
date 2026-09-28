package com.vnweather.app.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.vnweather.app.R
import com.vnweather.app.WeatherApp
import com.vnweather.app.databinding.ActivityMainBinding
import com.vnweather.app.domain.ErrorType
import com.vnweather.app.domain.Forecast
import com.vnweather.app.domain.UiState
import com.vnweather.app.domain.WeatherCodeMapper
import com.vnweather.app.ui.search.CitySearchActivity
import com.vnweather.app.ui.settings.SettingsActivity
import com.vnweather.app.util.Formatters
import com.vnweather.app.util.LocaleHelper
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val hourlyAdapter = HourlyAdapter()
    private lateinit var dailyList: DailyListRenderer

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        if (data.getBooleanExtra(CitySearchActivity.EXTRA_USE_CURRENT_LOCATION, false)) {
            requestLocationThenLoad()
        } else {
            val city = CitySearchActivity.cityFrom(data) ?: return@registerForActivityResult
            viewModel.selectCity(city)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.useCurrentLocation()
        } else {
            showSnack(getString(R.string.error_location_permission))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        applyFontScaleLayout()
        setupLists()
        setupActions()
        observe()

        viewModel.load()
    }

    /**
     * The three-across detail row (humidity / wind / rain) has no room left
     * once the user raises the system font size, which is what made labels
     * wrap and push the layout around. Above ~1.15x we stack the cells
     * vertically instead. Resource qualifiers cannot express font scale, so
     * this has to happen in code.
     */
    private fun applyFontScaleLayout() {
        val fontScale = resources.configuration.fontScale
        val stack = fontScale > 1.15f
        val row = binding.detailRow

        row.orientation = if (stack) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            val params = child.layoutParams as LinearLayout.LayoutParams
            if (stack) {
                // Weighted 0dp widths collapse to nothing in a vertical row.
                params.width = LinearLayout.LayoutParams.MATCH_PARENT
                params.weight = 0f
            } else {
                params.width = 0
                params.weight = 1f
            }
            child.layoutParams = params
            (child as? LinearLayout)?.gravity =
                if (stack) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
        }
    }

    private fun setupLists() {
        binding.recyclerHourly.apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity, LinearLayoutManager.HORIZONTAL, false
            )
            adapter = hourlyAdapter
            // NEVER setHasFixedSize(true) here. This list is wrap_content inside
            // a ScrollView, so its height depends on its contents. With the flag
            // on, RecyclerView skips the relayout when the adapter finally gets
            // data, the height stays 0, and the section looks empty until some
            // other change forces a fresh layout pass.
            setHasFixedSize(false)
            // Keeping views around avoids re-inflation jank on low-end devices.
            setItemViewCacheSize(12)
        }
        dailyList = DailyListRenderer(binding.dailyContainer)
    }

    private fun setupActions() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.load(forceRefresh = true) }
        binding.buttonRetry.setOnClickListener { viewModel.load(forceRefresh = true) }
        binding.buttonShowMore.setOnClickListener {
            viewModel.toggleShowAllDays()
            // Re-render straight away rather than waiting for an observer.
            (viewModel.state.value as? UiState.Success)?.let { render(it) }
        }
        binding.headerCity.setOnClickListener { openSearch() }

        // Long-press the timestamp to see the raw failure. Invaluable when the
        // UI says one thing and the network is doing another.
        binding.textUpdatedAt.setOnLongClickListener {
            val detail = viewModel.lastErrorDetail ?: getString(R.string.no_recent_error)
            AlertDialog.Builder(this)
                .setTitle(R.string.last_error)
                .setMessage(detail)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
    }

    private fun observe() {
        viewModel.state.observe(this) { state -> render(state) }
    }

    private fun render(state: UiState) {
        when (state) {
            is UiState.Loading -> {
                binding.progress.visibility = View.VISIBLE
                binding.errorGroup.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.progress.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.errorGroup.visibility = View.GONE
                binding.contentGroup.visibility = View.VISIBLE
                bindForecast(state.forecast)
            }
            is UiState.Error -> {
                binding.progress.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                val stale = state.staleForecast
                if (stale != null) {
                    binding.contentGroup.visibility = View.VISIBLE
                    bindForecast(stale)
                    showSnack(messageFor(state.type))
                } else {
                    binding.contentGroup.visibility = View.GONE
                    binding.errorGroup.visibility = View.VISIBLE
                    binding.textError.text = messageFor(state.type)
                }
            }
        }
    }

    private fun bindForecast(forecast: Forecast) {
        val locale = LocaleHelper.currentLocale()
        val tempUnit = viewModel.temperatureUnit
        val windUnit = viewModel.windUnit
        val current = forecast.current

        binding.textCityName.text = forecast.city.name
        binding.textCitySubtitle.apply {
            text = forecast.city.subtitle
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        binding.textTemperature.text = Formatters.temperature(current.temperature, tempUnit)
        binding.textCondition.setText(WeatherCodeMapper.descriptionRes(current.weatherCode))
        binding.imageCurrentIcon.setImageResource(
            WeatherCodeMapper.iconRes(current.weatherCode, current.isDay)
        )
        binding.textFeelsLike.text = getString(
            R.string.feels_like_format,
            Formatters.temperature(current.apparentTemperature, tempUnit)
        )
        binding.textHumidity.text = getString(R.string.percent_format, current.humidity)
        binding.textWind.text = Formatters.wind(current.windSpeed, windUnit)
        binding.textPrecipitation.text = Formatters.precipitation(current.precipitation)

        binding.textUpdatedAt.text = getString(
            if (forecast.fromCache) R.string.updated_at_cached else R.string.updated_at,
            Formatters.clockLabel(forecast.fetchedAtMillis, locale)
        )

        // Hourly: the next 24 hours.
        hourlyAdapter.temperatureUnit = tempUnit
        hourlyAdapter.submitList(upcomingHours(forecast, HOURS_SHOWN)) {
            // submitList computes its diff on a background thread; request a
            // layout pass after it commits so the ScrollView picks up the
            // list's real height on the very first load.
            binding.recyclerHourly.requestLayout()
        }

        // Daily: 3 days by default, every day the model returns when expanded.
        // The list is wrap_content with nested scrolling off, so all 7 rows
        // render inline inside the page scroll - no inner scrollbar.
        val visibleDays = viewModel.visibleDays(forecast).coerceAtMost(forecast.daily.size)
        dailyList.temperatureUnit = tempUnit
        dailyList.render(forecast.daily.take(visibleDays))

        binding.textAttribution.setText(
            if (viewModel.isUsingGfs) R.string.attribution_gfs else R.string.attribution
        )

        val expanded = viewModel.isShowingAllDays
        binding.buttonShowMore.apply {
            visibility = if (forecast.daily.size > visibleDays || expanded) View.VISIBLE else View.GONE
            setText(if (expanded) R.string.show_less else R.string.show_more)
        }
    }

    /** Drop hours already in the past, then take the next [count]. */
    private fun upcomingHours(forecast: Forecast, count: Int) =
        forecast.hourly
            .dropWhile { item ->
                val date = Formatters.parseIsoDateTime(item.timeIso) ?: return@dropWhile false
                date.before(Date(System.currentTimeMillis() - ONE_HOUR_MILLIS))
            }
            .take(count)

    private fun messageFor(type: ErrorType): String = getString(
        when (type) {
            ErrorType.NO_NETWORK -> R.string.error_no_network
            ErrorType.DNS_ERROR -> R.string.error_dns
            ErrorType.TLS_ERROR -> R.string.error_tls
            ErrorType.TIMEOUT -> R.string.error_timeout
            ErrorType.API_ERROR -> R.string.error_api
            ErrorType.LOCATION_UNAVAILABLE -> R.string.error_location
            ErrorType.UNKNOWN -> R.string.error_unknown
        }
    )

    private fun showSnack(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    private fun openSearch() {
        searchLauncher.launch(Intent(this, CitySearchActivity::class.java))
    }

    private fun requestLocationThenLoad() {
        val app = application as WeatherApp
        if (com.vnweather.app.util.LocationProvider(this).hasPermission()) {
            viewModel.useCurrentLocation()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_message)
            .setPositiveButton(R.string.allow) { _, _ ->
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        app.savedCities.useCurrentLocation = false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_search -> { openSearch(); true }
        R.id.action_location -> { requestLocationThenLoad(); true }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyFontScaleLayout()
    }

    override fun onResume() {
        super.onResume()
        // Settings (units / language) may have changed while we were away.
        viewModel.load()
    }

    private companion object {
        const val HOURS_SHOWN = 24          // next 24 hours
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
    }
}

