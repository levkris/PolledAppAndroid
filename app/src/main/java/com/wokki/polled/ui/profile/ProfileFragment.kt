package com.wokki.polled.ui.profile

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.wokki.polled.FullPostActivity
import com.wokki.polled.LoginActivity
import com.wokki.polled.R
import com.wokki.polled.databinding.FragmentProfileBinding
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ProfileFragment : Fragment() {
    private val client = OkHttpClient()

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val internetCheckRunnable = object : Runnable {
        override fun run() {
            if (!isInternetAvailable(requireContext())) {
                binding.noInternetBanner.visibility = View.VISIBLE
                binding.noInternetWarning.visibility = View.VISIBLE
            } else {
                binding.noInternetBanner.visibility = View.GONE
                binding.noInternetWarning.visibility = View.GONE
            }
            handler.postDelayed(this, 3000) // Re-run every 3 seconds
        }
    }

    private lateinit var bannedLayout: LinearLayout
    private lateinit var bannedMessageTextView: TextView
    private lateinit var bannedReasonTextView: TextView
    private lateinit var bannedUsernameTextView: TextView
    private lateinit var appealBanButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var markwon: Markwon? = null
    private lateinit var tabLayout: TabLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        handler.post(internetCheckRunnable)


        bannedLayout = binding.bannedLayout
        bannedMessageTextView = binding.bannedMessage
        bannedReasonTextView = binding.bannedReason
        bannedUsernameTextView = binding.bannedUsername
        appealBanButton = binding.appealBanButton
        sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        markwon = Markwon.create(requireContext())

        // Initially hide the banned layout
        bannedLayout.visibility = View.GONE

        // Set up the profile ViewModel
        val profileViewModelFactory = ProfileViewModelFactory(requireContext())
        val profileViewModel = ViewModelProvider(this, profileViewModelFactory).get(ProfileViewModel::class.java)

        // Observe profile data and image URL LiveData
        profileViewModel.profileUsername.observe(viewLifecycleOwner) { username ->
            binding.username.text = username
        }

        profileViewModel.imageUrl.observe(viewLifecycleOwner) { imageUrl ->
            Glide.with(this)
                .load(imageUrl)
                .centerCrop()
                .transform(RoundedCorners(16))  // Set the radius here (e.g., 16dp)
                .into(binding.profileImage)
        }

        // Observe profile data and banner URL LiveData
        profileViewModel.bannerUrl.observe(viewLifecycleOwner) { banner ->
            Glide.with(this)
                .load(banner)
                .centerCrop()  // Makes the image fill the ImageView while maintaining aspect ratio
                .into(binding.banner)
        }

        profileViewModel.verified.observe(viewLifecycleOwner) { verified ->
            binding.verifiedIcon.visibility = if (verified) View.VISIBLE else View.GONE
        }

        profileViewModel.bio.observe(viewLifecycleOwner) { bio ->
            // if bio is null, set it to something else
            markwon?.setMarkdown(binding.bio, bio)
        }



        profileViewModel.followersCount.observe(viewLifecycleOwner) { followers ->
            profileViewModel.followingCount.observe(viewLifecycleOwner) { following ->
                profileViewModel.postsCount.observe(viewLifecycleOwner) { posts ->
                    // Format the text with Followers, Following, and Posts counts
                    val followersText = getString(R.string.followers_count, followers)
                    val followingText = getString(R.string.following_count, following)
                    val postsText = getString(R.string.posts_count, posts)

                    // Combine the three texts
                    val fullText = "$followersText | $followingText | $postsText"

                    // Create a SpannableString to make the numbers bold
                    val spannableString = SpannableString(fullText)

                    // Get positions for followers count
                    val followersStart = fullText.indexOf(followers.toString())
                    val followersEnd = followersStart + followers.toString().length

                    // Get positions for following count
                    val followingStart = fullText.indexOf(following.toString())
                    val followingEnd = followingStart + following.toString().length

                    // Get positions for posts count
                    val postsStart = fullText.indexOf(posts.toString())
                    val postsEnd = postsStart + posts.toString().length

                    // Set the bold span for the numbers
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), followersStart, followersEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), followingStart, followingEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), postsStart, postsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Set the text to the TextView
                    binding.accountInfo.text = spannableString

                }
            }
        }

        var postsList: List<Post>? = null

        profileViewModel.posts.observe(viewLifecycleOwner) { posts ->
            postsList = posts
        }


