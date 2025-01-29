package io.bambosan.mbloader

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class Fallback : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fallback)
        findViewById<TextView>(R.id.logOut).text = intent.getStringExtra("LOG_STR")
    }
}