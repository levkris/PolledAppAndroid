package com.wokki.polled.ui.profile

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileViewModel(private val context: Context) : ViewModel() {

    private val _profileData = MutableLiveData<String>()
    val profileData: LiveData<String> = _profileData

    private val _imageUrl = MutableLiveData<String>()
    val imageUrl: LiveData<String> = _imageUrl

    fun fetchProfileData() {
        val client = OkHttpClient()

        // Get access token from SharedPreferences
        val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken.isNullOrEmpty()) {
            _profileData.value = "Error: Access token not available"
            return
        }

        val request = Request.Builder()
            .url("https://levgames.nl/polled/api/v1/profile")
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                _profileData.postValue("Request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        _profileData.postValue("HTTP error: ${response.code}")
                        return
                    }

                    val responseData = response.body?.string()
                    if (responseData != null) {
                        val json = JSONObject(responseData)

                        // Check if the 'status' is success
                        if (json.getString("status") == "success") {
                            val profile = json.getJSONObject("profile")

                            // Get the profile information
                            val username = profile.getString("username")
                            val image = profile.getString("image")
                            val userUrl = profile.getString("user_url")

                            // Construct the image URL
                            val imageUrl = "https://levgames.nl/polled/api/v1/users/$userUrl/$image"

                            // Create the profile info string
                            val profileInfo = """
                                Hello, $username! This is your profile page.
                            """.trimIndent()

                            // Update LiveData with profile info and image URL
                            _profileData.postValue(profileInfo)
                            _imageUrl.postValue(imageUrl)
                        } else {
                            _profileData.postValue("Error: ${json.getString("error")}")
                        }
                    } else {
                        _profileData.postValue("Empty response body")
                    }
                }
            }
        })
    }
}
