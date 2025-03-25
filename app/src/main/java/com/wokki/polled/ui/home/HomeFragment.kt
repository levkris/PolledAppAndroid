package com.wokki.polled.ui.home

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.wokki.polled.R
import com.wokki.polled.databinding.FragmentHomeBinding
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HomeFragment : Fragment() {

    private var offset = 0
    private var loading = false



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

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var timelineAdapter: TimelineAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var homeViewModel: HomeViewModel

    private lateinit var timelineRecyclerView: RecyclerView
    private lateinit var bannedLayout: LinearLayout
    private lateinit var bannedMessageTextView: TextView
    private lateinit var bannedReasonTextView: TextView
    private lateinit var bannedUsernameTextView: TextView
    private lateinit var appealBanButton: Button


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)



        // Start internet check loop
        handler.post(internetCheckRunnable)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize the RecyclerView
        recyclerView = binding.root.findViewById(R.id.timelineRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Add OnScrollListener for infinite scrolling
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                // Check if the user is near the bottom
                if (!loading && lastVisibleItem == totalItemCount - 1) {
                    // Start loading more data
                    loadMoreData()
                }
            }
        })

        // SwipeRefreshLayout for pull-to-refresh gesture
        swipeRefreshLayout = binding.swipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            // Clear the existing timeline data and reset offset when refreshing
            offset = 0
            homeViewModel.setTimelineData(emptyList())  // Clear data in ViewModel
            fetchData(true)
        }

        val searchIcon: ImageView = binding.root.findViewById(R.id.searchIcon)

// Check the current night mode
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

