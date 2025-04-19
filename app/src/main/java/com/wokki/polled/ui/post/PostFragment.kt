package com.wokki.polled.ui.post

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.wokki.polled.R
import com.wokki.polled.RefreshAccessToken
import com.wokki.polled.databinding.FragmentPostBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

class PostFragment : Fragment() {
    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null
    private var imageUri: Uri? = null
    // Constants for permissions and request codes
    private val REQUEST_PERMISSION = 100
    private val PICK_IMAGE_REQUEST = 101
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>


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
        val postVisibilityDropdown = binding.postVisibility
        val imageButton = binding.addImageButton

        // Inside onCreateView or onCreate method
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    // Set the image URI to the ImageView
                    binding.addImageView.setImageURI(it)
                    imageUri = it

                    // Extract the file name from the URI
                    val fileName = getFileNameFromUri(it)

                    // Set the button text to the file name
                    imageButton.text = fileName ?: "No file selected"
                }
            }
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted, open the image picker
                openImagePicker()
            } else {
                // Permission denied, show a message or handle accordingly
                Toast.makeText(requireContext(), "Storage permission is required", Toast.LENGTH_SHORT).show()
            }
        }

        imageButton.setOnClickListener {
            when {
                // For Android 13 and above
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.READ_MEDIA_IMAGES
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        openImagePicker()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    }
                }
                else -> {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        openImagePicker()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }
        }




        val visibilityOptions = arrayOf("Public", "Private", "Followers Only", "Friends Only", "Unlisted")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, visibilityOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        postVisibilityDropdown.adapter = adapter

        var visibilityOption = "public"
// Set a listener to update visibilityOption when an item is selected
        postVisibilityDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val visibilityOptionDropdown = when (position) {
                    0 -> "public"
                    1 -> "private"
                    2 -> "followers"
                    3 -> "friends"
                    4 -> "unlisted"
                    else -> "public" // Default value
                }
                visibilityOption = visibilityOptionDropdown
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                val visibilityOptionDropdown = "public"
                visibilityOption = visibilityOptionDropdown
            }

        }


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
                createPost(message, pollQuestion, pollOptions, multipleChoice, visibilityOption, imageUri)

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

    // Function to extract the file name from the URI
    fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        val cursor = requireContext().contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            if (columnIndex != -1) {
                if (it.moveToFirst()) {
                    fileName = it.getString(columnIndex)
                }
            } else {
                // Handle the case where the column does not exist
                fileName = uri.lastPathSegment
            }
        }
        return fileName
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

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
        println("Image picker launched")
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission granted, open the image picker
            openImagePicker()
        } else {
            // Permission denied, show a toast message
            Toast.makeText(requireContext(), "Storage permission is required", Toast.LENGTH_SHORT).show()
        }
    }




    // Handle the result from image selection
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data // Get the URI of the selected image
            if (imageUri != null) {
                // You can display the image in an ImageView
                binding.addImageView.setImageURI(imageUri)
            }
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

    fun createPost(message: String, pollQuestion: String? = null, pollOptions: List<String> = emptyList(), multiple: Int, visibility: String, imageUri: Uri?) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Call the background function to make the network request
                val response = withContext(Dispatchers.IO) {
                    sendPostRequest(message, pollQuestion, pollOptions, multiple, visibility, imageUri)
                }

                // Handle the response (success or failure)
                if (response != null) {
                    if (response.contains("success") && response.contains("Post created successfully")) {
                        navigateToHomeFragment()
                    } else if (response.contains("error")) {
                        if (response.contains("Rate limit exceeded")) {
                            Toast.makeText(context, getString(R.string.rate_limit_exceeded), Toast.LENGTH_LONG).show()
                        } else if (response.contains("Invalid or expired access token")) {
                            val refreshAccessToken = context?.let { RefreshAccessToken(it) }

                            lifecycleScope.launch {
                                if (refreshAccessToken != null) {
                                    refreshAccessToken.refreshTokenIfNeeded()
                                    Toast.makeText(context, getString(R.string.something_went_wrong), Toast.LENGTH_LONG).show()
                                }
                            }
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


    fun getRealPathFromURI(contentUri: Uri): String? {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context?.contentResolver?.query(contentUri, proj, null, null, null)
        cursor?.moveToFirst()
        val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        val filePath = cursor?.getString(columnIndex ?: 0)
        cursor?.close()
        return filePath
    }


    fun sendPostRequest(
        message: String,
        pollQuestion: String?,
        pollOptions: List<String>,
        multiple: Int,
        visibility: String,
        imageUri: Uri?
    ): String? {
        val url = URL("https://wokki20.nl/polled/api/v1/timeline")
        val boundary = "Boundary-${System.currentTimeMillis()}"
        val connection = url.openConnection() as HttpURLConnection

        return try {
            // Set connection parameters for multipart/form-data
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true

            // Create an output stream to send data
            val outputStream = DataOutputStream(connection.outputStream)

            // Write the text data (message, poll options, etc.)
            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"message\"\r\n\r\n")
            outputStream.writeBytes(message + "\r\n")

            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"visibility\"\r\n\r\n")
            outputStream.writeBytes(visibility + "\r\n")

            if (!pollQuestion.isNullOrEmpty() && pollOptions.isNotEmpty()) {
                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"poll_question\"\r\n\r\n")
                outputStream.writeBytes(pollQuestion + "\r\n")

                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"poll_options\"\r\n\r\n")
                outputStream.writeBytes("[\"${pollOptions.joinToString("\",\"")}\"]\r\n")

                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"multiple\"\r\n\r\n")
                outputStream.writeBytes(multiple.toString() + "\r\n")
            }

            // If an image is selected, upload it
            imageUri?.let {
                val filePath = getRealPathFromURI(it)
                val file = File(filePath)


                if (file.exists()) {
                    val fileInputStream = FileInputStream(file)
                    val fileName = file.name
                    val fileType = URLConnection.guessContentTypeFromName(file.name)

                    // Write the image part of the multipart form
                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"images[]\"; filename=\"$fileName\"\r\n")
                    outputStream.writeBytes("Content-Type: $fileType\r\n\r\n")

                    // Write the image bytes to the output stream
                    fileInputStream.copyTo(outputStream)

                    outputStream.writeBytes("\r\n")
                    fileInputStream.close()
                } else {
                    Log.e("Image Upload", "File does not exist")
                }
            }

            // End of the multipart data
            outputStream.writeBytes("--$boundary--\r\n")
            outputStream.flush()

            // Get the response from the server
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e("Image Upload", "Failed to upload image. Response code: $responseCode")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()  // Log the exception for debugging
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