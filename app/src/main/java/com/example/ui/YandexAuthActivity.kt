package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.data.YandexDiskManager
import com.yandex.authsdk.YandexAuthException
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthToken

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
                        Log.d("YandexAuth", "Token received successfully")
                        YandexDiskManager.saveToken(this, result.token.value)
                        Toast.makeText(this, "Успешная авторизация в Яндекс Диске", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                    }
                    is YandexAuthResult.Failure -> {
                        Log.e("YandexAuth", "Auth exception", result.exception)
                        Toast.makeText(this, "Ошибка авторизации: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    is YandexAuthResult.Cancelled -> {
                        Log.d("YandexAuth", "Auth canceled")
                        Toast.makeText(this, "Авторизация отменена", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
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
