package com.wokki.polled

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.wokki.polled.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val targetLanguage = Locale.getDefault().language

    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null

    private val context = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the user is logged in
        if (!isUserLoggedIn()) {
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivity(loginIntent)
            finish() // Close the app if not logged in
            return
        }

        sharedPreferences = applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        accessToken = sharedPreferences.getString("access_token", null)

        val refreshAccessToken = RefreshAccessToken(this)

        lifecycleScope.launch {
            refreshAccessToken.refreshTokenIfNeeded()
        }

        // Continue with normal setup for logged-in user
        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_post, R.id.navigation_profile
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Check if there's an extra for the page to navigate to
        val page = intent?.getStringExtra("page")
        val post = intent?.getStringExtra("post")
        if (page != null) {
            navigateToPage(page)
        } else if (post != null) {
            Log.d("MainActivity", "Received post ID: $post")
            showFullPost(post, context)
        }

    }

    private fun navigateToPage(page: String) {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        when (page) {
            "home" -> navController.navigate(R.id.navigation_home)
            "post" -> navController.navigate(R.id.navigation_post)
            "profile" -> navController.navigate(R.id.navigation_profile)
            else -> Log.e("LoginRedirect", "Unknown page: $page")
        }


    }

    private fun showFullPost(postId: String, context: Context) {
        val url = "https://levgames.nl/polled/api/v1/timeline?limit=1&offset_id=$postId"

        CoroutineScope(Dispatchers.IO).launch {
            var response = ""

            try {
                val urlObj = URL(url)
                val urlConnection = urlObj.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")
                urlConnection.connect()

                val responseCode = urlConnection.responseCode
                response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    urlConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    "Error: $responseCode"
                }
                urlConnection.disconnect()
            } catch (e: Exception) {
                response = "Error: ${e.message}"
            }

            // Switch to the main thread to start the activity
            withContext(Dispatchers.Main) {
                try {
                    // Parse response to JSON
                    val jsonResponse = JSONObject(response)

                    // Extract the "timeline" array
                    val timelineArray = jsonResponse.optJSONArray("timeline")

                    // Check if the timeline array is not empty and get the first item
                    if (timelineArray != null && timelineArray.length() > 0) {
                        val firstPost = timelineArray.getJSONObject(0)

                        // Now send over the first post data to FullPostActivity
                        val intent = Intent(context, FullPostActivity::class.java)
                        intent.putExtra("POST_DATA", firstPost.toString())  // Pass the first post as a string
                        context.startActivity(intent)
                    } else {
                        // Handle the case where there's no data in the timeline array
                        Log.e("Error", "No posts found in timeline.")
                    }
                } catch (e: Exception) {
                    Log.e("Error", "Error parsing response: ${e.message}")
                }
            }
        }
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return sharedPreferences.getBoolean("is_logged_in", false)
    }




    fun translateMessage(message: String, onTranslated: (String) -> Unit) {
        // Replace markdown characters with placeholders
        val escapedMessage = message
            .replace("*", "\uE001")  // Escape '*' (used for bold, italics)
            .replace("#", "\uE002")  // Escape '#' (used for headings)
            .replace("_", "\uE003")  // Escape '_' (used for italic, underlining)
            .replace("`", "\uE004")  // Escape '`' (used for code blocks)
            .replace("\n", "\uE000") // Escape newline character

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)  // Assuming message is in English
            .setTargetLanguage(targetLanguage)  // Set this dynamically based on the user's language
            .build()

        val translator = Translation.getClient(options)

        // Download the model if it's not already downloaded
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                // If the model is downloaded successfully, translate the message
                translator.translate(escapedMessage)
                    .addOnSuccessListener { translatedText ->
                        // Restore the markdown placeholders back to their original characters
                        val formattedText = translatedText
                            .replace("\uE001", "*")
                            .replace("\uE002", "#")
                            .replace("\uE003", "_")
                            .replace("\uE004", "`")
                            .replace("\uE000", "\n")

                        // Return the translated text via the callback, update UI on main thread
                        Handler(Looper.getMainLooper()).post {
                            onTranslated(formattedText)
                        }
                    }
                    .addOnFailureListener { exception ->
                        // If translation fails, return the original message
                        Handler(Looper.getMainLooper()).post {
                            onTranslated(message)
                        }
                    }
            }
            .addOnFailureListener { exception ->
                // If the model download fails, return the original message
                Handler(Looper.getMainLooper()).post {
                    onTranslated(message)
                }
            }
    }

}

