package com.nightread.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.nightread.app.R

enum class ToastType {
    INFO, SUCCESS, WARNING, ERROR
}

object AiToast {
    fun show(context: Context, message: String, type: ToastType = ToastType.INFO) {
        Handler(Looper.getMainLooper()).post {
            try {
                val inflater = LayoutInflater.from(context)
                val layout = inflater.inflate(R.layout.ai_toast, null)
                val textView = layout.findViewById<TextView>(R.id.toastText)
                val imageView = layout.findViewById<ImageView>(R.id.toastIcon)
                
                textView.text = message
                
                val iconRes = when (type) {
                    ToastType.INFO -> R.drawable.ic_ai_info
                    ToastType.SUCCESS -> R.drawable.ic_ai_success
                    ToastType.WARNING -> R.drawable.ic_ai_warning
                    ToastType.ERROR -> R.drawable.ic_ai_error
                }
                imageView.setImageResource(iconRes)
                
                val toast = Toast(context.applicationContext).apply {
                    this.duration = Toast.LENGTH_LONG
                    view = layout
                }
                toast.show()
            } catch (e: Exception) {
                try {
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }
}