// Retrieve the last selected tab position from SharedPreferences (default to 0)
        val lastSelectedTabPosition = sharedPreferences.getInt("lastSelectedTab", 0)

// Initialize the TabLayout
        tabLayout = binding.profileTabs // Replace with your actual TabLayout reference

// Add tabs to the TabLayout
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_about))
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_activity))
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_settings))

// Set the last selected tab after adding the tabs
        tabLayout.getTabAt(lastSelectedTabPosition)?.select()

        if (lastSelectedTabPosition == 0) {
            showAboutSection()
        } else if (lastSelectedTabPosition == 1) {
            showActivitySection(postsList)
        } else if (lastSelectedTabPosition == 2) {
            showSettingsSection()
        } else {
            showAboutSection()
        }

// Listen for tab selection
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Save the selected tab position in SharedPreferences
                sharedPreferences.edit().putInt("lastSelectedTab", tab?.position ?: 0).apply()

                when (tab?.position) {
                    0 -> showAboutSection()
                    1 -> showActivitySection(postsList)
                    2 -> showSettingsSection()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })



        // Fetch profile data
        profileViewModel.fetchProfileData()

        // Set up logout button click listener
        binding.logoutButton.setOnClickListener {
            val sharedPreferences = context?.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences?.edit()
            editor?.remove("access_token")
            editor?.putBoolean("is_logged_in", false)
            editor?.apply()

            navigateToLogin()
        }
    }

    fun showAboutSection() {
        binding.settingsContainer.visibility = View.GONE
        binding.bio.visibility = View.VISIBLE
        binding.posts.visibility = View.GONE


    }

    fun showActivitySection(posts: List<Post>?) {
        // Hide logout button and bio
        binding.settingsContainer.visibility = View.GONE
        binding.bio.visibility = View.GONE

        // Show posts section
        binding.posts.visibility = View.VISIBLE

        // Get reference to GridLayout where posts are shown
        val gridLayout = binding.postsGrid // Assuming 'postsGrid' is your GridLayout
        gridLayout.removeAllViews()

        // Check if posts is not null or empty
        posts?.forEach { post ->
            // Inflate the profile_post_item.xml and add it to the GridLayout
            val postView = LayoutInflater.from(requireContext())
                .inflate(R.layout.profile_post_item, gridLayout, false)

            // Add the post view to the GridLayout
            gridLayout.addView(postView)

            // Get references to UI elements inside the post layout
            val postMessage = postView.findViewById<TextView>(R.id.post_text)
            val showMoreButton = postView.findViewById<TextView>(R.id.show_more_button)

            val strippedPostMessageText = if (post.message.length > 100) {
                post.message.take(100) + "..."
            } else {
                post.message
            }
            // Set post content
            postMessage.text = strippedPostMessageText
            accessToken = sharedPreferences.getString("access_token", null)

            val postId = post.id

            val listener = View.OnClickListener {
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
                                context?.startActivity(intent)
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

            // Set the same listener for both views
            postView.setOnClickListener(listener)
            showMoreButton.setOnClickListener(listener)
            postMessage.setOnClickListener(listener)


        }
    }

    fun showSettingsSection() {
        // Ensure the sharedPreferences is initialized before using it
        if (!::sharedPreferences.isInitialized) {
            sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        }

        // Hide and show views as needed
        binding.settingsContainer.visibility = View.VISIBLE
        binding.bio.visibility = View.GONE
        binding.posts.visibility = View.GONE

        binding.checkUpdateButton.setOnClickListener {
            checkForNewVersion()
        }

        // Set the auto-translate checkbox based on shared preferences
        binding.autoTranslatePosts.isChecked = sharedPreferences.getBoolean("auto_translate", false)
        binding.autoUpdate.isChecked = sharedPreferences.getBoolean("auto_update", true)

        // Listen for changes to the checkbox and save the value to SharedPreferences
        binding.autoTranslatePosts.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("auto_translate", isChecked)
            editor.apply()
        }

        binding.autoUpdate.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("auto_update", isChecked)
            editor.apply()
        }

        var isProgrammaticChange = false

        val radioDark = binding.radioDark
        val radioLight = binding.radioLight
        val autoTheme = binding.useDeviceTheme

        val isAutoTheme = sharedPreferences.getBoolean("auto_theme", true)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        radioDark.isChecked = isDarkMode
        radioLight.isChecked = !isDarkMode
        autoTheme.isChecked = isAutoTheme

        autoTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener

            if (isChecked) {
                // Get system-wide theme preference (phone setting)
                val uiModeManager = requireContext().getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                val systemNightMode = uiModeManager.nightMode

                when (systemNightMode) {
                    UiModeManager.MODE_NIGHT_YES -> {
                        radioDark.isChecked = true
                        radioLight.isChecked = false
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                    UiModeManager.MODE_NIGHT_NO -> {
                        radioLight.isChecked = true
                        radioDark.isChecked = false
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                    else -> {
                        // fallback to config if systemNightMode is unknown
                        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                            radioDark.isChecked = true
                            radioLight.isChecked = false
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        } else {
                            radioLight.isChecked = true
                            radioDark.isChecked = false
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        }
                    }
                }
            }

            sharedPreferences.edit()
                .putBoolean("auto_theme", isChecked)
                .apply()
        }


        // DARK MODE button clicked
        radioDark.setOnClickListener {
            radioLight.isChecked = false
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            sharedPreferences.edit()
                .putBoolean("dark_mode", true)
                .putBoolean("auto_theme", false)
                .apply()

            // Disable auto theme programmatically
            isProgrammaticChange = true
            autoTheme.isChecked = false
            isProgrammaticChange = false

            showSettingsSection()
        }

        // LIGHT MODE button clicked
        radioLight.setOnClickListener {
            radioDark.isChecked = false
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            sharedPreferences.edit()
                .putBoolean("dark_mode", false)
                .putBoolean("auto_theme", false)
                .apply()

            // Disable auto theme programmatically
            isProgrammaticChange = true
            autoTheme.isChecked = false
            isProgrammaticChange = false

            showSettingsSection()
        }


        // Somewhere in your Activity or Fragment
        val sendNotifications = sharedPreferences.getBoolean("send_notifications", false)
        val sendNotificationsBinding = binding.sendNotifications

        sendNotificationsBinding.isChecked = sendNotifications

        sendNotificationsBinding.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        // Ask for permission in Fragment
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            1001
                        )
                    } else {
                        // Already granted
                        sharedPreferences.edit()
                            .putBoolean("send_notifications", true)
                            .apply()
                    }
                } else {
                    // Old Android, no permission needed
                    sharedPreferences.edit()
                        .putBoolean("send_notifications", true)
                        .apply()
                }
            } else {
                sharedPreferences.edit()
                    .putBoolean("send_notifications", false)
                    .apply()
            }
        }



    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Perm granted
                sharedPreferences.edit()
                    .putBoolean("send_notifications", true)
                    .apply()
                binding.sendNotifications.isChecked = true
            } else {
                // Denied
                sharedPreferences.edit()
                    .putBoolean("send_notifications", false)
                    .apply()
                binding.sendNotifications.isChecked = false
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the binding and set up the views
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handler.removeCallbacks(internetCheckRunnable) // Stop checking when fragment is destroyed

    }

    fun showBannedPage(username: String, reason: String) {
        if (_binding == null) return  // Avoid null pointer exception

        // Safely access views via the binding reference
        binding.bannedLayout.visibility = View.VISIBLE

        // Format the banned reason text
        val formattedReason = formatTextWithStyles(reason)

        // Enable the appeal ban button and set up click listener
        appealBanButton.isEnabled = true
        appealBanButton.requestFocus()
        appealBanButton.setOnClickListener {
            openMailTo(username)
        }

        // Set formatted reason and username on the views
        binding.bannedReason.text = formattedReason
        binding.bannedUsername.text = "Username: $username"
    }

    // Function to format text with bold/italic styles
    private fun formatTextWithStyles(text: String): CharSequence {
        var formattedText = text
        formattedText = formattedText.replace("\n", "<br>")
        formattedText = formattedText.replace("\\*\\*(.*?)\\*\\*".toRegex()) { match ->
            "<b>${match.groupValues[1]}</b>"
        }
        formattedText = formattedText.replace("([*_])(.*?)([*_])".toRegex()) { match ->
            "<i>${match.groupValues[2]}</i>"
        }
        return Html.fromHtml(formattedText, Html.FROM_HTML_MODE_LEGACY)
    }

    // Function to open a mailto link for ban appeal
    private fun openMailTo(username: String) {
        val emailUri = Uri.parse("mailto:info@wokki20.nl?subject=Ban Appeal for $username&body=I would like to appeal my ban.")
        val emailIntent = Intent(Intent.ACTION_VIEW, emailUri)
        ContextCompat.startActivity(requireContext(), emailIntent, null)
    }

    // Function to navigate to the login activity
    private fun navigateToLogin() {
        val intent = Intent(context, LoginActivity::class.java)
        startActivity(intent)
        activity?.finish()  // Finish the current activity
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
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, getString(R.string.no_update_available), Toast.LENGTH_SHORT).show()
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
        val packageManager: PackageManager = requireContext().packageManager
        val packageInfo: PackageInfo = packageManager.getPackageInfo(requireContext().packageName, 0)
        return packageInfo.versionCode
    }

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


            // Set the custom background for the dialog
            val customBackground: Drawable = ContextCompat.getDrawable(requireContext(), R.drawable.report_post_bg)!!
            builder.setView(layout) // Add the custom layout to the dialog

            // Set positive and negative buttons (Download and Cancel)
            builder.setPositiveButton(getString(R.string.download)) { dialog, which ->


                downloadAndInstallApk("https://polled.wokki20.nl/app/install")
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



    private fun downloadAndInstallApk(url: String) {
        // Create a custom progress dialog with a ProgressBar
        val progressDialog = Dialog(requireContext()).apply {
            setContentView(R.layout.custom_progress_dialog) // Custom layout with ProgressBar
            setCancelable(false) // Prevent canceling by tapping outside
            show()
        }

        // Set up the progress bar in the custom dialog
        val progressBar = progressDialog.findViewById<ProgressBar>(R.id.progressBar)
        progressDialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.report_post_bg))

        // Create a handler to update the progress on the main thread
        val handler = Handler(Looper.getMainLooper())

        // Start a download in the background
        Thread {
            try {
                // Define the file path where the APK will be saved
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "polled.apk")
                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.connect()

                // Get the total length of the APK file
                val totalLength = connection.contentLength

                // Get the input stream to read the APK from the URL
                val inputStream: InputStream = connection.inputStream
                val outputStream: OutputStream = file.outputStream()

                // Buffer for download
                val buffer = ByteArray(1024)
                var bytesRead: Int
                var totalRead = 0

                // Loop to read from the input stream and write to the output file
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    // Update the progress on the main thread using the handler
                    val progress = (totalRead * 100 / totalLength)
                    handler.post {
                        // Update the progress bar in the dialog
                        progressBar.progress = progress
                    }
                }

                // After downloading, flush streams and close
                outputStream.flush()
                inputStream.close()
                outputStream.close()

                // Now that the APK is downloaded, initiate installation
                installApk(file)

                // Dismiss the progress dialog once the download is complete
                handler.post {
                    progressDialog.dismiss()
                }

            } catch (e: Exception) {
                Log.e("DownloadError", "Error downloading APK: ${e.message}")

                // Dismiss the progress dialog in case of an error
                handler.post {
                    progressDialog.dismiss()
                }
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
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
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
            requireContext().startActivity(intent)
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
}
