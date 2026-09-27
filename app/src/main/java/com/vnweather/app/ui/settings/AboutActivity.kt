package com.vnweather.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vnweather.app.BuildConfig
import com.vnweather.app.R
import com.vnweather.app.databinding.ActivityAboutBinding

/**
 * Attribution screen. Open-Meteo data is CC BY 4.0, so crediting the source
 * is a licence requirement, not a nicety.
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.about)

        binding.textVersion.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
        binding.buttonOpenMeteo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/")))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
