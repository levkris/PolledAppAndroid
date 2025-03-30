package com.wokki.polled.ui.profile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
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
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.tabs.TabLayout
import com.wokki.polled.FullPostActivity
import com.wokki.polled.LoginActivity
import com.wokki.polled.R
import com.wokki.polled.databinding.FragmentProfileBinding
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ProfileFragment : Fragment() {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        profileViewModel.profileData.observe(viewLifecycleOwner) { username ->
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
            binding.bio.visibility = View.VISIBLE
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




        val tabLayout = view.findViewById<TabLayout>(R.id.profile_tabs)

        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_about))
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_activity))
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_settings))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
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

        // Set the auto-translate checkbox based on shared preferences
        binding.autoTranslatePosts.isChecked = sharedPreferences.getBoolean("auto_translate", false)

        // Listen for changes to the checkbox and save the value to SharedPreferences
        binding.autoTranslatePosts.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("auto_translate", isChecked)
            editor.apply()
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
}