// Change the icon based on the night mode
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            searchIcon.setImageResource(R.drawable.ic_search_white_24dp) // Dark mode
        } else {
            searchIcon.setImageResource(R.drawable.ic_search_black_24dp) // Light mode
        }


        // Observe ViewModel for timeline data and update UI
        homeViewModel.timelineData.observe(viewLifecycleOwner) { timeline ->
            if (timeline.isNotEmpty()) {
                if (!::timelineAdapter.isInitialized) {
                    // Get the current scroll position before submitting the list
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastKnownPosition = layoutManager.findFirstVisibleItemPosition()

                    val timelineAdapter = TimelineAdapter(requireContext()) // pass parentFragmentManager here

                    val timelineData: List<JSONObject> = timeline

                    timelineAdapter.submitList(timelineData)
                    recyclerView.scrollToPosition(lastKnownPosition)



                    recyclerView.adapter = timelineAdapter
                } else {
                    // Fetch the current data from ViewModel
                    val currentData = homeViewModel.timelineData.value ?: emptyList()
                    val timelineList = timeline

                    // Combine current data with new timeline data
                    val newData = currentData + timelineList

                    // Update ViewModel with the combined data
                    homeViewModel.setTimelineData(newData)

                    // Update the adapter's list
                    timelineAdapter.submitList(newData)


                }


            }
        }

        // Fetch data on first load if there's no cached data
        if (homeViewModel.timelineData.value.isNullOrEmpty()) {
            fetchData(false)
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Safely access views using the 'view' property
        timelineRecyclerView = view.findViewById(R.id.timelineRecyclerView)
        bannedLayout = view.findViewById(R.id.banned_layout)
        bannedMessageTextView = view.findViewById(R.id.banned_message)
        bannedReasonTextView = view.findViewById(R.id.banned_reason)
        bannedUsernameTextView = view.findViewById(R.id.banned_username)
        appealBanButton = view.findViewById(R.id.appealBanButton)
        // Ensure that view has been initialized before using it
        timelineRecyclerView?.let {
            it.visibility = View.VISIBLE
        }

        // Initially hide the banned layout
        bannedLayout?.visibility = View.GONE
    }

    private fun loadMoreData() {
        loading = true
        fetchDataFromApi()
    }

    fun fetchData(forceRefresh: Boolean) {
        // If forceRefresh is true, perform a new API call, otherwise, use cached data
        if (forceRefresh) {
            // Reset offset to 0 and fetch new data
            offset = 0
            fetchDataFromApi()
        } else {
            // Use cached data if available
            val cachedData = homeViewModel.timelineData.value
            if (cachedData.isNullOrEmpty()) {
                fetchDataFromApi() // Fetch data if no cache exists
            }
        }
    }


    private fun fetchDataFromApi() {
        // Check internet connection
        if (!isInternetAvailable(requireContext())) {
            binding.noInternetBanner.visibility = View.VISIBLE
            binding.noInternetWarning.visibility = View.VISIBLE
            swipeRefreshLayout.isRefreshing = false
            homeViewModel.setTimelineData(emptyList())
            return
        } else {
            binding.noInternetBanner.visibility = View.GONE
            binding.noInternetWarning.visibility = View.GONE
        }

        val sharedPreferences = requireActivity().getSharedPreferences("user_prefs", MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken != null) {
            FetchTimelineTask(accessToken).execute()
        }
    }

    // AsyncTask to handle the network request
    inner class FetchTimelineTask(private val accessToken: String) : AsyncTask<Void, Void, String>() {

        override fun doInBackground(vararg params: Void?): String {
            val url = URL("https://wokki20.nl/polled/api/v1/timeline?limit=10&offset=$offset")
            val urlConnection: HttpURLConnection = url.openConnection() as HttpURLConnection

            try {
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")
                urlConnection.connect()

                // Read the response
                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = urlConnection.inputStream
                    return inputStream.bufferedReader().use { it.readText() }
                } else {
                    throw Exception("Error: $responseCode")

                }
            } catch (e: Exception) {
                return "Error: ${e.message}"

            } finally {
                urlConnection.disconnect()
            }
        }

        private fun retryRequest() {
            println("Retrying the request after token refresh...")
            execute()
        }


        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            swipeRefreshLayout.isRefreshing = false // Stop refreshing animation
            homeViewModel.setTimelineData(emptyList())
            loading = false // Reset loading flag

            // Log the raw response
            println("Raw response: $result")

            try {
                val jsonResponse = JSONObject(result)

                // Check if the response indicates a ban
                if (jsonResponse.has("success") && jsonResponse.getBoolean("success") == false && jsonResponse.has("message")) {
                    val message = jsonResponse.getString("message")
                    if (message == "User is banned") {
                        val username = jsonResponse.getString("username")
                        val reason = jsonResponse.getString("reason")

                        // Update the UI to show the banned page
                        showBannedPage(username, reason)
                        return // Exit early since we don't need to process the timeline anymore
                    }
                }


                // Existing logic for handling non-banned users
                if (jsonResponse.has("error")) {
                    val errorMessage = jsonResponse.getString("error")
                    println("Error from server: $errorMessage")

                    var retried = false
                    if (errorMessage == "Invalid or expired access token" && !retried) {
                        retried = true
                        retryRequest()
                    }
                } else {
                    // Try to parse the response as JSON
                    val status = jsonResponse.optString("status")
                    val message = jsonResponse.optString("message")

                    if (status != "success") {
                        // Handle the error if status is not success
                        throw Exception("Error: Unexpected status - $status")
                    } else {

                        if (message == "No posts available") {
                            // Clear cached data
                            homeViewModel.timelineData.value ?: emptyList()
                            // Fetch new data from the API
                            fetchDataFromApi()
                        }
                        // Success, process the timeline
                        val timeline = jsonResponse.optJSONArray("timeline")
                        if (timeline != null && timeline.length() > 0) {
                            // Convert the timeline JSONArray into a List<JSONObject>
                            val timelineList = mutableListOf<JSONObject>()
                            for (i in 0 until timeline.length()) {
                                timelineList.add(timeline.getJSONObject(i))
                            }

                            // Update ViewModel with the new data
                            val currentData = homeViewModel.timelineData.value ?: emptyList()
                            homeViewModel.setTimelineData(currentData + timelineList) // Append new data
                            offset += 10 // Increment offset for the next API call
                        } else {
                            println("No more posts to load.")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Failed to parse JSON response: $e")
            }
        }


        private fun showBannedPage(username: String, reason: String) {
            // Safely access views using 'view' property and ensure 'view' is not null
            view?.findViewById<RecyclerView>(R.id.timelineRecyclerView)?.visibility = View.GONE
            view?.findViewById<LinearLayout>(R.id.banned_layout)?.visibility = View.VISIBLE

            // Handle the reason text with bold and italic formatting
            val formattedReason = formatTextWithStyles(reason)

            appealBanButton.isEnabled = true // Ensure the button is enabled

            appealBanButton?.requestFocus()

            appealBanButton?.setOnClickListener {
                openMailTo(username)
            }

            // Set the formatted reason text with bold/italic styles
            view?.findViewById<TextView>(R.id.banned_reason)?.text = formattedReason
            view?.findViewById<TextView>(R.id.banned_username)?.text = "Username: $username"
        }

        // Function to format the text with bold and italic, remove the markers, and handle newlines
        private fun formatTextWithStyles(text: String): CharSequence {
            var formattedText = text

            // Handle line breaks by replacing \n with <br>
            formattedText = formattedText.replace("\n", "<br>")

            // Remove the bold markers (**)
            val boldPattern = "\\*\\*(.*?)\\*\\*".toRegex()
            formattedText = formattedText.replace(boldPattern) { match ->
                val boldText = match.groupValues[1]  // Get the content inside ** **
                "<b>$boldText</b>" // Wrap it in <b> for bold styling
            }

            // Remove the italic markers (* or _)
            val italicPattern = "([*_])(.*?)([*_])".toRegex()
            formattedText = formattedText.replace(italicPattern) { match ->
                val italicText = match.groupValues[2]  // Get the content inside * or _
                "<i>$italicText</i>" // Wrap it in <i> for italic styling
            }

            // Convert the formatted text to a SpannableString for styling
            return Html.fromHtml(formattedText, Html.FROM_HTML_MODE_LEGACY)
        }



        private fun openMailTo(username: String) {
            // Create an intent for opening the mailto link (email) with the subject and body
            val emailUri = Uri.parse("mailto:info@wokki20.nl?subject=Ban Appeal for $username&body=I would like to appeal my ban.")
            val emailIntent = Intent(Intent.ACTION_VIEW, emailUri)

            // Directly start the activity without checking for a mail app
            startActivity(emailIntent)
        }




    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handler.removeCallbacks(internetCheckRunnable) // Stop checking when fragment is destroyed
    }

}
