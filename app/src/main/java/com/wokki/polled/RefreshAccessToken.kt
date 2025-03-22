package com.wokki.polled

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RefreshAccessToken(private val context: Context) {

    private val clientId = BuildConfig.CLIENT_ID
    private val clientSecret = BuildConfig.CLIENT_SECRET
    private val apiUrl = "https://wokki20.nl/polled/api/v1/authorization_code.php"

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    suspend fun refreshTokenIfNeeded() {
        println("function called")
        println("Checking token expiration...") // This should always print

        val isAccessExpired = isTokenExpired("access_token_expires_in")
        val isRefreshExpired = isTokenExpired("refresh_token_expires_in")
        println("Access token expired? $isAccessExpired, And refresh token expired? $isRefreshExpired")

        if (isAccessExpired && !isRefreshExpired) {
            refreshAccessToken()
        } else if (!isAccessExpired) {
            println("Access token is still valid")
        }
    }

    private fun isTokenExpired(tokenKey: String): Boolean {
        val expiresAtString = sharedPreferences.getString(tokenKey, null)

        println("$tokenKey stored value: $expiresAtString") // Check what's stored

        if (expiresAtString == null) {
            println("$tokenKey is NULL, assuming expired.")
            return true
        }

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("Europe/Amsterdam")

        return try {
            val expiresAt = formatter.parse(expiresAtString)
            val now = Calendar.getInstance(TimeZone.getTimeZone("Europe/Amsterdam")).time

            if (expiresAt == null) {
                println("$tokenKey parsing failed, assuming expired.")
                return true
            }

            println("Now: ${formatter.format(now)} | Token Expiration: ${formatter.format(expiresAt)}")

            val expired = now.after(expiresAt)
            println("$tokenKey expired? $expired")

            expired
        } catch (e: Exception) {
            e.printStackTrace()
            println("$tokenKey encountered an error, assuming expired.")
            true // If parsing fails, assume expired
        }
    }




    private suspend fun refreshAccessToken() {
        val refreshToken = sharedPreferences.getString("refresh_token", null) ?: return

        val params = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to clientId,
            "client_secret" to clientSecret
        )


        val response = postRequest(apiUrl, params)
        response?.let { jsonResponse ->
            val accessToken = jsonResponse.optString("access_token", "")
            val newRefreshToken = jsonResponse.optString("refresh_token", refreshToken)
            val accessTokenExpiresInString = jsonResponse.optString("access_token_expires_in", "")
            val accessTokenExpiresIn = parseDateToMillis(accessTokenExpiresInString)

            val refreshTokenExpiresInString = jsonResponse.optString("refresh_token_expires_in", "")
            val refreshTokenExpiresIn = parseDateToMillis(refreshTokenExpiresInString)

            with(sharedPreferences.edit()) {
                putString("access_token", accessToken)
                putString("refresh_token", newRefreshToken)
                putString("access_token_expires_in", accessTokenExpiresIn.toString())
                putString("refresh_token_expires_in", refreshTokenExpiresIn.toString())
                apply()
            }
        }
    }

    private suspend fun postRequest(url: String, params: Map<String, String>): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = params.entries.joinToString("&") { "${it.key}=${it.value}" }
                connection.outputStream.use { it.write(postData.toByteArray()) }


                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun parseDateToMillis(dateString: String): Long {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            formatter.timeZone = TimeZone.getTimeZone("Europe/Amsterdam")
            val date = formatter.parse(dateString)
            date?.time ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L // Fallback als parsing mislukt
        }
    }


}