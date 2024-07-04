package com.example.currencyconvert

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    @GET("2de0e6051acdd2056860cc8b/latest/{fromCurrency}")
    fun getSupportedCurrencies(
        @Path("fromCurrency") fromCurrency: String
    ): Call<SupportedCurrenciesResponse>
}

