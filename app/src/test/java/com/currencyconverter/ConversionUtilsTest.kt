package com.currencyconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConversionUtilsTest {
    @Test
    fun convertRatesToBase_normalizesRatesForTargetBase() {
        val rates = mapOf(
            "USD" to 1.0,
            "USDT" to 1.1,
            "EUR" to 0.9
        )

        val normalized = convertRatesToBase("USDT", "USD", rates)
        assertNotNull(normalized)

        val normalizedRates = normalized!!
        assertEquals(1.0, normalizedRates.getValue("USDT"), 1e-9)
        assertEquals(1.0 / 1.1, normalizedRates.getValue("USD"), 1e-9)
        assertEquals(0.9 / 1.1, normalizedRates.getValue("EUR"), 1e-9)
    }

    @Test
    fun convertAmount_convertsUsingBaseRates() {
        val rates = mapOf(
            "USD" to 1.0,
            "EUR" to 0.9,
            "USDT" to 1.1
        )

        val result = convertAmount(100.0, "USD", "EUR", rates)
        assertNotNull(result)
        assertEquals(90.0, result!!, 1e-9)
    }
}
