package com.wokki.polled

import android.content.Intent
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
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val targetLanguage = Locale.getDefault().language


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the user is logged in
        if (!isUserLoggedIn()) {
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivity(loginIntent)
            finish() // Close the app if not logged in
            return
        }


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

        if (page != null) {
            navigateToPage(page)
        }

    }

    private fun navigateToPage(page: String) {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        Log.d("NavController", "NavController found: $navController")
        navigateToPage(page)


        Log.e("LoginRedirect", "Attempting to navigate to: $page")

        when (page) {
            "home" -> navController.navigate(R.id.navigation_home)
            "post" -> navController.navigate(R.id.navigation_post)
            "profile" -> navController.navigate(R.id.navigation_profile)
            else -> Log.e("LoginRedirect", "Unknown page: $page")
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

