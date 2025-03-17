package com.wokki.polled

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class LoginRedirectActivity : AppCompatActivity() {

    private val clientId = BuildConfig.CLIENT_ID
    private val clientSecret = BuildConfig.CLIENT_SECRET
    private val redirect_uri = BuildConfig.API_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        val uri: Uri? = intent?.data
        val code = uri?.getQueryParameter("code")
        val page = uri?.getQueryParameter("page")

        if (code != null) {
            // If the URI contains a code, exchange it for a token
            exchangeCodeForToken(code)
        } else {
            // If no code is found, redirect to the MainActivity
            Log.e("LoginRedirect", "No code found in URI, redirecting to MainActivity")

            val mainIntent = Intent(this, MainActivity::class.java).apply {
                if (page != null) {
                    putExtra("page", page)  // Pass the page to MainActivity
                }
            }
            startActivity(mainIntent)
            finish()  // Close the current activity
        }
    }






    private fun exchangeCodeForToken(code: String) {
        val client = OkHttpClient()

        val requestBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirect_uri)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .build()

        val request = Request.Builder()
            .url("https://levgames.nl/polled/api/v1/authorization_code")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LoginRedirect", "Request failed: ${e.message}")
                finish()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("LoginRedirect", "HTTP error: ${response.code}")
                        finish()
                        return
                    }

                    val responseData = response.body?.string()
                    if (responseData != null) {
                        val json = JSONObject(responseData)
                        if (json.has("error")) {
                            Log.e("LoginRedirect", "Error: ${json.getString("error")}")
                        } else {
                            val accessToken = json.getString("access_token")
                            val refreshToken = json.getString("refresh_token")

                            // Save tokens in SharedPreferences
                            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
                            with(sharedPreferences.edit()) {
                                putString("access_token", accessToken)
                                putString("refresh_token", refreshToken)
                                putString("access_token_expires_in", json.getString("access_token_expires_in"))
                                putString("refresh_token_expires_in", json.getString("refresh_token_expires_in"))
                                putBoolean("is_logged_in", true)
                                apply()
                            }

                            Log.d("LoginRedirect", "Login successful!")

                            // Redirect user back to the main activity
                            val intent = Intent(this@LoginRedirectActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                    }
                    finish()
                }
            }
        })
    }
}
