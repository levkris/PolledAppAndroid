package com.wokki.polled

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.tabs.TabLayout
import com.wokki.polled.ui.profile.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class UserActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null

    // Declare the views at the top but initialize them later
    private lateinit var profilePic: ImageView
    private lateinit var userName: TextView
    private lateinit var backButton: ImageButton
    private lateinit var profileOf: TextView
    private lateinit var bio: TextView
    private lateinit var posts: NestedScrollView
    private lateinit var postsGrid: GridLayout
    private lateinit var banner: ImageView
    private lateinit var accountInfo: TextView
    private lateinit var followButton: Button
    private lateinit var header: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        supportActionBar?.hide()

        sharedPreferences = applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        accessToken = sharedPreferences.getString("access_token", null)

        // Initialize views
        profilePic = findViewById(R.id.profile_image)
        userName = findViewById(R.id.username)
        backButton = findViewById(R.id.backButton)
        profileOf = findViewById(R.id.profileOf)
        bio = findViewById(R.id.bio)
        posts = findViewById(R.id.posts)
        postsGrid = findViewById(R.id.posts_grid)
        banner = findViewById(R.id.banner)
        accountInfo = findViewById(R.id.accountInfo)
        followButton = findViewById(R.id.followButton)

        backButton.setOnClickListener {
            onBackPressed()
        }



        val requestUserUrl = intent.getStringExtra("userUrl")
        val requestUserName = intent.getStringExtra("userName")

        lifecycleScope.launch(Dispatchers.IO) {
            val userProfileFull = fetchUserProfile(requestUserUrl, requestUserName)

            withContext(Dispatchers.Main) {
                if (userProfileFull != null) {
                    val userProfile = userProfileFull.optJSONObject("profile")
                    val image = userProfile?.optString("image")
                    val userUrl = userProfile?.optString("user_url")
                    val username = userProfile?.optString("username")
                    var isFollowing = userProfile?.optInt("is_following")
                    val followers = userProfile?.optInt("followers_count") ?: 0
                    val following = userProfile?.optInt("following_count") ?: 0
                    val postsCount = userProfile?.optInt("posts_count") ?: 0
                    val bannerImage = if (userProfile.isNull("banner")) {
                        null
                    } else {
                        userProfile?.optString("banner")
                    }
                    val bioText = if (userProfile.isNull("bio")) {
                        getString(R.string.no_bio_user)
                    } else {
                        userProfile?.optString("bio")
                    }

                    val postsList = mutableListOf<Post>()

                    val postsArray = userProfile?.getJSONArray("posts")
                    if (postsArray != null) {
                        for (i in 0 until postsArray.length()) {
                            val postJson = postsArray.getJSONObject(i)
                            val post = Post(
                                id = postJson.getInt("id"),
                                message = postJson.getString("message"),
                                createdAt = postJson.getString("created_at")
                            )
                            postsList.add(post)
                        }
                    }
                    if (image != null && userUrl != null) {
                        val imageUrl = "https://wokki20.nl/polled/api/v1/users/$userUrl/$image"
                        Glide.with(this@UserActivity)
                            .load(imageUrl)
                            .centerCrop()
                            .transform(RoundedCorners(16))
                            .into(profilePic)
                    }

                    if (bannerImage != null && userUrl != null) {
                        val bannerUrl = "https://wokki20.nl/polled/api/v1/users/$userUrl/$bannerImage"
                        Glide.with(this@UserActivity)
                            .load(bannerUrl)
                            .centerCrop()
                            .into(banner)
                    } else {
                        val bannerUrl = "https://polled.wokki20.nl/assets/img/default-banner.png"
                        Glide.with(this@UserActivity)
                            .load(bannerUrl)
                            .centerCrop()
                            .into(banner)
                    }

                    if (isFollowing == 1) {
                        followButton.text = getString(R.string.unfollow_button)
                    } else {
                        followButton.text = getString(R.string.follow_button)
                    }

                    followButton.setOnClickListener {
                        if (isFollowing == 1) {
                            isFollowing = 0
                            followButton.text = getString(R.string.follow_button)
                            unfollowUser(username)
                        } else {
                            isFollowing = 1
                            followButton.text = getString(R.string.unfollow_button)
                            followUser(username)
                        }
                    }

                    userName.text = username ?: "Unknown"

                    profileOf.text = getString(R.string.viewingProfileOf, username ?: "Unknown")

                    bio.text = bioText

                    val tabLayout = findViewById<TabLayout>(R.id.profile_tabs)

                    tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_about))
                    tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_activity))

                    tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                        override fun onTabSelected(tab: TabLayout.Tab?) {
                            when (tab?.position) {
                                0 -> showAboutSection()
                                1 -> showActivitySection(postsList)
                            }
                        }

                        override fun onTabUnselected(tab: TabLayout.Tab?) {}

                        override fun onTabReselected(tab: TabLayout.Tab?) {}
                    })


                    // Format the text with Followers, Following, and Posts counts
                    val followersText = getString(R.string.followers_count, followers)
                    val followingText = getString(R.string.following_count, following)
                    val postsText = getString(R.string.posts_count, postsCount)

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
                    val postsStart = fullText.indexOf(postsCount.toString())
                    val postsEnd = postsStart + postsCount.toString().length

                    // Set the bold span for the numbers
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), followersStart, followersEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), followingStart, followingEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), postsStart, postsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Set the text to the TextView
                    accountInfo.text = spannableString

                } else {
                    println("⚠️ User profile request failed!")
                }
            }
        }
    }

    fun showAboutSection() {
        bio.visibility = View.VISIBLE
        posts.visibility = View.GONE


    }

    fun showActivitySection(postsList: List<Post>?) {
        bio.visibility = View.GONE

        // Show posts section
        posts.visibility = View.VISIBLE

        // Get reference to GridLayout where posts are shown
        val gridLayout = postsGrid // Assuming 'postsGrid' is your GridLayout
        gridLayout.removeAllViews()

        // Check if posts is not null or empty
        postsList?.forEach { post ->
            // Inflate the profile_post_item.xml and add it to the GridLayout
            val postView = LayoutInflater.from(this)
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
                                val intent = Intent(this@UserActivity, FullPostActivity::class.java)
                                intent.putExtra("POST_DATA", firstPost.toString())  // Pass the first post as a string
                                this@UserActivity.startActivity(intent)
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

    fun unfollowUser(username: String?) {
        if (username.isNullOrEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://wokki20.nl/polled/api/v1/follow")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "DELETE"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $accessToken")

                val formData = "user=$username"

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(formData)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    println(response)
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText()
                    throw Exception("Error: HTTP $responseCode\\n$error")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun followUser(username: String?) {
        if (username.isNullOrEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://wokki20.nl/polled/api/v1/follow")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $accessToken")

                val formData = "user=$username"

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(formData)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    println(response)
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText()
                    throw Exception("Error: HTTP $responseCode\\n$error")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }



    private suspend fun fetchUserProfile(userUrl: String? = null, userName: String? = null): JSONObject? {
        val correctParameter = when {
            userUrl != null -> "?url=$userUrl"
            userName != null -> "?username=$userName"
            else -> return null
        }
        val apiUrl = "https://wokki20.nl/polled/api/v1/profile$correctParameter"

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.optString("status") == "success") {
                        return@withContext jsonResponse
                    } else {
                        println("Error: ${jsonResponse.optString("error")}")
                    }
                } else {
                    println("Network response was not ok, status: $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }


}
