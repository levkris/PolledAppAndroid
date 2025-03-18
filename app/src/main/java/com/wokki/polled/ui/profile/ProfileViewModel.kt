package com.wokki.polled.ui.profile

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
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
                // Log the error message and avoid crashing the app
                _profileData.postValue("Request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!response.isSuccessful) {
                            _profileData.postValue("HTTP error: ${response.code}")
                            return
                        }

                        val responseData = response.body?.string()
                        if (responseData != null) {
                            val json = JSONObject(responseData)


                            if (json.getBoolean("success")) {
                                val profile = json.getJSONObject("profile")

                                // Get the profile information
                                val username = profile.getString("username")
                                val image = profile.getString("image")
                                val userUrl = profile.getString("user_url")

                                // Construct the image URL
                                val imageUrl =
                                    "https://levgames.nl/polled/api/v1/users/$userUrl/$image"

                                // Create the profile info string
                                val profileInfo = """
                                Hello, $username! This is your profile page.
                            """.trimIndent()

                                // Update LiveData with profile info and image URL
                                _profileData.postValue(profileInfo)
                                _imageUrl.postValue(imageUrl)
                            } else {
                                try {
                                    // Assuming this part is fetching and parsing the JSON response
                                    if (json.has("message")) {
                                        val errorMessage = json.getString("message")
                                        println(errorMessage)

                                        // Check if the error message is "User is banned"
                                        if (errorMessage == "User is banned") {
                                            // Extract username and reason from the JSON response
                                            val username = json.getString("username")
                                            val reason = json.getString("reason")

                                            // Show the banned page in the fragment
                                            // Make sure to call this in the fragment context
                                            (context as? ProfileFragment)?.showBannedPage(
                                                username,
                                                reason
                                            )
                                        } else {
                                            _profileData.postValue("Error: $errorMessage")
                                        }
                                    } else {
                                        _profileData.postValue("Empty response body")
                                    }
                                } catch (e: JSONException) {
                                    // Handle JSON parsing errors
                                    _profileData.postValue("Please clear the cache of the app and try again. To clear the cache: long press the app icon, click app info > Storage & cache > Clear storage.")
                                } catch (e: Exception) {
                                    // Handle any other unexpected errors and avoid crashing the app
                                    // If the exception is related to banning, handle it in the same way
                                    if (e.message?.contains("User is banned") == true) {
                                        val username = "unknown"
                                        val reason = "unknown reason"
                                        (context as? ProfileFragment)?.showBannedPage(
                                            username,
                                            reason
                                        )
                                        _profileData.postValue("Error: User is banned.")
                                    } else {
                                        _profileData.postValue("Error: ${e.message}")
                                    }
                                }


                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle any other unexpected errors and avoid crashing the app
                    _profileData.postValue("Error: ${e.message}")
                }
            }
        })
    }





}