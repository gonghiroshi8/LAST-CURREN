package com.example.currencyconvert

import com.google.android.material.textfield.MaterialAutoCompleteTextView
import android.widget.ArrayAdapter
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.currencyconvert.camera.FocusOverlayView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.text.DecimalFormat
class OcrActivity : AppCompatActivity() {
    private lateinit var textureView: PreviewView
    private lateinit var ocrResult: TextView
    private lateinit var spinnerCurrencyFrom: Spinner
    private lateinit var spinnerCurrencyTo: Spinner
    private lateinit var btnPauseResume: Button
    private lateinit var btnSwap: Button
    private lateinit var manualButton: Button
    private lateinit var Mainconvert: Button

    private var isPaused = false
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var focusOverlayView: FocusOverlayView
    private var imageAnalysis: ImageAnalysis? = null
    private var ocrNumber: Double = 0.0
    private lateinit var fromCurrencySpinner: MaterialAutoCompleteTextView
    private lateinit var toCurrencySpinner: MaterialAutoCompleteTextView
    private var selectedCurrencyFrom: String = "USD"
    private var selectedCurrencyTo: String = "USD"
    private val KEY_EXCHANGE_RATES = "exchange_rates"
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "currency_prefs"
    private val KEY_CURRENCIES = "currencies"
    private val KEY_LAST_UPDATED = "last_updated"

    private val api: ExchangeRateApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://v6.exchangerate-api.com/v6/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().build())  // ใช้ OkHttpClient ที่ถูกต้อง
            .build()
            .create(ExchangeRateApi::class.java)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fromCurrencySpinner = findViewById(R.id.fromCurrencySpinner)
        toCurrencySpinner = findViewById(R.id.toCurrencySpinner)
        textureView = findViewById(R.id.textureView)
        ocrResult = findViewById(R.id.ocrResult)
        focusOverlayView = findViewById(R.id.focusOverlay)
        btnPauseResume = findViewById(R.id.btnPauseResume)
        btnSwap = findViewById(R.id.btnSwap)
        manualButton = findViewById(R.id.manualButton)
        Mainconvert = findViewById(R.id.Mainconvert)
        fetchAndSetupCurrencies()
        loadSelectedCurrenciesFromPrefs()
        manualButton.setOnClickListener {
            val intent = Intent(this, ManualActivity::class.java)
            startActivity(intent)
        }
        Mainconvert.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        btnSwap.setOnClickListener {
            swapCurrencies()
        }



        fromCurrencySpinner.setOnItemClickListener { _, _, position, _ ->
            selectedCurrencyFrom = fromCurrencySpinner.adapter.getItem(position) as String
            fetchExchangeRates(selectedCurrencyFrom, selectedCurrencyTo)
            saveSelectedCurrenciesToPrefs()
        }

        toCurrencySpinner.setOnItemClickListener { _, _, position, _ ->
            selectedCurrencyTo = toCurrencySpinner.adapter.getItem(position) as String
            fetchExchangeRates(selectedCurrencyFrom, selectedCurrencyTo)
            saveSelectedCurrenciesToPrefs()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnPauseResume.setOnClickListener {
            if (isPaused) {
                resumeCamera()
            } else {
                pauseCamera()
            }
        }

        btnSwap.setOnClickListener {
            swapCurrencies()
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            startCameraX()
        }
    }

