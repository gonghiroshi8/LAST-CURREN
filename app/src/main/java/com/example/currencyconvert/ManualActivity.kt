package com.example.currencyconvert

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ManualActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editTextCurrencyFrom: EditText
    private lateinit var editTextRateTo: EditText
    private lateinit var buttonConvert: Button
    private lateinit var buttonSaveRateTo: Button
    private lateinit var buttonDeleteRateTo: Button
    private lateinit var textViewResult: TextView
    private lateinit var autoCompleteCurrencyTo: AutoCompleteTextView
    private lateinit var spinnerSavedCurrencies: Spinner
    private lateinit var ocrButton: Button
    private lateinit var manualButton: Button
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mannual)

        sharedPreferences = getSharedPreferences("currencyRates", MODE_PRIVATE)

        editTextCurrencyFrom = findViewById(R.id.editTextCurrencyFrom)
        editTextRateTo = findViewById(R.id.editTextRateTo)
        buttonConvert = findViewById(R.id.buttonConvert)
        buttonSaveRateTo = findViewById(R.id.buttonSaveRateTo)
        buttonDeleteRateTo = findViewById(R.id.buttonDeleteRateTo)
        textViewResult = findViewById(R.id.textViewResult)
        autoCompleteCurrencyTo = findViewById(R.id.autoCompleteCurrencyTo)
        spinnerSavedCurrencies = findViewById(R.id.spinnerSavedCurrencies)
        ocrButton = findViewById(R.id.ocrButton)
        manualButton = findViewById(R.id.manualButton)

        // Predefined currencies for AutoCompleteTextView
        val currencies = arrayOf("USD", "EUR", "GBP", "JPY")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        autoCompleteCurrencyTo.setAdapter(adapter)

        // Load saved rates and update the spinner
        loadSavedCurrencies()

        buttonConvert.setOnClickListener {
            val amountFrom = editTextCurrencyFrom.text.toString().toDoubleOrNull()
            val rateTo = editTextRateTo.text.toString().toDoubleOrNull()
            val currencyTo = autoCompleteCurrencyTo.text.toString()

            if (amountFrom != null && rateTo != null) {
                val result = amountFrom * rateTo
                textViewResult.text = "Converted amount to $currencyTo: $result"
            } else {
                textViewResult.text = "Please enter valid numbers"
            }
        }

        buttonSaveRateTo.setOnClickListener {
            val rateTo = editTextRateTo.text.toString().toFloatOrNull()
            val currencyTo = autoCompleteCurrencyTo.text.toString()

            if (rateTo != null && currencyTo.isNotEmpty()) {
                saveCurrencyRate(currencyTo, rateTo)
                loadSavedCurrencies()
                Toast.makeText(this, "Rate and currency saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid currency and rate", Toast.LENGTH_SHORT).show()
            }
        }

        buttonDeleteRateTo.setOnClickListener {
            val currencyTo = spinnerSavedCurrencies.selectedItem.toString()
            deleteCurrencyRate(currencyTo)
            loadSavedCurrencies()
            Toast.makeText(this, "Rate and currency deleted", Toast.LENGTH_SHORT).show()
        }

        // Set onClickListeners for ocrButton and manualButton
        ocrButton.setOnClickListener {
            val intent = Intent(this, OcrActivity::class.java)
            startActivity(intent)
        }

        manualButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        spinnerSavedCurrencies.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCurrency = parent?.getItemAtPosition(position).toString()
                val rate = loadCurrencyRateMap()[selectedCurrency]
                autoCompleteCurrencyTo.setText(selectedCurrency)
                editTextRateTo.setText(rate?.toString())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun saveCurrencyRate(currency: String, rate: Float) {
        val currencyRateMap = loadCurrencyRateMap().toMutableMap()
        currencyRateMap[currency] = rate
        with(sharedPreferences.edit()) {
            putString("currencyRateMap", gson.toJson(currencyRateMap))
            apply()
        }
    }

    private fun loadCurrencyRateMap(): Map<String, Float> {
        val json = sharedPreferences.getString("currencyRateMap", null)
        return if (json != null) {
            val type = object : TypeToken<Map<String, Float>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyMap()
        }
    }

    private fun loadSavedCurrencies() {
        val currencyRateMap = loadCurrencyRateMap()
        val currencies = currencyRateMap.keys.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSavedCurrencies.adapter = adapter
    }

    private fun deleteCurrencyRate(currency: String) {
        val currencyRateMap = loadCurrencyRateMap().toMutableMap()
        currencyRateMap.remove(currency)
        with(sharedPreferences.edit()) {
            putString("currencyRateMap", gson.toJson(currencyRateMap))
            apply()
        }
    }
}
