package com.wokki.polled

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wokki.polled.databinding.ActivityCreateAccountBinding

class CreateAccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateAccountBinding
    private lateinit var recaptchaWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the action bar
        supportActionBar?.hide()

        // Initialize view binding
        binding = ActivityCreateAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize WebView
        recaptchaWebView = findViewById(R.id.recaptchaWebView)

        // Set up the WebView to display the full HTML page
        recaptchaWebView.settings.javaScriptEnabled = true
        recaptchaWebView.webViewClient = object : WebViewClient() {
            // Optionally, handle any redirects here if needed
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // You can handle specific URLs here if needed, or let WebView handle the loading
                return super.shouldOverrideUrlLoading(view, url)
            }
        }

        // Load the page from the URL
        recaptchaWebView.loadUrl("https://polled.levgames.nl/createApp")

        // Periodically check if 'created' is true
        startCheckingForAccountCreation()
    }

    private fun startCheckingForAccountCreation() {
        // Use a handler to periodically check the 'created' variable on the webpage
        val handler = android.os.Handler(mainLooper)
        val checkInterval: Long = 1000 // Check every second

        val runnable = object : Runnable {
            override fun run() {
                // Execute JavaScript code to check if 'created' is true
                recaptchaWebView.evaluateJavascript("javascript:window.created") { value ->
                    if (value == "true") {
                        // If 'created' is true, do something
                        // Show a success message
                        Toast.makeText(this@CreateAccountActivity, "Account created!", Toast.LENGTH_SHORT).show()

                        // Optionally, stop further checks by removing the runnable
                        handler.removeCallbacks(this)

                        // Delay the redirection to allow the user to see the success message
                        handler.postDelayed({
                            // Redirect to the login website
                            val loginIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://polled.wokki20.nl/loginApp"))
                            startActivity(loginIntent)
                            finish()  // Finish the current activity to prevent going back to the creation page
                        }, 1000)  // Delay the redirection by 2 seconds (or adjust as needed)
                    } else {
                        // If not created, check again after the specified interval
                        handler.postDelayed(this, checkInterval)
                    }
                }
            }
        }

        // Start the periodic check
        handler.post(runnable)
    }

}
