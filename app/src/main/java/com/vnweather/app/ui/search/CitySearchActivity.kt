package com.vnweather.app.ui.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vnweather.app.R
import com.vnweather.app.WeatherApp
import com.vnweather.app.databinding.ActivityCitySearchBinding
import com.vnweather.app.domain.City
import com.vnweather.app.util.LocaleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * City / district picker.
 *
 * Typing queries the Open-Meteo geocoding API with language=vi, so Vietnamese
 * place names come back properly accented ("Đà Nẵng", "Quận Hoàn Kiếm").
 * Input is debounced by 400 ms so a slow 3G connection is not hammered on
 * every keystroke.
 */
class CitySearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCitySearchBinding
    private lateinit var adapter: CityAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var searchJob: Job? = null

    private val repository by lazy { (application as WeatherApp).repository }
    private val savedCities by lazy { (application as WeatherApp).savedCities }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.search_title)

        adapter = CityAdapter(
            onClick = { city -> returnCity(city) },
            onDelete = { city ->
                savedCities.remove(city)
                showSavedCities()
            }
        )
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter
        binding.recyclerResults.setHasFixedSize(true)

        binding.buttonUseLocation.setOnClickListener {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_USE_CURRENT_LOCATION, true))
            finish()
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                scheduleSearch(s?.toString().orEmpty().trim())
            }
        })

        showSavedCities()
    }

    private fun scheduleSearch(query: String) {
        pendingSearch?.let { handler.removeCallbacks(it) }
        searchJob?.cancel()

        if (query.length < MIN_QUERY_LENGTH) {
            showSavedCities()
            return
        }

        val runnable = Runnable { runSearch(query) }
        pendingSearch = runnable
        handler.postDelayed(runnable, DEBOUNCE_MILLIS)
    }

    private fun runSearch(query: String) {
        binding.progress.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE
        binding.textSectionTitle.setText(R.string.search_results)
        binding.buttonUseLocation.visibility = View.GONE

        searchJob = lifecycleScope.launch {
            val result = repository.searchCities(query, LocaleHelper.geocodingLanguage())
            binding.progress.visibility = View.GONE
            result.fold(
                onSuccess = { cities ->
                    adapter.submit(cities, showDelete = false)
                    binding.textEmpty.apply {
                        setText(R.string.search_no_results)
                        visibility = if (cities.isEmpty()) View.VISIBLE else View.GONE
                    }
                },
                onFailure = {
                    adapter.submit(emptyList(), showDelete = false)
                    binding.textEmpty.apply {
                        setText(R.string.error_no_network)
                        visibility = View.VISIBLE
                    }
                }
            )
        }
    }

    private fun showSavedCities() {
        binding.progress.visibility = View.GONE
        binding.buttonUseLocation.visibility = View.VISIBLE
        binding.textSectionTitle.setText(R.string.saved_cities)
        val cities = savedCities.getSavedCities()
        adapter.submit(cities, showDelete = true)
        binding.textEmpty.apply {
            setText(R.string.saved_cities_empty)
            visibility = if (cities.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun returnCity(city: City) {
        savedCities.save(city)
        val data = Intent().putExtra(EXTRA_CITY, JSON.encodeToString(City.serializer(), city))
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        pendingSearch?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CITY = "extra_city"
        const val EXTRA_USE_CURRENT_LOCATION = "extra_use_current_location"

        private const val DEBOUNCE_MILLIS = 400L
        private const val MIN_QUERY_LENGTH = 2
        private val JSON = Json { ignoreUnknownKeys = true }

        fun cityFrom(intent: Intent): City? {
            val raw = intent.getStringExtra(EXTRA_CITY) ?: return null
            return runCatching { JSON.decodeFromString(City.serializer(), raw) }.getOrNull()
        }
    }
}