    private fun startCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(textureView.surfaceProvider)
            }

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (!isPaused) {
                            processImageProxy(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                Log.d("OcrActivity", "Camera is bound to lifecycle.")
            } catch (exc: Exception) {
                Log.e("OcrActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun pauseCamera() {
        isPaused = true
        btnPauseResume.text = "Resume"
    }

    private fun resumeCamera() {
        isPaused = false
        btnPauseResume.text = "Pause"
    }


    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val bitmap = imageProxy.toBitmap()

                    // กำหนดพื้นที่ของ bitmap ที่จะเน้นให้ตรงกับการคำนวณใน onDraw
                    val rectHeight = bitmap.height / 7f
                    val top = (bitmap.height - rectHeight) / 2f + (bitmap.height * 0.17f)
                    val bottom = top + rectHeight

                    // กำหนดขอบซ้ายและขอบขวาของพื้นที่โฟกัสให้ตรงกับ onDraw
                    val leftMargin = bitmap.width * 0.22f
                    val rightMargin = bitmap.width * 0.457f

                    val focusArea = Rect(
                        leftMargin.toInt(),
                        top.toInt(),
                        (bitmap.width - rightMargin).toInt(),
                        bottom.toInt()
                    )

                    // กรองข้อความในพื้นที่เน้นและดึงเฉพาะตัวเลข
                    val filteredText = filterTextInFocusArea(visionText, focusArea)
                    val numbersOnly = filterNumbers(filteredText)
                    Log.d("OcrActivity", "เลขที่ตรวจจับได้ : $numbersOnly")

                    // อัปเดต UI
                    runOnUiThread {
                        if (!isPaused && numbersOnly.isNotEmpty()) {
                            // ตัวอย่างการใช้งานตัวเลขแรกที่ตรวจพบ
                            ocrNumber = numbersOnly[0].toDoubleOrNull() ?: 0.0
                            fetchExchangeRates(selectedCurrencyFrom, selectedCurrencyTo)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OcrActivity", "อ่านเลขไม่ออก", e)
                }
                .addOnCompleteListener {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        imageProxy.close()
                    }
                }
        } else {
            imageProxy.close()
        }
    }


    private fun setupCurrencyAdapters(currencies: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        fromCurrencySpinner.setAdapter(adapter)
        toCurrencySpinner.setAdapter(adapter)
        fromCurrencySpinner.setText("", false)
        toCurrencySpinner.setText("", false)
        fromCurrencySpinner.threshold = 0 // Minimum characters to start the filtering process
        toCurrencySpinner.threshold = 0 // Minimum characters to start the filtering process
    }

    private fun filterTextInFocusArea(
        visionText: com.google.mlkit.vision.text.Text,
        focusArea: Rect
    ): String {
        val stringBuilder = StringBuilder()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val boundingBox = line.boundingBox
                if (boundingBox != null && isInsideFocusArea(focusArea, boundingBox)) {
                    stringBuilder.append(line.text).append("\n")
                }
            }
        }

        return stringBuilder.toString()
    }

    private fun isInsideFocusArea(focusArea: Rect, boundingBox: Rect): Boolean {
        val centerX = (boundingBox.left + boundingBox.right) / 2
        val centerY = (boundingBox.top + boundingBox.bottom) / 2
        return focusArea.contains(centerX, centerY) || focusArea.intersect(boundingBox)
    }


    private fun filterNumbers(text: String): List<String> {
        // แยกคำจากข้อความด้วยช่องว่างหรืออักขระที่ไม่ใช่ตัวเลขและจุดทศนิยม
        val words = text.split(Regex("\\s+"))

        val result = mutableListOf<String>()
        for (word in words) {
            // ลบอักขระที่ไม่ใช่ตัวเลขและจุดทศนิยมออก
            val cleanedWord = word.replace(Regex("[^0-9.]"), "")
            // ตรวจสอบว่าคำที่ถูกลบอักขระแล้วเป็นตัวเลขที่ถูกต้องหรือไม่
            if (cleanedWord.matches(Regex("^\\d+(\\.\\d+)?$")) && cleanedWord.length <= 6 && !cleanedWord.matches(Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}"))) {
                result.add(cleanedWord)
            }
        }
        return result
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraX()
        } else {
            Log.e("OcrActivity", "กล้องอนุญาตไม่สำเร็จ")
        }
    }

    private fun fetchAndSetupCurrencies() {
        val cachedCurrencies = sharedPreferences.getStringSet(KEY_CURRENCIES, null)
        val lastUpdated = sharedPreferences.getLong(KEY_LAST_UPDATED, 0)
        val currentTime = System.currentTimeMillis()

        if (cachedCurrencies != null && currentTime - lastUpdated < 24 * 60 * 60 * 1000) {
            Log.d("OcrActivity", "ใช้ข้อมูลในแคช")
            setupCurrencyAdapters(cachedCurrencies.toList())

        } else {
            Log.d("OcrActivity", "ดึงค่าเงินจาก WEB")
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        api.getSupportedCurrencies("USD").awaitResponse()
                    }

                    if (response.isSuccessful) {
                        val supportedCurrencies = response.body()?.conversion_rates
                        if (supportedCurrencies != null) {
                            saveSupportedCurrenciesToCache(supportedCurrencies.keys)
                            setupCurrencyAdapters(supportedCurrencies.keys.toList())
                        } else {
                            ocrResult.text = "Failed to load currencies"
                        }
                    } else {
                        ocrResult.text = "API Error: ${response.message()} (${response.code()})"
                    }
                } catch (e: Exception) {
                    ocrResult.text = "API Call Failure: ${e.message}"
                }
            }
        }
    }
    private fun saveSupportedCurrenciesToCache(currencies: Set<String>) {
        val editor = sharedPreferences.edit()
        editor.putStringSet(KEY_CURRENCIES, currencies)
        editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
        editor.apply()
    }

    private fun updateExchangeRateUI(rate: Double, currencyFrom: String, currencyTo: String) {
        val convertedAmount = ocrNumber * rate
        val decimalFormat = DecimalFormat("#,###.##")

        val formattedOcrNumber = decimalFormat.format(ocrNumber)
        val formattedConvertedAmount = decimalFormat.format(convertedAmount)

        runOnUiThread {
            ocrResult.text = "$formattedOcrNumber $currencyFrom = $formattedConvertedAmount $currencyTo"
        }
    }
    private fun saveExchangeRatesToCache(rates: Map<String, Double>) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_EXCHANGE_RATES, Gson().toJson(rates))
        editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
        editor.apply()
    }

    // ตี่งค่า spin
    private fun setupCurrencySpinners(currencies: Map<String, Double>) {
        val sortedCurrencies = currencies.keys.sorted()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortedCurrencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrencyFrom.adapter = adapter
        spinnerCurrencyTo.adapter = adapter
    }
    // แปลงค่าเงิน
    private fun fetchExchangeRates(currencyFrom: String, currencyTo: String) {
        val cachedRatesJson = sharedPreferences.getString(KEY_EXCHANGE_RATES, null)
        val lastUpdated = sharedPreferences.getLong(KEY_LAST_UPDATED, 0)
        val currentTime = System.currentTimeMillis()

        if (cachedRatesJson != null && currentTime - lastUpdated < 24 * 60 * 60 * 1000) {
            Log.d("CurrencyCache", "ใช้ข้อมูลในแคช")
            val cachedRates: Map<String, Double> = Gson().fromJson(cachedRatesJson, object : TypeToken<Map<String, Double>>() {}.type)
            val fromRate = cachedRates[currencyFrom] ?: 1.0
            val toRate = cachedRates[currencyTo] ?: 1.0
            val rate = toRate / fromRate
            Log.d("CurrencyCache", "แปลงค่าเงินจาก $currencyFrom เป็น $currencyTo: $rate")
            updateExchangeRateUI(rate, currencyFrom, currencyTo)
        } else {
            Log.d("CurrencyAPI", "ดึงข้อมูลจาก WEB")
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    val responseFrom = withContext(Dispatchers.IO) {
                        api.getSupportedCurrencies(currencyFrom).awaitResponse()
                    }
                    val responseTo = withContext(Dispatchers.IO) {
                        api.getSupportedCurrencies(currencyTo).awaitResponse()
                    }

                    if (responseFrom.isSuccessful && responseTo.isSuccessful) {
                        val ratesFrom = responseFrom.body()?.conversion_rates
                        val ratesTo = responseTo.body()?.conversion_rates
                        if (ratesFrom != null && ratesTo != null) {
                            val fromRate = ratesFrom[currencyFrom] ?: 1.0
                            val toRate = ratesTo[currencyTo] ?: 1.0
                            val rate = toRate / fromRate
                            Log.d("CurrencyAPI", "Exchange rate from $currencyFrom to $currencyTo: $rate")
                            saveExchangeRatesToCache(ratesFrom + ratesTo)
                            updateExchangeRateUI(rate, currencyFrom, currencyTo)
                        } else {
                            ocrResult.text = "Failed to load exchange rates"
                        }
                    } else {
                        ocrResult.text = "API Error: ${responseFrom.message()} (${responseFrom.code()})"
                    }
                } catch (e: Exception) {
                    ocrResult.text = "API Call Failure: ${e.message}"
                }
            }
        }
    }
    // ทำปุ่มสลับค่าเงิน
    private fun swapCurrencies() {
        val tempCurrencyFrom = selectedCurrencyFrom
        selectedCurrencyFrom = selectedCurrencyTo
        selectedCurrencyTo = tempCurrencyFrom

        // Update the spinners' selected items
        fromCurrencySpinner.setText(selectedCurrencyFrom, false)
        toCurrencySpinner.setText(selectedCurrencyTo, false)

        // Fetch the new exchange rates
        fetchExchangeRates(selectedCurrencyFrom, selectedCurrencyTo)

        // Save the swapped currencies to preferences
        saveSelectedCurrenciesToPrefs()
    }

    private fun saveSelectedCurrenciesToPrefs() {
        val editor = sharedPreferences.edit()
        editor.putString("selectedCurrencyFrom", selectedCurrencyFrom)
        editor.putString("selectedCurrencyTo", selectedCurrencyTo)
        editor.apply()
    }
    private fun loadSelectedCurrenciesFromPrefs() {
        selectedCurrencyFrom = sharedPreferences.getString("selectedCurrencyFrom", "USD") ?: "USD"
        selectedCurrencyTo = sharedPreferences.getString("selectedCurrencyTo", "USD") ?: "USD"

        fromCurrencySpinner.setText(selectedCurrencyFrom, false)
        toCurrencySpinner.setText(selectedCurrencyTo, false)
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


}
