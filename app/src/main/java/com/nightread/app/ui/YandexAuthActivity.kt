package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nightread.app.data.YandexDiskManager
import com.yandex.authsdk.YandexAuthException
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthToken
import kotlinx.coroutines.launch

class YandexAuthActivity : AppCompatActivity() {

    private lateinit var sdk: YandexAuthSdk

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val options = YandexAuthOptions(this, true)
            sdk = YandexAuthSdk.create(options)
            
            // Register here in onCreate to avoid contract issues if sdk is lazy initialized
            val launcher = registerForActivityResult(sdk.contract) { result ->
                when (result) {
                    is YandexAuthResult.Success -> {
                        val tokenValue = result.token.value
                        Log.d("AUTH_ERROR", "Token received successfully. Initiating validation check.")
                        Toast.makeText(this, "Проверка подключения к Яндекс Диску...", Toast.LENGTH_SHORT).show()
                        
                        lifecycleScope.launch {
                            val success = YandexDiskManager.connect(this@YandexAuthActivity, tokenValue)
                            if (success) {
                                Toast.makeText(this@YandexAuthActivity, "Успешная авторизация в Яндекс Диске", Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                            } else {
                                Log.e("AUTH_ERROR", "Yandex Disk verification check failed. Token was invalid or network error.")
                                Toast.makeText(this@YandexAuthActivity, "Ошибка авторизации: недействительный токен или нет сети", Toast.LENGTH_LONG).show()
                                setResult(RESULT_CANCELED)
                            }
                            finish()
                        }
                    }
                    is YandexAuthResult.Failure -> {
                        Log.e("AUTH_ERROR", "Auth SDK exception", result.exception)
                        Toast.makeText(this, "Ошибка авторизации: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is YandexAuthResult.Cancelled -> {
                        Log.d("AUTH_ERROR", "Auth canceled by user")
                        Toast.makeText(this, "Авторизация отменена", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            
            val loginOptions = YandexAuthLoginOptions()
            launcher.launch(loginOptions)
        } catch (e: Exception) {
            Log.e("YandexAuthActivity", "Error initializing Yandex Auth SDK", e)
            Toast.makeText(this, "Ошибка авторизации Яндекса", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
