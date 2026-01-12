package com.currencyconverter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ExchangeRateService {
    private const val BASE_URL = "https://open.er-api.com/v6/latest/"

    data class RatesResponse(
        val rates: Map<String, Double>,
        val updatedAtSeconds: Long
    )

    suspend fun fetchRates(base: String): RatesResponse = withContext(Dispatchers.IO) {
        val url = URL(BASE_URL + URLEncoder.encode(base, "UTF-8"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Unexpected response code: $responseCode")
            }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            val resultStatus = json.optString("result", "")
            if (resultStatus != "success") {
                val errorType = json.optString("error-type", "unknown_error")
                throw IOException("Exchange rate error: $errorType")
            }
            val ratesJson = json.getJSONObject("rates")
            val result = mutableMapOf<String, Double>()
            val keys = ratesJson.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                result[code] = ratesJson.getDouble(code)
            }
            val updatedAt = json.optLong("time_last_update_unix", 0L)
            RatesResponse(result, updatedAt)
        } finally {
            connection.disconnect()
        }
    }
}
