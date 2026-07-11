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

class YandexAuthActivity : BaseActivity() {

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
                        CustomToast.show(this, "Проверка подключения к Яндекс Диску...")
                        
                        lifecycleScope.launch {
                            val success = YandexDiskManager.connect(this@YandexAuthActivity, tokenValue)
                            if (success) {
                                CustomToast.show(this@YandexAuthActivity, "Успешная авторизация в Яндекс Диске")
                                setResult(RESULT_OK)
                            } else {
                                Log.e("AUTH_ERROR", "Yandex Disk verification check failed. Token was invalid or network error.")
                                CustomToast.show(this@YandexAuthActivity, "Ошибка авторизации: недействительный токен или нет сети")
                                setResult(RESULT_CANCELED)
                            }
                            finish()
                        }
                    }
                    is YandexAuthResult.Failure -> {
                        Log.e("AUTH_ERROR", "Auth SDK exception", result.exception)
                        CustomToast.show(this, "Ошибка авторизации: ${result.exception.message}")
                        finish()
                    }
                    is YandexAuthResult.Cancelled -> {
                        Log.d("AUTH_ERROR", "Auth canceled by user")
                        CustomToast.show(this, "Авторизация отменена")
                        finish()
                    }
                }
            }
            
            val loginOptions = YandexAuthLoginOptions()
            launcher.launch(loginOptions)
        } catch (e: Exception) {
            Log.e("YandexAuthActivity", "Error initializing Yandex Auth SDK", e)
            CustomToast.show(this, "Ошибка авторизации Яндекса")
            finish()
        }
    }
}
