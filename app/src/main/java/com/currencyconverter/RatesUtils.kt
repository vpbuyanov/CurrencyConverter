package com.currencyconverter

internal fun convertRatesToBase(
    targetBase: String,
    sourceBase: String,
    rates: Map<String, Double>
): Map<String, Double>? {
    if (targetBase == sourceBase) {
        return rates.toMap()
    }
    val targetRate = rates[targetBase] ?: return null
    val normalized = mutableMapOf<String, Double>()
    for ((code, rate) in rates) {
        normalized[code] = rate / targetRate
    }
    normalized[targetBase] = 1.0
    return normalized
}

internal fun convertAmount(
    amount: Double,
    fromCurrency: String,
    toCurrency: String,
    rates: Map<String, Double>
): Double? {
    val fromRate = rates[fromCurrency] ?: return null
    val toRate = rates[toCurrency] ?: return null
    val amountInBase = amount / fromRate
    return amountInBase * toRate
}
