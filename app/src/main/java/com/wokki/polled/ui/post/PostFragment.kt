package com.wokki.polled.ui.post

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.wokki.polled.R
import com.wokki.polled.databinding.FragmentPostBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class PostFragment : Fragment() {
    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null



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

    private var _binding: FragmentPostBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {


        sharedPreferences = this.requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        accessToken = sharedPreferences.getString("access_token", null)

        handler.post(internetCheckRunnable)

        _binding = FragmentPostBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val pollTitleInput = binding.pollTitleInput
        val pollOptionsContainer = binding.pollOptionsContainer
        val addPollOptionButton = binding.addPollOptionButton
        val messageInput = binding.messageInput
        val postButton = binding.postButton

        var optionsAdded = false

        pollTitleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Only add new options if the poll title is not empty and if options haven't been added yet
                if (s != null && s.isNotEmpty() && !optionsAdded) {
                    // Loop to add 2 options when poll title is typed
                    for (i in 1..2) {
                        addNewPollOption(true)
                    }
                    optionsAdded = true // Mark options as added
                } else if (s.isNullOrEmpty()) {
                    // Reset flag and remove all options if poll title is cleared
                    optionsAdded = false
                    if (areAllPollOptionsEmpty()) {
                        removeAllPollOptions()
                    }
                }
            }
        })


        addPollOptionButton.setOnClickListener {
            addNewPollOption(false)
        }

        postButton.setOnClickListener {
            if (messageInput.text.isNotEmpty()) {
                if (pollTitleInput.text.isNotEmpty() && isAnyPollOptionEmpty()) {
                    errorForEmptyOptions()
                    return@setOnClickListener
                }

                // Collect the message
                val message = messageInput.text.toString()

                // Collect poll data if exists
                val pollQuestion = pollTitleInput.text.toString()
                val pollOptions = getPollOptions() // This would be a function that retrieves the poll options
                val multipleChoice = if (pollOptions.size > 1) 1 else 0 // If more than one option, multiple choice is allowed

                // If there is no poll, pass null or empty data for poll-related values
                createPost(message, pollQuestion, pollOptions, multipleChoice)

            } else {
                messageInput.error = getString(R.string.message_error)
            }
        }


        return root
    }

    fun getPollOptions(): List<String> {
        val options = mutableListOf<String>()


        for (i in 0 until binding.pollOptionsContainer.childCount) {
            val optionView = binding.pollOptionsContainer.getChildAt(i)
            val editText = optionView.findViewById<EditText>(R.id.pollOptionInput)

            options.add(editText.text.toString())
        }
        // Add more inputs as needed
        return options
    }


    private fun addNewPollOption(autoAdded: Boolean) {
        val optionView = LayoutInflater.from(requireContext()).inflate(R.layout.post_option, binding.pollOptionsContainer, false)

        // Get the EditText inside the inflated layout
        val editText = optionView.findViewById<EditText>(R.id.pollOptionInput)
        val removeButton = optionView.findViewById<ImageButton>(R.id.removeButton)
        // Set the hint dynamically using the string resource and formatting
        val optionNumber = binding.pollOptionsContainer.childCount + 1
        val hint = getString(R.string.poll_option_hint, optionNumber)
        editText.hint = hint

        if (optionNumber == 1 || optionNumber == 2) {
            removeButton.visibility = View.GONE
        }
        removeButton.setOnClickListener {
            binding.pollOptionsContainer.removeView(optionView)
        }
        if (autoAdded && binding.pollOptionsContainer.childCount <= 1) {
            binding.pollOptionsContainer.addView(optionView)
        } else if (autoAdded) {
            return
        } else {
            binding.pollOptionsContainer.addView(optionView)
        }
    }

    private fun isAnyPollOptionEmpty(): Boolean {
        // Iterate through all the options in the poll
        for (i in 0 until binding.pollOptionsContainer.childCount) {
            val optionView = binding.pollOptionsContainer.getChildAt(i)
            val editText = optionView.findViewById<EditText>(R.id.pollOptionInput)

            if (editText.text.isEmpty()) {
                return true
            }
        }
        return false
    }

    private fun areAllPollOptionsEmpty(): Boolean {
        // Iterate through all the options in the poll
        for (i in 0 until binding.pollOptionsContainer.childCount) {
            val optionView = binding.pollOptionsContainer.getChildAt(i)
            val editText = optionView.findViewById<EditText>(R.id.pollOptionInput)

            if (editText.text.isNotEmpty()) {
                // If any option has text, return false
                return false
            }
        }
        return true  // All options are empty
    }

    private fun removeAllPollOptions() {
        // Remove all options from the container
        binding.pollOptionsContainer.removeAllViews()
    }

    private fun errorForEmptyOptions() {
        // Iterate through all the options in the poll
        for (i in 0 until binding.pollOptionsContainer.childCount) {
            val optionView = binding.pollOptionsContainer.getChildAt(i)
            val editText = optionView.findViewById<EditText>(R.id.pollOptionInput)

            if (editText.text.isEmpty()) {
                editText.error = getString(R.string.option_error)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(internetCheckRunnable) // Stop checking when fragment is destroyed
        _binding = null
    }

    // Call this from an activity or fragment with CoroutineScope
    fun createPost(message: String, pollQuestion: String? = null, pollOptions: List<String> = emptyList(), multiple: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Call the background function to make the network request
                val response = withContext(Dispatchers.IO) {
                    sendPostRequest(message, pollQuestion, pollOptions, multiple)
                }

                // Handle the response (success or failure)
                if (response != null) {
                    Log.d("CreatePost", "Success: $response")
                    if (response.contains("success") && response.contains("Post created successfully")) {
                        navigateToHomeFragment()


                    } else if (response.contains("error")) {
                        if (response.contains("Rate limit exceeded")) {

                            Toast.makeText(context, "Rate limit exceeded, you can only post once per minute", Toast.LENGTH_LONG).show()

                        }
                    }
                } else {
                    Log.e("CreatePost", "Error: Unable to create post")
                }
            } catch (e: Exception) {
                Log.e("CreatePost", "Request failed", e)
            }
        }
    }

    fun sendPostRequest(message: String, pollQuestion: String?, pollOptions: List<String>, multiple: Int): String? {
        val url = URL("https://wokki20.nl/polled/api/v1/timeline")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            // Set connection parameters
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            // Prepare form data
            val formData = StringBuilder()
            formData.append("message=${URLEncoder.encode(message, "UTF-8")}") // URL encode the message

            // Add poll data if provided
            if (!pollQuestion.isNullOrEmpty() && pollOptions.isNotEmpty()) {
                formData.append(
                    "&poll_question=${
                        URLEncoder.encode(
                            pollQuestion,
                            "UTF-8"
                        )
                    }"
                ) // URL encode the poll question

                // Format poll options as a JSON array
                val pollOptionsJson =
                    URLEncoder.encode("[\"${pollOptions.joinToString("\",\"")}\"]", "UTF-8")
                formData.append("&poll_options=$pollOptionsJson") // Poll options as JSON array
                formData.append("&multiple=$multiple")
            }

            // Write the data to the output stream
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(formData.toString())
            writer.flush()

            // Get the response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()  // It's helpful to log the exception for debugging
            null
        } finally {
            connection.disconnect()
        }
    }




    fun navigateToHomeFragment() {
        val navController = findNavController() // Get NavController
        navController.navigate(R.id.navigation_home)

    }


}