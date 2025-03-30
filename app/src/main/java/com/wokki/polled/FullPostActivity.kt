package com.wokki.polled

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FullPostActivity : AppCompatActivity() {

    val context = this

    // Declare the views at the top but initialize them later
    private lateinit var profilePic: ImageView
    private lateinit var userName: TextView
    private lateinit var messageText: TextView
    private lateinit var verifiedIcon: ImageView
    private lateinit var dateText: TextView
    private lateinit var pollLayout: LinearLayout
    private lateinit var buttonContainer: LinearLayout
    private lateinit var pollQuestionText: TextView
    private lateinit var pollOptionsContainer: LinearLayout
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var backButton: ImageButton
    private var accessToken: String? = null
    private val mainActivity = this@FullPostActivity // Assuming FullPostActivity is started from MainActivity
    private lateinit var replyInput: EditText
    private lateinit var replyButton: ImageButton
    private var replyingComment = false
    private var replyingToCommentId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_post)

        // Initialize the views
        profilePic = findViewById(R.id.profilePic)
        userName = findViewById(R.id.userName)
        messageText = findViewById(R.id.messageText)
        verifiedIcon = findViewById(R.id.verifiedIcon)
        dateText = findViewById(R.id.dateText)
        pollLayout = findViewById(R.id.pollLayout)
        buttonContainer = findViewById(R.id.buttonContainer)
        pollQuestionText = findViewById(R.id.pollQuestionText)
        pollOptionsContainer = findViewById(R.id.pollOptionsContainer)
        backButton = findViewById(R.id.backButton)
        replyInput = findViewById(R.id.replyInput)
        replyButton = findViewById(R.id.replyButton)

        val markwon = Markwon.create(this)

        supportActionBar?.hide()

        sharedPreferences = applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        accessToken = sharedPreferences.getString("access_token", null)

        // Retrieve the JSON string from the intent
        val postString = intent.getStringExtra("POST_DATA")

        val itemView = findViewById<LinearLayout>(R.id.linearLayout)

        // Convert the string back to a JSONObject
        val timelineItem = if (postString != null) {
            JSONObject(postString)
        } else {
            JSONObject()  // Default to an empty JSONObject if no data is found
        }

        val id = timelineItem.optInt("id")

        backButton.setOnClickListener {
            onBackPressed()
        }

        // Username
        val name = timelineItem.optString("maker")
        userName.text = name

        // Construct the profile picture URL dynamically
        val profilePictureUrl = "https://wokki20.nl/polled/api/v1/users/" + timelineItem.optString("maker_url") + "/" + timelineItem.optString("maker_image")
        Glide.with(this)
            .load(profilePictureUrl)
            .circleCrop()
            .into(profilePic)

        // Message Text
        val message = timelineItem.optString("message")

        val truncatedMessage = if (message.length > 273) {
            message.substring(0, 273) + "..."
        } else {
            message
        }

        userName.setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            intent.putExtra("userUrl", timelineItem.optString("maker_url"))
            startActivity(intent)
        }

        // Initially show truncated message
        markwon.setMarkdown(messageText, truncatedMessage)

        var translated = false
        translateMessageInFullPost(truncatedMessage) { translatedText ->
            markwon.setMarkdown(messageText, translatedText)
            translated = true
        }

        // Add a "Read more" button dynamically if the message was truncated
        if (message.length > 273) {
            val readMoreButton = Button(this)
            readMoreButton.text = getString(R.string.read_more)
            readMoreButton.setBackgroundColor(Color.TRANSPARENT)
            readMoreButton.setTextColor(resources.getColor(R.color.main))
            readMoreButton.textSize = 14f
            readMoreButton.setTypeface(readMoreButton.typeface, Typeface.BOLD)
            readMoreButton.setPadding(0, 0, 0, 0)

            // Set the listener for the "Read more" button
            readMoreButton.setOnClickListener {
                markwon.setMarkdown(messageText, message)
                translateMessageInFullPost(message) { translatedText ->
                    markwon.setMarkdown(messageText, translatedText)
                }

                // Change button text to "Read less"
                readMoreButton.text = getString(R.string.read_less)
                readMoreButton.setOnClickListener {
                    markwon.setMarkdown(messageText, truncatedMessage)
                    readMoreButton.text = getString(R.string.read_more)
                }
            }

            // Add the "Read more" button to the layout
            buttonContainer.removeAllViews()
            buttonContainer.addView(readMoreButton)
        }

        // Date Handling
        val date = timelineItem.optString("created_at")
        val edited = timelineItem.optInt("edited") == 1
        dateText.text = formatDate(date, edited)

        // Check for verification status
        val isVerified = timelineItem.optInt("verified") == 1
        verifiedIcon.visibility = if (isVerified) View.VISIBLE else View.GONE

        // Poll Handling
        val poll = timelineItem.optJSONObject("poll")
        if (poll != null) {
            displayPoll(poll, true)  // Call displayPoll to handle the poll section
        } else {
            pollLayout.visibility = View.GONE  // Hide poll section if no poll data exists
        }

        // Handle Comments
        val comments = timelineItem.optJSONArray("comments")
        if (comments != null) {
            displayComments(comments, parentMessageId = id)
        }

        // Handle the reply input and button for posting comments
        itemView.setOnClickListener {
            // Reset reply context when tapping outside the comment area
            replyInput.requestFocus()
            replyInput.setHint(getString(R.string.reply_hint))
            replyingToCommentId = null
        }

        // Reply to comment
        replyButton.setOnClickListener {
            if (replyInput.text.toString().isNotEmpty() && replyingToCommentId == null) {
                addComment(replyInput.text.toString(), id, id)
                replyInput.text.clear()
                replyingToCommentId = null  // Reset after posting
            }
        }
    }


    private fun addComment(message: String, id: Int, parentID: Int) {
        // Use coroutines to perform the network request on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://wokki20.nl/polled/api/v1/timeline")
                val urlConnection = url.openConnection() as HttpURLConnection

                urlConnection.requestMethod = "POST"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")
                urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                urlConnection.doOutput = true

                val formData = "message=$message&message_id=$id"
                urlConnection.outputStream.use { outputStream ->
                    outputStream.write(formData.toByteArray())
                }

                val responseCode = urlConnection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP error! Response code: $responseCode")
                }

                val response = urlConnection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)

                withContext(Dispatchers.Main) {
                    updatePost(parentID)

                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FullPostActivity, "Request failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePost(id: Int) {
        val url = "https://wokki20.nl/polled/api/v1/timeline?limit=1&offset_id=$id"

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



                        val markwon = Markwon.create(context)

                        // Retrieve the JSON string from the intent
                        val postString = firstPost.toString()


                        // Convert the string back to a JSONObject
                        val timelineItem = if (postString != null) {
                            JSONObject(postString)
                        } else {
                            JSONObject()  // Default to an empty JSONObject if no data is found
                        }

                        val canChange = timelineItem.optBoolean("can_change")

                        var translated = false

                        backButton.setOnClickListener {
                            onBackPressed()
                        }

                        val id = timelineItem.optInt("id")

                        replyButton.setOnClickListener {
                            addComment(replyInput.text.toString(), id, id)
                        }

                        // Construct the profile picture URL dynamically
                        val profilePictureUrl = "https://wokki20.nl/polled/api/v1/users/" + timelineItem.optString("maker_url") + "/" + timelineItem.optString("maker_image")

                        // Load the profile picture using Glide
                        Glide.with(context)
                            .load(profilePictureUrl)
                            .circleCrop()
                            .into(profilePic)


                        // Username
                        val name = timelineItem.optString("maker")
                        userName.text = name

                        // Message Text
                        val message = timelineItem.optString("message")

                        // Check if message is longer than 273 characters
                        if (message.length > 273) {
                            // Truncate the message and add "..."
                            val truncatedMessage = message.substring(0, 300) + "..."

                            markwon.setMarkdown(messageText, truncatedMessage)

                            translateMessageInFullPost(truncatedMessage) { translatedText ->
                                // Set the translated text to the messageText TextView
                                markwon.setMarkdown(messageText, translatedText)
                                translated = true
                            }



                            // Add a "Read more" button dynamically
                            val readMoreButton = Button(context)
                            readMoreButton.text = context.getString(R.string.read_more)

                            readMoreButton.setBackgroundColor(Color.TRANSPARENT)
                            readMoreButton.setTextColor(context.getColor(R.color.main))
                            readMoreButton.textSize = 14f
                            readMoreButton.setPadding(0, 0, 0, 0)
                            readMoreButton.setTypeface(readMoreButton.typeface, Typeface.BOLD)

                            // Set the listener for the "Read more" button
                            readMoreButton.setOnClickListener {
                                markwon.setMarkdown(messageText, message)

                                translateMessageInFullPost(message) { translatedText ->
                                    // Set the translated text to the messageText TextView
                                    markwon.setMarkdown(messageText, translatedText)

                                }



                                // Optionally, you can hide the "Read more" button after it's clicked
                                // Or make the button text change to "Read less"
                                readMoreButton.text = context.getString(R.string.read_less)
                                readMoreButton.setOnClickListener {
                                    messageText.text = truncatedMessage
                                    readMoreButton.text = context.getString(R.string.read_more)
                                }
                            }

                            // Add the "Read more" button to the layout (ensure it has space in your layout)
                            val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)
                            buttonContainer.removeAllViews()
                            buttonContainer.addView(readMoreButton)
                        } else {
                            markwon.setMarkdown(messageText, message)


                            translateMessageInFullPost(message) { translatedText ->
                                // Set the translated text to the messageText TextView
                                markwon.setMarkdown(messageText, translatedText)
                                translated = true
                            }


                        }



                        val edited = timelineItem.optInt("edited") == 1

                        val date = timelineItem.optString("created_at")
                        dateText.text = formatDate(date, edited)

                        // Check for verification status
                        val isVerified = timelineItem.optInt("verified") == 1
                        verifiedIcon.visibility = if (isVerified) View.VISIBLE else View.GONE

                        // Poll Handling
                        val poll = timelineItem.optJSONObject("poll")
                        if (poll != null) {
                            displayPoll(poll, true)  // Call displayPoll to handle the poll section
                        } else {
                            pollLayout.visibility = View.GONE  // Hide poll section if no poll data exists
                        }

                        val commentsContainer = findViewById<LinearLayout>(R.id.commentsContainer)

                        // remove previous comments
                        commentsContainer.removeAllViews()

                        val comments = timelineItem.optJSONArray("comments")
                        if (comments != null) {
                            displayComments(comments, parentMessageId = id)
                        }
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

    fun formatDate(dateString: String, isEdited: Boolean = false): CharSequence {
        val now = System.currentTimeMillis()
        val eventDate = parseDate(dateString)

        // Calculate the difference in milliseconds
        val diffInMillis = now - eventDate.time
        val diffInSeconds = (diffInMillis / 1000).toInt()
        val diffInMinutes = diffInSeconds / 60
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24
        val diffInWeeks = diffInDays / 7

        // Check if the event year is the same as the current year
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val eventYear = Calendar.getInstance().apply { time = eventDate }.get(Calendar.YEAR)

        // Create the "Edited" text if necessary
        val editedText = if (isEdited) "${context.getString(R.string.edited)} " else ""

        // Create the full text
        var resultText = when {
            diffInSeconds < 60 -> "$diffInSeconds ${context.getString(R.string.sec)}"
            diffInMinutes < 60 -> "$diffInMinutes ${context.getString(R.string.min)}"
            diffInHours < 2 -> "1 ${context.getString(R.string.hour)}"
            diffInHours < 24 -> "$diffInHours ${context.getString(R.string.hours)}"
            diffInDays < 2 -> "1 ${context.getString(R.string.day)}"
            diffInDays < 7 -> "$diffInDays ${context.getString(R.string.days)}"
            eventYear == currentYear -> {
                // If the event is in the same year, format as dd/MM HH:mm
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("Europe/Amsterdam")
                dateFormat.format(eventDate)
            }
            else -> {
                // More than a week ago and not in the same year, return full date with year
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("Europe/Amsterdam")
                dateFormat.format(eventDate)
            }
        }

        resultText = editedText + resultText

        // Only apply bold if "Edited" is present and the length of resultText is sufficient
        if (editedText.isNotEmpty()) {
            val spannableString = SpannableString(resultText)
            val editedStart = 0
            val editedEnd = editedText.length
            if (editedEnd <= resultText.length) {
                spannableString.setSpan(
                    StyleSpan(Typeface.BOLD), // Make the text bold
                    editedStart,
                    editedEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return spannableString
        }

        return resultText
    }

    fun parseDate(dateString: String): Date {
        // Use the correct date format
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Europe/Amsterdam") // Set time zone to Amsterdam
        return try {
            sdf.parse(dateString) ?: Date() // Default to current date if parsing fails
        } catch (e: Exception) {
            // Handle the error if parsing fails
            e.printStackTrace()
            Date() // Return the current date if parsing fails
        }
    }

    private fun displayPoll(poll: JSONObject, translated: Boolean) {
        pollLayout.visibility = View.VISIBLE  // Show the poll layout

        // Set the poll question
        pollQuestionText.text = poll.optString("question")

        // Set the decoded text to the option text view
        if (translated) {

            translateMessageInFullPost(poll.optString("question")) { translatedText ->
                // Set the translated text to the messageText TextView
                pollQuestionText.text = translatedText
            }

        }

        val multiple = poll.optInt("multiple_choice")

        // Clear existing options in the pollOptionsContainer
        pollOptionsContainer.removeAllViews()

        // Add each poll option dynamically
        val options = poll.optJSONArray("options")
        for (i in 0 until options.length()) {
            val option = options.getJSONObject(i)
            val optionText = option.optString("text")
            val percentage = option.optInt("percentage")
            val votes = option.optInt("votes")
            var voted = option.optBoolean("voted")

            // Inflate the option item and find the TextViews inside it
            val optionView = LayoutInflater.from(context).inflate(R.layout.poll_option_item, pollOptionsContainer, false)
            val optionTextView = optionView.findViewById<TextView>(R.id.pollOptionText)  // For the option text
            val percentageTextView = optionView.findViewById<TextView>(R.id.pollPercentage)  // For the percentage
            val progressBarBackground = optionView.findViewById<LinearLayout>(R.id.progressBarBackground)
            val optionButton = optionView.findViewById<RadioButton>(R.id.pollOptionButton)
            val optionCheckbox = optionView.findViewById<CheckBox>(R.id.pollOptionCheckbox)
            var correctOptionButton = "option"

            optionView.tag = option.optInt("id")

            // Decode the HTML-encoded string back to normal
            val decodedText = Html.fromHtml(optionText).toString()

            optionTextView.text = decodedText
            if (translated) {
                // Set the decoded text to the option text view
                translateMessageInFullPost(decodedText) { translatedText ->
                    // Set the translated text to the messageText TextView
                    optionTextView.text = translatedText
                }
            }

            if (multiple == 1) {
                optionCheckbox.visibility = View.VISIBLE
                optionButton.visibility = View.GONE
                correctOptionButton = "checkbox"
            }


            if (voted) {
                if (correctOptionButton == "checkbox") {
                    optionCheckbox.isChecked = true
                } else {
                    optionButton.isChecked = true
                }
            }

            // Set the percentage text to the percentage view
            percentageTextView.text = "$percentage%"

            // Use post to ensure the layout is measured before setting the progress bar width
            optionView.post {
                // Get the width of the parent container (pollOptionsContainer)
                val progressBarWidth = (percentage / 100f) * pollOptionsContainer.width

                // Set the progress bar width directly
                val params = progressBarBackground.layoutParams
                params.width = progressBarWidth.toInt() // Set the width dynamically
                progressBarBackground.layoutParams = params
            }

            optionView.setOnClickListener {
                voteOption(poll.optInt("id"), option.optInt("id"), poll.optInt("total_votes"), votes, poll.optInt("multiple"), voted, pollLayout)

                if (correctOptionButton == "checkbox") {
                    if (optionCheckbox.isChecked) {
                        voted = false
                        optionCheckbox.isChecked = false
                    } else {
                        voted = true
                        optionCheckbox.isChecked = true
                    }
                } else {
                    if (optionButton.isChecked) {
                        voted = false
                        optionButton.isChecked = false
                    } else {
                        voted = true
                        removeOtherCheckedButtons(pollLayout)
                        optionButton.isChecked = true
                    }
                }
            }

            // Add the option to the container
            pollOptionsContainer.addView(optionView)
        }
    }

    fun removeOtherCheckedButtons(pollLayout: LinearLayout) {
        val pollOptionsContainer = pollLayout.findViewById<LinearLayout>(R.id.pollOptionsContainer)

        for (i in 0 until pollOptionsContainer.childCount) {
            val optionView = pollOptionsContainer.getChildAt(i)
            val optionButton = optionView.findViewById<RadioButton>(R.id.pollOptionButton)
            optionButton.isChecked = false
        }
    }

    private fun displayComments(comments: JSONArray, nested: Boolean = false, parentContainer: LinearLayout? = null, parentMessageId: Int) {
        val commentsContainer = parentContainer ?: findViewById<LinearLayout>(R.id.commentsContainer)

        for (i in 0 until comments.length()) {
            val commentObject = comments.getJSONObject(i)
            val commentMessage = commentObject.getString("message")

            Log.d("CommentsDebug", "Displaying comment: $commentMessage")

            // Inflate the item_comment layout
            val commentView = LayoutInflater.from(this).inflate(R.layout.item_comment, commentsContainer, false)

            // Find elements inside the inflated layout
            val commentTextView = commentView.findViewById<TextView>(R.id.commentMessageText)
            val commentProfilePic = commentView.findViewById<ImageView>(R.id.commentProfilePic)
            val commentUserName = commentView.findViewById<TextView>(R.id.commentUserName)
            val commentDateText = commentView.findViewById<TextView>(R.id.commentDateText)
            val commentVerifiedIcon = commentView.findViewById<ImageView>(R.id.commentVerifiedIcon)
            val nestedCommentsContainer = commentView.findViewById<LinearLayout>(R.id.nestedCommentsContainer)

            val markwon = Markwon.create(this)
            var translated = false

            commentTextView.text = commentMessage

            translateMessageInFullPost(commentMessage) { translatedText ->
                // Set the translated text to the messageText TextView
                markwon.setMarkdown(commentTextView, translatedText)
                translated = true
            }

            // Set onClickListener for the commentView to set the reply context.
            commentView.setOnClickListener {
                replyInput.requestFocus()
                replyInput.setHint(getString(R.string.commentReply, commentObject.optString("maker")))
                replyingToCommentId = commentObject.optInt("id") // Store the ID of the comment being replied to
                replyingComment = true
            }

            // Use the global replyButton listener to post replies to the correct comment
            replyButton.setOnClickListener {
                if (replyInput.text.toString().isNotEmpty() && replyingToCommentId != null) {
                    addComment(replyInput.text.toString(), replyingToCommentId!!, parentMessageId)
                    replyInput.text.clear()
                    replyingComment = false
                    replyingToCommentId = null  // Reset the reply context
                    replyInput.setHint(getString(R.string.reply_hint))
                }
            }

            // Load profile picture using Glide
            val profilePictureUrl = "https://wokki20.nl/polled/api/v1/users/${commentObject.optString("maker_url")}/${commentObject.optString("maker_image")}"
            Glide.with(context).load(profilePictureUrl).circleCrop().into(commentProfilePic)

            // Set username and other details
            commentUserName.text = commentObject.optString("maker")
            val edited = commentObject.optInt("edited") == 1
            commentDateText.text = formatDate(commentObject.optString("created_at"), edited)

            commentUserName.setOnClickListener {
                val intent = Intent(this, UserActivity::class.java)
                intent.putExtra("userUrl", commentObject.optString("maker_url"))
                startActivity(intent)
            }

            // Show verification badge if verified
            commentVerifiedIcon.visibility = if (commentObject.optInt("verified") == 1) View.VISIBLE else View.GONE

            // Ensure nestedCommentsContainer is visible
            nestedCommentsContainer.visibility = View.VISIBLE

            // Indent nested comments for better UI
            if (nested) {
                val params = commentView.layoutParams as ViewGroup.MarginLayoutParams
                params.marginStart = 50  // Indent nested comments
                commentView.layoutParams = params
            }

            // Add commentView to parent container
            commentsContainer.addView(commentView)

            // Process nested comments
            val nestedComments = commentObject.optJSONArray("comments")
            if (nestedComments != null && nestedComments.length() > 0) {
                Log.d("CommentsDebug", "Found nested comments for: $commentMessage")

                // Force nested container to be visible
                nestedCommentsContainer.visibility = View.VISIBLE

                // Pass nestedCommentsContainer as the new parentContainer
                displayComments(nestedComments, nested = true, parentContainer = nestedCommentsContainer, parentMessageId)
            } else {
                Log.d("CommentsDebug", "No nested comments for: $commentMessage")
            }
        }
    }


    fun voteOption(
        pollId: Int,
        optionId: Int,
        totalVotes: Int,
        votes: Int,
        multiple: Int,
        voted: Boolean,
        pollLayout: LinearLayout
    ) {
        val formBody = FormBody.Builder()
            .add("poll_id", pollId.toString())
            .add("option_id", optionId.toString())
            .add("method", "vote")
            .build()

        val request = Request.Builder()
            .url("https://wokki20.nl/polled/api/v1/vote")
            .post(formBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val data = response.body?.string()
                    try {
                        val jsonResponse = JSONObject(data)

                        val status = jsonResponse.optString("status", "")
                        if (status == "success") {
                            val newTotalVotes = jsonResponse.optInt("new_total_votes")
                            val newOptionVotes = jsonResponse.optJSONArray("new_option_votes")

                            // go through each option and update the votes
                            if (newOptionVotes != null) {
                                for (i in 0 until newOptionVotes.length()) {
                                    val option = newOptionVotes.getJSONObject(i)
                                    val optionId = option.optInt("id")
                                    val newVotes = option.optInt("votes")
                                    pollLayout.post {
                                        updatePollUI(pollOptionsContainer, newTotalVotes, newVotes, optionId)
                                    }
                                }
                            }
                        } else {
                            println("Error: Response status not successful.")
                        }
                    } catch (e: JSONException) {
                        println("Error parsing JSON response: ${e.message}")
                    }
                } else {
                    println("Network response was not ok, status: ${response.code}")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                println("Request failed: ${e.message}")
            }
        })
    }



    fun updatePollUI(pollOptionsContainer: LinearLayout, newTotalVotes: Int, votes: Int, optionId: Int) {
        // Loop through each poll option and update the percentage
        for (i in 0 until pollOptionsContainer.childCount) {
            val optionView = pollOptionsContainer.getChildAt(i)

            val optionIdTag = optionView.tag as? Int ?: continue
            val percentageTextView = optionView.findViewById<TextView>(R.id.pollPercentage)
            val progressBarBackground = optionView.findViewById<LinearLayout>(R.id.progressBarBackground)

            if (optionIdTag == optionId) {
                val newPercentage = (votes.toFloat() / newTotalVotes.toFloat() * 100).toInt()
                percentageTextView.text = "$newPercentage%"
                val progressBarWidth = (newPercentage / 100f) * pollOptionsContainer.width

                // Animate width change
                val currentWidth = progressBarBackground.layoutParams.width
                val animator = ValueAnimator.ofInt(currentWidth, progressBarWidth.toInt())
                animator.addUpdateListener { valueAnimator ->
                    val animatedValue = valueAnimator.animatedValue as Int
                    val params = progressBarBackground.layoutParams
                    params.width = animatedValue
                    progressBarBackground.layoutParams = params
                }
                animator.duration = 500
                animator.start()
            }
        }
    }


    private fun translateMessageInFullPost(message: String, callback: (String) -> Unit) {

        val mainActivity = MainActivity()

        lifecycleScope.launch {
            mainActivity.translateMessage(message) { translatedText ->
                callback(translatedText)
            }
        }
    }




}

