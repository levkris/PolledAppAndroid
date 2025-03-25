package com.wokki.polled

import android.content.Context
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
    private var accessToken: String? = null
    private val mainActivity = this@FullPostActivity // Assuming FullPostActivity is started from MainActivity


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_post)

        // Now initialize the views after setContentView
        profilePic = findViewById(R.id.profilePic)
        userName = findViewById(R.id.userName)
        messageText = findViewById(R.id.messageText)
        verifiedIcon = findViewById(R.id.verifiedIcon)
        dateText = findViewById(R.id.dateText)
        pollLayout = findViewById(R.id.pollLayout)
        buttonContainer = findViewById(R.id.buttonContainer)
        pollQuestionText = findViewById(R.id.pollQuestionText)
        pollOptionsContainer = findViewById(R.id.pollOptionsContainer)
        val markwon = Markwon.create(this)

        supportActionBar?.hide()

        sharedPreferences = applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        accessToken = sharedPreferences.getString("access_token", null)

        // Retrieve the JSON string from the intent
        val postString = intent.getStringExtra("POST_DATA")

        // Convert the string back to a JSONObject
        val timelineItem = if (postString != null) {
            JSONObject(postString)
        } else {
            JSONObject()  // Default to an empty JSONObject if no data is found
        }

        val canChange = timelineItem.optBoolean("can_change")

        var translated = false


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

        val comments = timelineItem.optJSONArray("comments")
        if (comments != null) {
            displayComments(comments)
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


        // Clear existing options in the pollOptionsContainer
        pollOptionsContainer.removeAllViews()

        // Add each poll option dynamically
        val options = poll.optJSONArray("options")
        for (i in 0 until options.length()) {
            val option = options.getJSONObject(i)
            val optionText = option.optString("text")
            val percentage = option.optInt("percentage")
            val votes = option.optInt("votes")

            // Inflate the option item and find the TextViews inside it
            val optionView = LayoutInflater.from(context).inflate(R.layout.poll_option_item, pollOptionsContainer, false)
            val optionTextView = optionView.findViewById<TextView>(R.id.pollOptionText)  // For the option text
            val percentageTextView = optionView.findViewById<TextView>(R.id.pollPercentage)  // For the percentage
            val progressBarBackground = optionView.findViewById<LinearLayout>(R.id.progressBarBackground)

            // Decode the HTML-encoded string back to normal
            val decodedText = Html.fromHtml(optionText).toString()

            optionTextView.text = decodedText
            if (translated) {

                translateMessageInFullPost(decodedText) { translatedText ->
                    // Set the translated text to the messageText TextView
                    optionTextView.text = translatedText
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
                voteOption(poll.optInt("id"), option.optInt("id"), poll.optInt("total_votes"), votes, poll.optInt("multiple"), poll.optBoolean("voted"))
            }

            // Add the option to the container
            pollOptionsContainer.addView(optionView)
        }
    }


    private fun displayComments(comments: JSONArray, nested: Boolean = false, parentContainer: LinearLayout? = null) {
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

            // Set the comment message text
            commentTextView.text = commentMessage

            // Load profile picture using Glide
            val profilePictureUrl = "https://wokki20.nl/polled/api/v1/users/${commentObject.optString("maker_url")}/${commentObject.optString("maker_image")}"
            Glide.with(context).load(profilePictureUrl).circleCrop().into(commentProfilePic)

            // Set username and other details
            commentUserName.text = commentObject.optString("maker")
            val edited = commentObject.optInt("edited") == 1
            commentDateText.text = formatDate(commentObject.optString("created_at"), edited)

            // Show verification badge if verified
            commentVerifiedIcon.visibility = if (commentObject.optInt("verified") == 1) View.VISIBLE else View.GONE

            // **Ensure nestedCommentsContainer is visible**
            nestedCommentsContainer.visibility = View.VISIBLE

            // **Indent nested comments for better UI**
            if (nested) {
                val params = commentView.layoutParams as ViewGroup.MarginLayoutParams
                params.marginStart = 50  // Indent nested comments
                commentView.layoutParams = params
            }

            // **Add commentView to parent container**
            commentsContainer.addView(commentView)

            // Process nested comments
            val nestedComments = commentObject.optJSONArray("comments")
            if (nestedComments != null && nestedComments.length() > 0) {
                Log.d("CommentsDebug", "Found nested comments for: $commentMessage")

                // **Force nested container to be visible**
                nestedCommentsContainer.visibility = View.VISIBLE

                // **Pass nestedCommentsContainer as the new parentContainer**
                displayComments(nestedComments, nested = true, parentContainer = nestedCommentsContainer)
            } else {
                Log.d("CommentsDebug", "No nested comments for: $commentMessage")
            }
        }
    }





    fun voteOption(pollId: Int, optionId: Int, totalVotes: Int, votes: Int, multiple: Int, voted: Boolean) {
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
                    val jsonResponse = JSONObject(data)

                    if (jsonResponse.getString("status") == "success") {




                    } else {
                        println("Error: ${jsonResponse.getString("error")}")
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


    private fun translateMessageInFullPost(message: String, callback: (String) -> Unit) {
        // No need to get the activity since this is an Activity
        val mainActivity = this as? MainActivity
        mainActivity?.let {
            it.translateMessage(message) { translatedText ->
                callback(translatedText)  // Return the translated text via the callback
            }
        }
    }




}

