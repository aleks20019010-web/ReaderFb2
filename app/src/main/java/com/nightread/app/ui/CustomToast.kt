package com.nightread.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.nightread.app.R

object CustomToast {
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        Handler(Looper.getMainLooper()).post {
            try {
                val inflater = LayoutInflater.from(context)
                val layout = inflater.inflate(R.layout.custom_toast, null)
                val textView = layout.findViewById<TextView>(R.id.toastText)
                textView.text = message
                
                val toast = Toast(context.applicationContext).apply {
                    this.duration = duration
                    view = layout
                }
                toast.show()
            } catch (e: Exception) {
                try {
                    Toast.makeText(context.applicationContext, message, duration).show()
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }
}
