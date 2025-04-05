package com.wokki.polled

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.wokki.polled.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class MainActivity<File> : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val targetLanguage = Locale.getDefault().language
    private val client = OkHttpClient()

    private lateinit var sharedPreferences: SharedPreferences
    private val context = this
    private var accessToken: String? = null
    private val handler = Handler(Looper.getMainLooper())
    var isAppOpen = false // Track if the app is in the foreground

    private val notificationRunnable: Runnable = object : Runnable {
        override fun run() {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY) // 24-hour format

            if (hour !in 23..6) { // Skip from 23:00 to 06:59
                if (isAppOpen) {
                    showNotificationsDot() // Run a different function when the app is open
                    handler.postDelayed(this, 60000) // Re-run every 1 minute

                } else {
                    fetchNotifications()
                    handler.postDelayed(this, 600000) // Re-run every 10 minutes

                }
            }

        }
    }

    override fun onResume() {
        super.onResume()
        isAppOpen = true
    }

    override fun onPause() {
        super.onPause()
        isAppOpen = false
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if there's internet connection
        if (!isInternetAvailable()) {
            showNoInternetPage()
            return
        }

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

        val postId = intent.getStringExtra("postId")
        if (postId != null) {
            showFullPost(postId, context)
        }


        // Continue with normal setup for logged-in user
        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_notifications, R.id.navigation_post, R.id.navigation_messages, R.id.navigation_profile
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

        // Start periodic notification fetch
        handler.post(notificationRunnable)
        // Check for newer version asynchronously
        checkForNewVersion()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnected == true
    }

    private fun showNoInternetPage() {
        // If no internet, start the NoInternetActivity
        val intent = Intent(this, NoInternetActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity to prevent further actions

    }

    private fun checkForNewVersion() {
        // Use a coroutine to run the network request on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Make the request to get the version info
                val response = fetchVersionInfo()

                if (response != null) {
                    // Parse the JSON response
                    val versionInfo = Gson().fromJson(response, VersionInfo::class.java)

                    // Get the current app version
                    val currentVersionCode = getAppVersionCode()

                    // Compare the versionCode and versionName
                    if (versionInfo.versionCode > currentVersionCode) {
                        // Notify user of the new version
                        withContext(Dispatchers.Main) {
                            showUpdateDialog(versionInfo)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error, such as network issues
            }
        }
    }

    // Function to fetch the version info from the server
    private fun fetchVersionInfo(): String? {
        val request = Request.Builder()
            .url("https://polled.wokki20.nl/app/version.json")
            .build()

        return try {
            val response: Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string() // Return the JSON response as string
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Function to get the app's current versionCode
    private fun getAppVersionCode(): Int {
        val packageManager: PackageManager = packageManager
        val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        return packageInfo.versionCode
    }

    // Show a dialog to notify the user about the update
    private suspend fun showUpdateDialog(versionInfo: VersionInfo) {
        // Switch to the main thread for UI operations
        withContext(Dispatchers.Main) {
            // Create a builder for the alert dialog
            val builder = AlertDialog.Builder(context)

            builder.setTitle(getString(R.string.new_version_available))
            builder.setMessage(getString(R.string.new_version_message, versionInfo.versionName))

            // Create a custom LinearLayout to hold the ProgressBar
            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(32, 32, 32, 32)

            // Create and add the ProgressBar to the layout
            val progressBar = ProgressBar(context)
            progressBar.isIndeterminate = false  // Set it to false to show progress
            progressBar.max = 100  // Set maximum to 100 to represent the percentage
            progressBar.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(progressBar)

            // Set the custom background for the dialog
            val customBackground: Drawable = ContextCompat.getDrawable(context, R.drawable.report_post_bg)!!
            builder.setView(layout) // Add the custom layout to the dialog

            // Set positive and negative buttons (Download and Cancel)
            builder.setPositiveButton(getString(R.string.download)) { dialog, which ->
                dialog.dismiss()
                downloadAndInstallApk("https://polled.wokki20.nl/app/install", progressBar)
            }
            builder.setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                dialog.dismiss()
            }

            // Creating the dialog
            val dialog = builder.create()

            // Apply the custom background to the dialog's window (optional)
            dialog.window?.setBackgroundDrawable(customBackground)

            // Show the dialog
            dialog.show()
        }
    }


    // Method to download and install the APK
    private fun downloadAndInstallApk(url: String, progressBar: ProgressBar) {
        // Start a download in the background
        Thread {
            try {
                // Define the file path where the APK will be saved
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "polled.apk")
                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.connect()

                // Get the total length of the APK
                val totalLength = connection.contentLength

                // Get the input stream to read the APK from the URL
                val inputStream: InputStream = connection.inputStream
                val outputStream: OutputStream = file.outputStream()

                // Buffer for download
                val buffer = ByteArray(1024)
                var bytesRead: Int
                var totalRead = 0
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    // Update the progress bar
                    val progress = (totalRead * 100 / totalLength)
                    progressBar.progress = progress
                }
                outputStream.flush()
                inputStream.close()
                outputStream.close()

                // Now that the APK is downloaded, initiate installation
                installApk(file)

            } catch (e: Exception) {
                Log.e("DownloadError", "Error downloading APK: ${e.message}")
            }
        }.start()
    }



    // Method to install the downloaded APK
    private fun installApk(file: java.io.File) {
        // Check if the file exists before trying to install it
        if (file.exists()) {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // For API level 24 and higher, use FileProvider to share the file
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                // For lower API levels, you can directly share the file
                Uri.fromFile(file)
            }

            // Create an intent to install the APK
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Start the install activity
            context.startActivity(intent)
        } else {
            // Log an error if the file doesn't exist
            Log.e("InstallError", "APK file not found at ${file.path}")
        }
    }


    // Data class to parse the JSON
    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val AppName: String,
        val URL: String,
        val description: String,
        val androidPackageName: String
    )


    private fun navigateToPage(page: String) {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        when (page) {
            "home" -> navController.navigate(R.id.navigation_home)
            "post" -> navController.navigate(R.id.navigation_post)
            "profile" -> navController.navigate(R.id.navigation_profile)
            "messages" -> navController.navigate(R.id.navigation_messages)
            "notifications" -> navController.navigate(R.id.navigation_notifications)
            else -> Log.e("LoginRedirect", "Unknown page: $page")
        }


    }

    fun showNotificationsDot() {

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "https://wokki20.nl/polled/api/v1/notifications?read=false"

            // Build the request with Authorization header if token exists
            val request = Request.Builder()
                .url(url)
                .apply {
                    accessToken?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
                .build()

            try {
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        // Directly process the response (assuming it's JSON or similar)
                        val jsonResponse = JSONObject(responseBody)

                        // Handle expired token
                        if (jsonResponse.optString("error").contains("Invalid or expired access token")) {
                            context?.let {
                                RefreshAccessToken(it).apply {
                                    lifecycleScope.launch { refreshTokenIfNeeded() }
                                }
                            }
                        }

                        // If the notifications array is part of the response
                        val notifications = jsonResponse.optJSONArray("notifications")
                        if (notifications != null && notifications.length() > 0) {
                            // Loop through notifications and send them
                            withContext(Dispatchers.Main) {
                                binding.navView.getOrCreateBadge(R.id.navigation_notifications).apply {
                                    isVisible = false
                                    number = 0
                                }

                                for (i in 0 until notifications.length()) {
                                    val notification = notifications.getJSONObject(i)
                                    if (notification.optInt("is_read") == 0) {
                                        binding.navView.getOrCreateBadge(R.id.navigation_notifications).apply {
                                            isVisible = true
                                            number += 1 // Increment the current badge number
                                        }
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                println("No unread notifications.")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            println("Error: Empty response body.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        println("Error: Failed to fetch notifications. Response code: ${response.code}")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    println("Error: Network failure.")
                }
                e.printStackTrace()
            }
        }
    }


    private fun showFullPost(postId: String, context: Context) {
        val url = "https://wokki20.nl/polled/api/v1/timeline?limit=1&offset_id=$postId"

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

        // Preserve mentions (@username)
        val mentionRegex = "@[^\\s\\\\/:*?\"<>|]+".toRegex()
        val mentions = mutableMapOf<String, String>()
        var tempMessage = escapedMessage
        var index = 0

        tempMessage = mentionRegex.replace(tempMessage) { matchResult ->
            val placeholder = "\uE100$index\uE101"
            mentions[placeholder] = matchResult.value
            index++
            placeholder
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)  // Assuming message is in English
            .setTargetLanguage(targetLanguage)  // Set this dynamically based on the user's language
            .build()

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(tempMessage)
                    .addOnSuccessListener { translatedText ->
                        // Restore markdown placeholders
                        var formattedText = translatedText
                            .replace("\uE001", "*")
                            .replace("\uE002", "#")
                            .replace("\uE003", "_")
                            .replace("\uE004", "`")
                            .replace("\uE000", "\n")

                        // Restore mentions
                        mentions.forEach { (placeholder, original) ->
                            formattedText = formattedText.replace(placeholder, original)
                        }

                        Handler(Looper.getMainLooper()).post {
                            onTranslated(formattedText)
                        }
                    }
                    .addOnFailureListener {
                        Handler(Looper.getMainLooper()).post {
                            onTranslated(message)
                        }
                    }
            }
            .addOnFailureListener {
                Handler(Looper.getMainLooper()).post {
                    onTranslated(message)
                }
            }
    }


    private fun fetchNotifications() {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "https://wokki20.nl/polled/api/v1/notifications"

            // Build the request with Authorization header if token exists
            val request = Request.Builder()
                .url(url)
                .apply {
                    accessToken?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
                .build()

            try {
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        // Directly process the response (assuming it's JSON or similar)
                        val jsonResponse = JSONObject(responseBody)

                        // Handle expired token
                        if (jsonResponse.optString("error").contains("Invalid or expired access token")) {
                            context?.let {
                                RefreshAccessToken(it).apply {
                                    lifecycleScope.launch { refreshTokenIfNeeded() }
                                }
                            }
                        }

                        // If the notifications array is part of the response
                        val notifications = jsonResponse.optJSONArray("notifications")
                        if (notifications != null && notifications.length() > 0) {
                            // Loop through notifications and send them
                            withContext(Dispatchers.Main) {
                                for (i in 0 until notifications.length()) {
                                    val notification = notifications.getJSONObject(i)
                                    if (notification.optInt("is_read") == 0) {
                                        sendNotification(notification)
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                println("No unread notifications.")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            println("Error: Empty response body.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        println("Error: Failed to fetch notifications. Response code: ${response.code}")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    println("Error: Network failure.")
                }
                e.printStackTrace()
            }
        }
    }



    private fun sendNotification(notification: JSONObject) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val type = notification.optString("type", "")
        val by_user = notification.optString("by_user", "Unknown User")
        val by_user_url = notification.optString("by_user_url", "")

        val comment_message = if (type == "comment") {
            notification.optString("comment_message", "This message has been deleted")
        } else {
            null
        }
        val post_message = if (type == "new_post") {
            notification.optString("post_message", "deleted this post")
        } else {
            null
        }

        val title = getCorrectTitle(type, by_user)

        val correctMessage = if (type == "comment") {
            comment_message?.replace("%20", " ")
        } else {
            post_message?.replace("%20", " ")
        } ?: "No message available"

        val message = getCorrectMessage(type, correctMessage, by_user)

        // Decide which activity to open
        val intent = when (type) {
            "follow", "unfollow" -> {
                Intent(this, UserActivity::class.java).apply {
                    putExtra("userUrl", by_user_url)
                }
            }
            "comment", "like", "mention", "vote", "new_post" -> {
                val targetMessageId = notification.optInt("target_message_id").toString()
                Intent(this, MainActivity::class.java).apply {
                    putExtra("postId", targetMessageId)
                }
            }
            else -> {
                Intent(this, MainActivity::class.java)
            }
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }


        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, "default_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_notifications_icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "default_channel", "Default Notifications", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for unread alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = notification.hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }




    fun getCorrectTitle(type: String, byUser: String): String {
        if (type == "follow") {
            return "$byUser is now following you"
        } else if (type == "unfollow") {
            return "$byUser is no longer following you"
        } else if (type == "like") {
            return "Someone liked your post"
        } else if (type == "comment") {
            return "$byUser commented on your post"
        } else if (type == "mention") {
            return "$byUser mentioned you in their post"
        } else if (type == "vote") {
            return "$byUser voted on your post"
        } else if (type == "new_post") {
            return "Someone you follow just posted a new post"
        }
        return ""
    }

    fun getCorrectMessage(type: String, message: String?, byUser: String): String {
        return when (type) {
            "follow", "unfollow" -> {
                "Click to go to the profile of $byUser."
            }
            "like" -> {
                "$byUser liked your post."
            }
            "comment" -> {
                if (message == "This message has been deleted") message ?: "" else "\"$message\""
            }
            "mention" -> {
                "$byUser mentioned you in a post."
            }
            "vote" -> {
                "$byUser voted on your poll."
            }
            "new_post" -> {
                if (message == "deleted this post") {
                    "$byUser $message"
                } else {
                    "$byUser posted: \"$message\""
                }
            }
            else -> message ?: ""
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Stop the periodic check when the activity is destroyed
        handler.removeCallbacks(notificationRunnable)
    }


}

