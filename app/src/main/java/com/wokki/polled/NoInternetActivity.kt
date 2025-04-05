package com.wokki.polled

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NoInternetActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_internet)
        supportActionBar?.hide()

        // Set up the retry button
        val retryButton: Button = findViewById(R.id.retryButton)
        retryButton.setOnClickListener {
            if (isInternetAvailable()) {
                // If internet is available, go back to the main activity
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // Close this activity
            } else {
                // Show a message if there's still no internet
                Toast.makeText(this, getString(R.string.noInternet), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnected == true
    }
}
