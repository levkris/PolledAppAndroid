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
import org.json.JSONObject
import java.io.IOException

class ProfileViewModel(private val context: Context) : ViewModel() {

    private val _profileData = MutableLiveData<String>()
    val profileData: LiveData<String> = _profileData

    private val _imageUrl = MutableLiveData<String>()
    val imageUrl: LiveData<String> = _imageUrl

    private val _bannerUrl = MutableLiveData<String>()
    val bannerUrl: LiveData<String> = _bannerUrl

    private val _verified = MutableLiveData<Boolean>()
    val verified: LiveData<Boolean> = _verified


    private val _followersCount = MutableLiveData<Int>()
    val followersCount: LiveData<Int> = _followersCount

    private val _followingCount = MutableLiveData<Int>()
    val followingCount: LiveData<Int> = _followingCount

    private val _postsCount = MutableLiveData<Int>()
    val postsCount: LiveData<Int> = _postsCount

    private val _bio = MutableLiveData<String>()
    val bio: LiveData<String> = _bio

    // Fetch profile data from the API
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
            .url("https://wokki20.nl/polled/api/v1/profile")
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

                            val status = json.getString("status")
                            if (status == "success") {
                                val profile = json.getJSONObject("profile")

                                // Get the profile information
                                val username = profile.getString("username")
                                val image = profile.getString("image")
                                val userUrl = profile.getString("user_url")
                                val banner = profile.getString("banner")
                                val verified = profile.getInt("verified") == 1
                                val followersCount = profile.getInt("followers_count")
                                val followingCount = profile.getInt("following_count")
                                val postsCount = profile.getInt("posts_count")
                                val bio = profile.getString("bio")

                                // Construct the image URL
                                val imageUrl = "https://wokki20.nl/polled/api/v1/users/$userUrl/$image"


                                val bannerUrl = "https://wokki20.nl/polled/api/v1/users/$userUrl/$banner"


                                _bannerUrl.postValue(bannerUrl)
                                // Update LiveData with profile info and image URL
                                _profileData.postValue(username)
                                _imageUrl.postValue(imageUrl)
                                _verified.postValue(verified)
                                _followersCount.postValue(followersCount)
                                _followingCount.postValue(followingCount)
                                _postsCount.postValue(postsCount)
                                _bio.postValue(bio)


                            } else {
                                // Handle case where status is not "success"
                                val errorMessage = json.optString("message", "Unknown error")
                                _profileData.postValue("Error: $errorMessage")
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
