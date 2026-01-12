package com.currencyconverter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    /**
     * Справочник "сколько единиц валюты за 1 USDT".
     * Используется как fallback до загрузки актуальных курсов.
     */
    private val fallbackRates: Map<String, Double> = mapOf(
        "USD" to 1.00,
        "EUR" to 0.93,
        "GBP" to 0.79,
        "TRY" to 29.00,
        "RUB" to 92.00,
        "DKK" to 6.90,
        "SEK" to 10.40,
        "AUD" to 1.52,
        "CAD" to 1.36,
        "JPY" to 155.00,
    )
    private val convertRateUSDT = fallbackRates.toMutableMap()
    private var baseCurrency = "TRY"
    private var convertedToCurrency = "RUB"

    lateinit var et_firstConversion: EditText
    lateinit var et_secondConversion: TextView
    lateinit var et_resultText: TextView
    private lateinit var refreshButton: Button
    private lateinit var repository: RatesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        et_firstConversion = findViewById(R.id.et_firstConversion)
        et_secondConversion = findViewById(R.id.et_secondConversion)
        et_resultText = findViewById(R.id.et_result)
        refreshButton = findViewById(R.id.button_refresh)

        spinnerSetup()
        textChangedStuff()
        initRates()
        setupRefresh()
    }

    private fun calculateConversion() {
        val leftCurrency = baseCurrency
        if (et_firstConversion.text.isEmpty()) {
            return
        }
        val leftAmount = parseAmount(et_firstConversion.text.toString()) ?: run {
            Toast.makeText(applicationContext, getString(R.string.error_invalid_amount), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val rightCurrency = convertedToCurrency

        val leftRateToUsdt = convertRateUSDT[leftCurrency]
        val rightRateToUsdt = convertRateUSDT[rightCurrency]

        if (leftRateToUsdt == null || rightRateToUsdt == null) {
            Toast.makeText(applicationContext, getString(R.string.error_no_rate), Toast.LENGTH_SHORT)
                .show()
            return
        }

        val res = convertAmount(leftAmount, leftCurrency, rightCurrency, convertRateUSDT) ?: return
        val leftToUsdtAmount = leftAmount / leftRateToUsdt

        et_secondConversion.text = String.format(Locale.getDefault(), "%.2f", res)
        et_resultText.text = String.format(
            Locale.getDefault(),
            getString(R.string.result_format),
            leftAmount,
            leftCurrency,
            res,
            rightCurrency,
            leftToUsdtAmount
        )
    }

    private fun initRates() {
        val database = AppDatabase.build(this)
        repository = RatesRepository(database.currencyRateDao())

        lifecycleScope.launch {
            val cachedRates = repository.getCachedRates(CONVERSION_BASE)
            if (cachedRates.isNotEmpty()) {
                updateRates(cachedRates.associate { it.code to it.rate })
            }

            refreshRatesIfStale(force = false, hadCachedRates = cachedRates.isNotEmpty())
        }
    }

    private fun setupRefresh() {
        refreshButton.setOnClickListener {
            lifecycleScope.launch {
                refreshRatesIfStale(force = false, hadCachedRates = true, showTooSoonMessage = true)
            }
        }
    }

    private suspend fun refreshRatesIfStale(
        force: Boolean,
        hadCachedRates: Boolean,
        showTooSoonMessage: Boolean = false
    ) {
        val lastUpdated = repository.getLastUpdated(CONVERSION_BASE) ?: 0L
        val now = System.currentTimeMillis()
        val isStale = now - lastUpdated >= ONE_HOUR_MILLIS
        if (!force && !isStale) {
            if (showTooSoonMessage) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.refresh_too_soon),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val fetchedRates = runCatching { repository.fetchRates(API_BASE) }.getOrNull()
        val normalizedRates = fetchedRates?.rates?.let { rates ->
            convertRatesToBase(CONVERSION_BASE, API_BASE, rates)
        }
        if (!normalizedRates.isNullOrEmpty()) {
            val updatedAtMillis = fetchedRates.updatedAtSeconds * 1000
            repository.replaceRates(CONVERSION_BASE, normalizedRates, updatedAtMillis)
            updateRates(normalizedRates)
            calculateConversion()
        } else if (!hadCachedRates) {
            Toast.makeText(
                applicationContext,
                getString(R.string.error_refresh_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateRates(rates: Map<String, Double>) {
        if (rates.isEmpty()) {
            return
        }
        convertRateUSDT.clear()
        convertRateUSDT.putAll(rates)
    }


    private fun textChangedStuff() {
        et_firstConversion.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                calculateConversion()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })

    }

    private fun spinnerSetup() {
        val spinner: Spinner = findViewById(R.id.spinner_firstConversion)
        val spinner2: Spinner = findViewById(R.id.spinner_secondConversion)

        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.currencies,
            android.R.layout.simple_spinner_item
        ).also { itemAdapter ->
            itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinner.adapter = adapter
        spinner2.adapter = adapter

        setSpinnerSelection(spinner, baseCurrency)
        setSpinnerSelection(spinner2, convertedToCurrency)

        spinner.onItemSelectedListener = (object : OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op to keep the last selection.
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                baseCurrency = parent?.getItemAtPosition(position).toString()
                calculateConversion()
            }

        })

        spinner2.onItemSelectedListener = (object : OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op to keep the last selection.
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                convertedToCurrency = parent?.getItemAtPosition(position).toString()
                calculateConversion()
            }

        })
    }

    private fun parseAmount(rawValue: String): Double? {
        val normalized = rawValue.trim().replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    private fun setSpinnerSelection(spinner: Spinner, currency: String) {
        val position = (0 until spinner.count)
            .firstOrNull { spinner.getItemAtPosition(it).toString() == currency }
            ?: return
        spinner.setSelection(position)
    }

    companion object {
        private const val API_BASE = "USD"
        private const val CONVERSION_BASE = "USDT"
        private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
    }
}
