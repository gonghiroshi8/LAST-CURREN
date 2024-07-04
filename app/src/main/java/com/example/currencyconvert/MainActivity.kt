package com.example.currencyconvert

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.awaitResponse
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "currency_prefs"
    private val KEY_CURRENCIES = "currencies"
    private val KEY_EXCHANGE_RATES = "exchange_rates"
    private val KEY_LAST_UPDATED = "last_updated"
    private lateinit var amountEditText: EditText
    private lateinit var fromCurrencySpinner: Spinner
    private lateinit var toCurrencySpinner: Spinner
    private lateinit var convertButton: Button
    private lateinit var ocrButton: Button
    private lateinit var resultTextView: TextView
    private lateinit var swapButton: Button

    private val api: ExchangeRateApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://v6.exchangerate-api.com/v6/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        amountEditText = findViewById(R.id.amountEditText)
        fromCurrencySpinner = findViewById(R.id.fromCurrencySpinner)
        toCurrencySpinner = findViewById(R.id.toCurrencySpinner)
        convertButton = findViewById(R.id.convertButton)
        ocrButton = findViewById(R.id.ocrButton)
        resultTextView = findViewById(R.id.resultTextView)
        swapButton = findViewById(R.id.swapButton)

        fetchAndSetupCurrencies()

        convertButton.setOnClickListener {
            convertCurrency()
        }

        ocrButton.setOnClickListener {
            val intent = Intent(this, OcrActivity::class.java)
            startActivity(intent)
        }

        swapButton.setOnClickListener {
            swapCurrencies()
        }
    }

    private fun fetchAndSetupCurrencies() {
        val cachedCurrencies = sharedPreferences.getStringSet(KEY_CURRENCIES, null)
        val lastUpdated = sharedPreferences.getLong(KEY_LAST_UPDATED, 0)
        val currentTime = System.currentTimeMillis()

        if (cachedCurrencies != null && currentTime - lastUpdated < 24 * 60 * 60 * 1000) {
            Log.d("MainActivity", "Using cached currencies")
            setupCurrencySpinners(cachedCurrencies.toList())
        } else {
            Log.d("MainActivity", "Fetching currencies from API")
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        api.getSupportedCurrencies("USD").awaitResponse()
                    }

                    if (response.isSuccessful) {
                        val supportedCurrencies = response.body()?.conversion_rates?.keys?.toList()
                        if (supportedCurrencies != null) {
                            saveCurrenciesToCache(supportedCurrencies)
                            setupCurrencySpinners(supportedCurrencies)
                        } else {
                            resultTextView.text = "Failed to load currencies"
                        }
                    } else {
                        resultTextView.text = "API Error: ${response.message()} (${response.code()})"
                    }
                } catch (e: Exception) {
                    resultTextView.text = "API Call Failure: ${e.message}"
                }
            }
        }
    }

    private fun saveCurrenciesToCache(currencies: List<String>) {
        val editor = sharedPreferences.edit()
        editor.putStringSet(KEY_CURRENCIES, currencies.toSet())
        editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
        editor.apply()
    }

    private fun saveExchangeRatesToCache(rates: Map<String, Double>) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_EXCHANGE_RATES, Gson().toJson(rates))
        editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
        editor.apply()
    }

    private fun updateExchangeRateUI(rate: Double, currencyFrom: String, currencyTo: String, amount: Double) {
        val convertedAmount = amount * rate
        runOnUiThread {
            resultTextView.text =
                String.format("%,.2f %s = %,.2f %s", amount, currencyFrom, convertedAmount, currencyTo)
        }
    }

    private fun handleExchangeRateFailure() {
        runOnUiThread {
            resultTextView.text = "ไม่สามารถดึงอัตราแลกเปลี่ยนได้ กรุณาลองใหม่ในภายหลัง"
        }
    }

    private fun setupCurrencySpinners(currencies: List<String>) {
        val sortedCurrencies = currencies.sorted()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortedCurrencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fromCurrencySpinner.adapter = adapter
        toCurrencySpinner.adapter = adapter
    }

    private fun convertCurrency() {
        val amountString = amountEditText.text.toString()
        if (amountString.isEmpty()) {
            resultTextView.text = "Please enter an amount"
            return
        }

        try {
            val amount = amountString.toDouble()
            val fromCurrency = fromCurrencySpinner.selectedItem?.toString()
            val toCurrency = toCurrencySpinner.selectedItem?.toString()

            if (fromCurrency == null || toCurrency == null) {
                resultTextView.text = "Please select both currencies"
                return
            }

            fetchExchangeRates(fromCurrency, toCurrency, amount)
        } catch (e: NumberFormatException) {
            resultTextView.text = "Invalid amount"
        }
    }

    private fun fetchExchangeRates(currencyFrom: String, currencyTo: String, amount: Double) {
        val cachedRates = sharedPreferences.getString(KEY_EXCHANGE_RATES, null)
        val lastUpdated = sharedPreferences.getLong(KEY_LAST_UPDATED, 0)
        val currentTime = System.currentTimeMillis()

        if (cachedRates != null && currentTime - lastUpdated < 24 * 60 * 60 * 1000) {
            Log.d("CurrencyCache", "Using cached exchange rates")
            val rates: Map<String, Double> = Gson().fromJson(cachedRates, object : TypeToken<Map<String, Double>>() {}.type)
            val fromRate = rates[currencyFrom] ?: 1.0
            val toRate = rates[currencyTo] ?: 1.0
            val rate = toRate / fromRate
            Log.d("CurrencyCache", "Exchange rate from $currencyFrom to $currencyTo: $rate")
            updateExchangeRateUI(rate, currencyFrom, currencyTo, amount)
        } else {
            Log.d("CurrencyAPI", "Fetching exchange rates from API")
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        api.getSupportedCurrencies(currencyFrom).awaitResponse()
                    }

                    if (response.isSuccessful) {
                        val rates = response.body()?.conversion_rates
                        if (rates != null) {
                            val fromRate = rates[currencyFrom] ?: 1.0
                            val toRate = rates[currencyTo] ?: 1.0
                            val rate = toRate / fromRate
                            Log.d("CurrencyAPI", "Exchange rate from $currencyFrom to $currencyTo: $rate")
                            saveExchangeRatesToCache(rates)
                            updateExchangeRateUI(rate, currencyFrom, currencyTo, amount)
                        } else {
                            resultTextView.text = "Failed to load exchange rates"
                        }
                    } else {
                        resultTextView.text = "API Error: ${response.message()} (${response.code()})"
                    }
                } catch (e: Exception) {
                    resultTextView.text = "API Call Failure: ${e.message}"
                }
            }
        }
    }



    private fun swapCurrencies() {
        val fromPosition = fromCurrencySpinner.selectedItemPosition
        val toPosition = toCurrencySpinner.selectedItemPosition
        fromCurrencySpinner.setSelection(toPosition)
        toCurrencySpinner.setSelection(fromPosition)
    }
}
