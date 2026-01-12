package com.currencyconverter

class RatesRepository(private val dao: CurrencyRateDao) {
    suspend fun getCachedRates(base: String): List<CurrencyRateEntity> {
        return dao.getAllRates(base)
    }

    suspend fun getLastUpdated(base: String): Long? {
        return dao.getLastUpdated(base)
    }

    suspend fun replaceRates(base: String, rates: Map<String, Double>, updatedAt: Long) {
        val entities = rates.map { (code, rate) ->
            CurrencyRateEntity(code = code, rate = rate, base = base, updatedAt = updatedAt)
        }
        dao.clearBase(base)
        dao.insertAll(entities)
    }

    suspend fun fetchRates(base: String): ExchangeRateService.RatesResponse {
        return ExchangeRateService.fetchRates(base)
    }
}
