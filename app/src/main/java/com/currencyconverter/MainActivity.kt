package com.currencyconverter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    /**
     * Fallback rates: units per 1 USDT.
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

    private val currencyNames: Map<String, String> = mapOf(
        "USD" to "US Dollar",
        "EUR" to "Euro",
        "GBP" to "British Pound",
        "TRY" to "Turkish Lira",
        "RUB" to "Russian Ruble",
        "DKK" to "Danish Krone",
        "SEK" to "Swedish Krona",
        "AUD" to "Australian Dollar",
        "CAD" to "Canadian Dollar",
        "JPY" to "Japanese Yen",
    )

    private val convertRateUSDT = fallbackRates.toMutableMap()
    private var baseCurrency = "TRY"
    private var convertedToCurrency = "RUB"
    private var inputValue = ""
    private var lastSavedSignature: String? = null

    private lateinit var inputAmountView: TextView
    private lateinit var outputAmountView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var fromNameView: TextView
    private lateinit var toNameView: TextView
    private lateinit var refreshButton: ImageButton
    private lateinit var swapButton: ImageButton

    private lateinit var spinnerFrom: Spinner
    private lateinit var spinnerTo: Spinner

    private lateinit var historyList: LinearLayout
    private lateinit var settingsText: TextView
    private lateinit var tabConvert: Button
    private lateinit var tabHistory: Button
    private lateinit var tabSettings: Button
    private lateinit var screenConvert: View
    private lateinit var screenHistory: View
    private lateinit var screenSettings: View

    private lateinit var repository: RatesRepository
    private lateinit var conversionDao: ConversionDao

    private var inputJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputAmountView = findViewById(R.id.input_amount)
        outputAmountView = findViewById(R.id.output_amount)
        resultTextView = findViewById(R.id.result_text)
        fromNameView = findViewById(R.id.from_currency_name)
        toNameView = findViewById(R.id.to_currency_name)
        refreshButton = findViewById(R.id.button_refresh)
        swapButton = findViewById(R.id.button_swap)

        spinnerFrom = findViewById(R.id.spinner_firstConversion)
        spinnerTo = findViewById(R.id.spinner_secondConversion)

        historyList = findViewById(R.id.history_list)
        settingsText = findViewById(R.id.settings_text)
        tabConvert = findViewById(R.id.tab_convert)
        tabHistory = findViewById(R.id.tab_history)
        tabSettings = findViewById(R.id.tab_settings)
        screenConvert = findViewById(R.id.screen_convert)
        screenHistory = findViewById(R.id.screen_history)
        screenSettings = findViewById(R.id.screen_settings)

        spinnerSetup()
        setupKeypad()
        initRates()
        setupRefresh()
        setupTabs()
        updateInputDisplay()
    }

    private fun calculateConversion() {
        if (inputValue.isBlank() || inputValue == ".") {
            outputAmountView.text = formatAmount(0.0)
            resultTextView.text = ""
            return
        }

        val leftAmount = parseAmount(inputValue) ?: return
        val leftRateToUsdt = convertRateUSDT[baseCurrency]
        val rightRateToUsdt = convertRateUSDT[convertedToCurrency]

        if (leftRateToUsdt == null || rightRateToUsdt == null) {
            Toast.makeText(applicationContext, getString(R.string.error_no_rate), Toast.LENGTH_SHORT)
                .show()
            return
        }

        val res = convertAmount(leftAmount, baseCurrency, convertedToCurrency, convertRateUSDT) ?: return
        val leftToUsdtAmount = leftAmount / leftRateToUsdt

        outputAmountView.text = formatAmount(res)
        resultTextView.text = String.format(
            Locale.getDefault(),
            getString(R.string.result_format),
            leftAmount,
            baseCurrency,
            res,
            convertedToCurrency,
            leftToUsdtAmount
        )
        saveConversionIfNew(leftAmount, baseCurrency, convertedToCurrency, res)
    }

    private fun scheduleConversion() {
        inputJob?.cancel()
        inputJob = lifecycleScope.launch {
            delay(INPUT_DEBOUNCE_MILLIS)
            calculateConversion()
        }
    }

    private fun initRates() {
        val database = AppDatabase.build(this)
        repository = RatesRepository(database.currencyRateDao())
        conversionDao = database.conversionDao()

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

    private fun refreshRatesIfStale(
        force: Boolean,
        hadCachedRates: Boolean,
        showTooSoonMessage: Boolean = false
    ) {
        lifecycleScope.launch {
            val lastUpdated = repository.getLastUpdated(CONVERSION_BASE) ?: 0L
            val now = System.currentTimeMillis()
            val isStale = now - lastUpdated >= REFRESH_INTERVAL_MILLIS
            if (!force && !isStale) {
                if (showTooSoonMessage) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.refresh_too_soon),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val fetchedRates = runCatching { repository.fetchRates(API_BASE) }.getOrNull()
            val normalizedRates = fetchedRates?.rates?.let { rates ->
                convertRatesToBase(CONVERSION_BASE, API_BASE, rates)
            }
            if (!normalizedRates.isNullOrEmpty()) {
                val updatedAtMillis = fetchedRates.updatedAtSeconds * 1000
                repository.replaceRates(CONVERSION_BASE, normalizedRates, updatedAtMillis)
                updateRates(normalizedRates)
                scheduleConversion()
            } else if (!hadCachedRates) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.error_refresh_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            updateSettingsInfo()
        }
    }

    private fun updateRates(rates: Map<String, Double>) {
        if (rates.isEmpty()) {
            return
        }
        convertRateUSDT.clear()
        convertRateUSDT.putAll(rates)
    }

    private fun spinnerSetup() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.currencies,
            android.R.layout.simple_spinner_item
        ).also { itemAdapter ->
            itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinnerFrom.adapter = adapter
        spinnerTo.adapter = adapter

        setSpinnerSelection(spinnerFrom, baseCurrency)
        setSpinnerSelection(spinnerTo, convertedToCurrency)
        updateCurrencyNames()

        spinnerFrom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op.
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                baseCurrency = parent?.getItemAtPosition(position).toString()
                updateCurrencyNames()
                scheduleConversion()
            }
        }

        spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op.
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                convertedToCurrency = parent?.getItemAtPosition(position).toString()
                updateCurrencyNames()
                scheduleConversion()
            }
        }

        swapButton.setOnClickListener {
            val temp = baseCurrency
            baseCurrency = convertedToCurrency
            convertedToCurrency = temp
            setSpinnerSelection(spinnerFrom, baseCurrency)
            setSpinnerSelection(spinnerTo, convertedToCurrency)
            updateCurrencyNames()
            scheduleConversion()
        }
    }

    private fun updateCurrencyNames() {
        fromNameView.text = currencyNames[baseCurrency] ?: baseCurrency
        toNameView.text = currencyNames[convertedToCurrency] ?: convertedToCurrency
    }

    private fun setupKeypad() {
        val mapping = listOf(
            R.id.key_1 to "1",
            R.id.key_2 to "2",
            R.id.key_3 to "3",
            R.id.key_4 to "4",
            R.id.key_5 to "5",
            R.id.key_6 to "6",
            R.id.key_7 to "7",
            R.id.key_8 to "8",
            R.id.key_9 to "9",
            R.id.key_0 to "0",
            R.id.key_dot to "."
        )

        for ((id, value) in mapping) {
            findViewById<Button>(id).setOnClickListener {
                appendInput(value)
            }
        }

        findViewById<Button>(R.id.key_clear).setOnClickListener {
            clearInput()
        }
    }

    private fun appendInput(value: String) {
        if (value == "." && inputValue.contains(".")) {
            return
        }
        inputValue = if (inputValue.isEmpty()) {
            if (value == ".") "0." else value
        } else if (inputValue == "0" && value != ".") {
            value
        } else {
            inputValue + value
        }
        updateInputDisplay()
        scheduleConversion()
    }

    private fun clearInput() {
        inputValue = ""
        updateInputDisplay()
        scheduleConversion()
    }

    private fun updateInputDisplay() {
        inputAmountView.text = if (inputValue.isEmpty()) "0" else inputValue
    }

    private fun saveConversionIfNew(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        result: Double
    ) {
        if (amount <= 0.0) {
            return
        }
        val signature = String.format(
            Locale.getDefault(),
            "%.4f-%s-%.4f-%s",
            amount,
            fromCurrency,
            result,
            toCurrency
        )
        if (signature == lastSavedSignature) {
            return
        }
        lastSavedSignature = signature
        lifecycleScope.launch {
            conversionDao.insert(
                ConversionEntity(
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency,
                    amount = amount,
                    result = result,
                    createdAt = System.currentTimeMillis()
                )
            )
            if (screenHistory.visibility == View.VISIBLE) {
                updateHistory()
            }
        }
    }

    private fun setupTabs() {
        tabConvert.setOnClickListener { showScreen(Screen.CONVERT) }
        tabHistory.setOnClickListener { showScreen(Screen.HISTORY) }
        tabSettings.setOnClickListener { showScreen(Screen.SETTINGS) }
        showScreen(Screen.CONVERT)
    }

    private fun showScreen(screen: Screen) {
        screenConvert.visibility = if (screen == Screen.CONVERT) View.VISIBLE else View.GONE
        screenHistory.visibility = if (screen == Screen.HISTORY) View.VISIBLE else View.GONE
        screenSettings.visibility = if (screen == Screen.SETTINGS) View.VISIBLE else View.GONE

        if (screen == Screen.HISTORY) {
            updateHistory()
        } else if (screen == Screen.SETTINGS) {
            updateSettingsInfo()
        }
    }

    private fun updateHistory() {
        lifecycleScope.launch {
            val items = conversionDao.getLatest(HISTORY_LIMIT)
            historyList.removeAllViews()
            if (items.isEmpty()) {
                val emptyView = TextView(this@MainActivity).apply {
                    text = getString(R.string.history_empty)
                }
                historyList.addView(emptyView)
                return@launch
            }

            val inflater = LayoutInflater.from(this@MainActivity)
            for (item in items) {
                val row = inflater.inflate(R.layout.item_history, historyList, false)
                val left = row.findViewById<TextView>(R.id.history_left)
                val right = row.findViewById<TextView>(R.id.history_right)

                left.text = String.format(
                    Locale.getDefault(),
                    "%s %.2f",
                    item.fromCurrency,
                    item.amount
                )
                right.text = String.format(
                    Locale.getDefault(),
                    "%s %.2f",
                    item.toCurrency,
                    item.result
                )

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = dpToPx(10)
                row.layoutParams = params
                historyList.addView(row)
            }
        }
    }

    private fun updateSettingsInfo() {
        lifecycleScope.launch {
            val lastUpdated = repository.getLastUpdated(CONVERSION_BASE) ?: 0L
            val lastUpdatedText = if (lastUpdated == 0L) {
                getString(R.string.settings_last_update_never)
            } else {
                formatTimestamp(lastUpdated)
            }
            settingsText.text = getString(R.string.settings_last_update, lastUpdatedText) +
                "\n" + getString(R.string.settings_refresh_interval)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
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

    private fun formatAmount(value: Double): String {
        return String.format(Locale.getDefault(), "%.2f", value)
    }

    private enum class Screen { CONVERT, HISTORY, SETTINGS }

    companion object {
        private const val API_BASE = "USD"
        private const val CONVERSION_BASE = "USDT"
        private const val REFRESH_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
        private const val HISTORY_LIMIT = 20
        private const val INPUT_DEBOUNCE_MILLIS = 500L
    }
}
