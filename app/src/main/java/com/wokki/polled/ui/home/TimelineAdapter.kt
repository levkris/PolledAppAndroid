package com.wokki.polled.ui.home

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.wokki.polled.FullPostActivity
import com.wokki.polled.MainActivity
import com.wokki.polled.R
import com.wokki.polled.UserActivity
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimelineAdapter(private val context: Context, private val resultLauncher: ActivityResultLauncher<Intent>): ListAdapter<JSONObject, TimelineAdapter.TimelineViewHolder>(TimelineDiffCallback()) {

    private val sharedPreferences = context.getSharedPreferences("user_prefs", MODE_PRIVATE)
    private val accessToken = sharedPreferences.getString("access_token", null)
    private val autoTranslate = sharedPreferences.getBoolean("auto_translate", false)


    // DiffUtil callback for efficient updates
    class TimelineDiffCallback : DiffUtil.ItemCallback<JSONObject>() {
        override fun areItemsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
            return oldItem.optString("id") == newItem.optString("id")
        }

        override fun areContentsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
            return oldItem.toString() == newItem.toString()
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline, parent, false)
        return TimelineViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val timelineItem = getItem(position)
        holder.bind(timelineItem)
    }



    inner class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profilePic: ImageView = itemView.findViewById(R.id.profilePic)
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val verifiedIcon: ImageView = itemView.findViewById(R.id.verifiedIcon)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val pollLayout: LinearLayout = itemView.findViewById(R.id.pollLayout)  // Assuming this is a LinearLayout
        private val pollQuestionText: TextView = itemView.findViewById(R.id.pollQuestionText)  // TextView for poll question
        private val pollOptionsContainer: LinearLayout = itemView.findViewById(R.id.pollOptionsContainer)  // LinearLayout for options
        private val likeButton: ImageButton = itemView.findViewById(R.id.like)
        private val likeCount: TextView = itemView.findViewById(R.id.likeCount)
        private val visibilityText = itemView.findViewById<TextView>(R.id.visibility)
        private val postImage = itemView.findViewById<ImageView>(R.id.postImage)

        val markwon = Markwon.builder(context).build()

        fun bind(timelineItem: JSONObject) {

            val canChange = timelineItem.optBoolean("can_change")

            var translated = false


            userName.setOnClickListener {
                val intent = Intent(context, UserActivity::class.java)
                intent.putExtra("userUrl", timelineItem.optString("maker_url"))
                context.startActivity(intent)
            }

            // get the image if it exists from the assets object
            val assets = timelineItem.optJSONObject("assets")
            if (assets != null) {
                val image = assets.optString("image")

                // Load image into postImage with a little border radius
                if (image !== null) {
                    val url = "https://wokki20.nl/polled/api/v1/$image"

                    postImage.visibility = View.VISIBLE

                    // Set a fixed width and height for the image to prevent it from being too large
                    Glide.with(itemView.context)
                        .load(url)
                        .centerCrop() // Center crop to fit the image nicely
                        .apply(RequestOptions().transform(RoundedCorners(26))) // Adds border radius of 26px
                        .override(500, 500) // Set a max size for the image (adjust as needed)
                        .into(postImage)
                } else {
                    postImage.visibility = View.GONE
                }
            }







            val visibility = timelineItem.optString("visibility")
            if (visibility == "public") {
                visibilityText.visibility = View.GONE
            } else {
                visibilityText.visibility = View.VISIBLE
                if (visibility == "private") {
                    visibilityText.text = context.getString(R.string.private_post)
                    // set the icon
                    visibilityText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_private, 0)
                } else if (visibility == "unlisted") {
                    visibilityText.text = context.getString(R.string.unlisted_post)
                    // set the icon
                    visibilityText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_unlisted, 0)
                } else if (visibility == "followers") {
                    visibilityText.text = context.getString(R.string.followers_post)
                    // set the icon
                    visibilityText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_followers, 0)
                } else if (visibility == "friends") {
                    visibilityText.text = context.getString(R.string.friends_post)
                    // set the icon
                    visibilityText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_friends, 0)
                }
            }

            var likes = timelineItem.optInt("likes")
            var liked = timelineItem.optBoolean("liked")
            val messageId = timelineItem.optInt("id")

            if (liked) {
                likeButton.setImageResource(R.drawable.liked)
            } else {
                likeButton.setImageResource(R.drawable.like)
            }
            likeCount.text = likes.toString()

            likeButton.setOnClickListener {
                if (liked) {
                    likes--
                    likeCount.text = likes.toString()
                    liked = false
                    likeButton.setImageResource(R.drawable.like)
                    updateLike(messageId)
                    timelineItem.put("liked", false)
                    timelineItem.put("likes", likes)
                } else {
                    likes++
                    likeCount.text = likes.toString()
                    liked = true
                    likeButton.setImageResource(R.drawable.liked)
                    updateLike(messageId)
                    timelineItem.put("liked", true)
                    timelineItem.put("likes", likes)
                }
            }

            itemView.setOnClickListener {
                val intent = Intent(context, FullPostActivity::class.java) // Use context passed to the adapter
                intent.putExtra("POST_DATA", timelineItem.toString())
                resultLauncher.launch(intent)  // Launch the activity using resultLauncher
            }

            messageText.setOnClickListener {
                val intent = Intent(context, FullPostActivity::class.java) // Use context passed to the adapter
                intent.putExtra("POST_DATA", timelineItem.toString())
                resultLauncher.launch(intent)  // Launch the activity using resultLauncher
            }


            val profilePictureUrl = "https://wokki20.nl/polled/api/v1/users/" + timelineItem.optString("maker_url") + "/" + timelineItem.optString("maker_image")

            // Load the profile picture using Glide
            Glide.with(itemView.context)
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

                if (autoTranslate) {
                    translateMessageInAdapter(truncatedMessage) { translatedText ->
                        // Set the translated text to the messageText TextView
                        markwon.setMarkdown(messageText, translatedText)
                    }
                }

                // Add a "Read more" button dynamically
                val readMoreButton = Button(itemView.context)
                readMoreButton.text = context.getString(R.string.read_more)

                readMoreButton.setBackgroundColor(Color.TRANSPARENT);
                readMoreButton.setTextColor(itemView.context.getColor(R.color.main))
                readMoreButton.textSize = 14f
                readMoreButton.setPadding(0, 0, 0, 0)
                readMoreButton.setTypeface(readMoreButton.typeface, Typeface.BOLD)

                // Set the listener for the "Read more" button
                readMoreButton.setOnClickListener {
                    markwon.setMarkdown(messageText, message)
                    if (autoTranslate) {
                        translateMessageInAdapter(message) { translatedText ->
                            // Set the translated text to the messageText TextView
                            markwon.setMarkdown(messageText, translatedText)
                        }
                    }

                    readMoreButton.text = context.getString(R.string.read_less)
                    readMoreButton.setOnClickListener {
                        markwon.setMarkdown(messageText, truncatedMessage)
                        if (autoTranslate) {
                            translateMessageInAdapter(truncatedMessage) { translatedText ->
                                // Set the translated text to the messageText TextView
                                markwon.setMarkdown(messageText, translatedText)
                            }
                        }
                        readMoreButton.text = context.getString(R.string.read_more)
                    }

                }

                // Add the "Read more" button to the layout (ensure it has space in your layout)
                val buttonContainer = itemView.findViewById<LinearLayout>(R.id.buttonContainer)  // Make sure you have a container in your layout
                buttonContainer.removeAllViews()
                buttonContainer.addView(readMoreButton)
            } else {
                markwon.setMarkdown(messageText, message)

                if (autoTranslate) {
                    translateMessageInAdapter(message) { translatedText ->
                        // Set the translated text to the messageText TextView
                        markwon.setMarkdown(messageText, translatedText)
                    }
                }

            }

            itemView.setOnLongClickListener {
                showPostOptions(timelineItem, canChange, translated)
                true // Return true to indicate the event is handled
            }
            messageText.setOnLongClickListener {
                showPostOptions(timelineItem, canChange, translated)
                true // Return true to indicate the event is handled
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
                displayPoll(poll, autoTranslate)  // Call displayPoll to handle the poll section
            } else {
                pollLayout.visibility = View.GONE  // Hide poll section if no poll data exists
            }
        }

        fun updateLike(messageId: Int) {
            val url = "https://wokki20.nl/polled/api/v1/like"

            // Create FormData
            val formBody = FormBody.Builder()
                .add("message_id", messageId.toString())
                .build()

            // Get the access token from local storage
            val accessToken = "Bearer $accessToken" // Implement this to fetch the token

            // Create the request
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("Authorization", accessToken)
                .build()

            // Create OkHttpClient to make the request
            val client = OkHttpClient()

            // Make the request
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        val jsonObject = JSONObject(responseData)

                        if (jsonObject.getString("status") == "success") {
                            // Success
                            println(jsonObject)
                        } else {
                            // Failure
                            throw IOException("Error: ${jsonObject.getString("error")}\n${jsonObject.getString("message")}")
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    // Handle failure
                    e.printStackTrace()
                }
            })
        }

        private fun showPostOptions(post: JSONObject, canChange: Boolean, translated: Boolean) {
            val bottomSheetDialog = BottomSheetDialog(itemView.context)


            val view = LayoutInflater.from(itemView.context).inflate(R.layout.bottom_sheet_post_options, null)

            val editPost = view.findViewById<TextView>(R.id.editPost)
            val deletePost = view.findViewById<TextView>(R.id.deletePost)
            val sharePost = view.findViewById<TextView>(R.id.sharePost)
            val reportPost = view.findViewById<TextView>(R.id.reportPost)
            val translatePost = view.findViewById<TextView>(R.id.translatePost)

            if (translated) {
                translatePost.text = context.getString(R.string.see_original)
            } else {
                translatePost.text = context.getString(R.string.translate)
            }

            if (!canChange) {
                editPost.visibility = View.GONE
                deletePost.visibility = View.GONE
            }

            // Add listener to detect when the sheet is opened
            bottomSheetDialog.setOnShowListener {
                itemView.animate()
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(250)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }

            // Add listener to detect when the sheet is dismissed
            bottomSheetDialog.setOnDismissListener {
                itemView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            // Set click listeners
            editPost.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            deletePost.setOnClickListener {
                deletePost(post.optInt("id"), itemView)
                bottomSheetDialog.dismiss()
            }

            sharePost.setOnClickListener {
                sharePost(post.optInt("id"))
                bottomSheetDialog.dismiss()
            }

            reportPost.setOnClickListener {
                reportPost(context, post.optInt("id"))
                bottomSheetDialog.dismiss()
            }

            translatePost.setOnClickListener {
                if (translated) {
                    seeOriginalPost(post.optString("message"), post, canChange)
                } else {
                    translatePostAsOption(post.optString("message"), post, canChange)
                }
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.setContentView(view)
            bottomSheetDialog.window?.setDimAmount(0.0f) // Adjust opacity (0.0 = no dim, 1.0 = full black)

            bottomSheetDialog.show()
        }

        fun reportPost(context: Context, id: Int) {
            // Create a builder for the alert dialog
            val builder = AlertDialog.Builder(context)

            builder.setTitle(getString(context, R.string.report_post))
            builder.setMessage(getString(context, R.string.report_post_message))

            // Create a custom LinearLayout to hold the EditText
            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(32, 32, 32, 32)

            // Create the EditText with a custom style
            val input = EditText(context)
            input.setBackgroundResource(R.drawable.report_input) // Custom background for the EditText
            input.setPadding(16, 16, 16, 16)
            input.setHint(getString(context, R.string.report_reason))
            input.setHintTextColor(ContextCompat.getColor(context, android.R.color.darker_gray)) // Custom hint color
            input.setTextSize(16f)

            layout.addView(input)

            // Set the custom background for the dialog
            val customBackground: Drawable = ContextCompat.getDrawable(context, R.drawable.report_post_bg)!!
            builder.setView(layout) // Add the custom layout to the dialog

            // Set positive and negative buttons (Submit and Cancel)
            builder.setPositiveButton("Submit") { dialog, which ->
                val reason = input.text.toString()

                if (reason.isNotEmpty()) {
                    sendReportRequest(id, reason)
                } else {
                    // Handle the case where the reason is empty
                    println("Reason cannot be empty")
                }
            }

            builder.setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss() // Close the dialog when Cancel is pressed
            }

            // Creating the dialog
            val dialog = builder.create()

            // Apply the custom background to the dialog's window (optional)
            dialog.window?.setBackgroundDrawable(customBackground)

            // Show the dialog
            dialog.show()
        }

        fun sendReportRequest(id: Int, reason: String) {
            val client = OkHttpClient()

            val formBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("message_id", id.toString())
                .addFormDataPart("reason", reason)
                .build()

            val request = Request.Builder()
                .url("https://wokki20.nl/polled/api/v1/report_post")
                .post(formBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        // Handle the response here
                        showPopup(context, getString(context, R.string.report_sent),  getString(context, R.string.report_sent_message))
                    } else {
                        println("Error: ${response.code}")
                        showPopup(context, getString(context, R.string.report_not_sent), getString(context, R.string.report_sent_error))

                    }
                } catch (e: IOException) {
                    println("Request failed: $e")
                }
            }
        }

        suspend fun showPopup(context: Context, title: String, message: String) {
            // Switch to the main thread for UI operations
            withContext(Dispatchers.Main) {
                // Create a builder for the alert dialog
                val builder = AlertDialog.Builder(context)

                builder.setTitle(title)
                builder.setMessage(message)

                // Create a custom LinearLayout to hold the EditText
                val layout = LinearLayout(context)
                layout.orientation = LinearLayout.VERTICAL
                layout.setPadding(32, 32, 32, 32)

                // Set the custom background for the dialog
                val customBackground: Drawable = ContextCompat.getDrawable(context, R.drawable.report_post_bg)!!
                builder.setView(layout) // Add the custom layout to the dialog

                // Set positive and negative buttons (Submit and Cancel)
                builder.setPositiveButton("Okay") { dialog, which ->
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

        fun seeOriginalPost(message: String, timeLineItem: JSONObject, canChange: Boolean) {
            markwon.setMarkdown(messageText, message)

            // Check if message is longer than 273 characters
            if (message.length > 273) {
                // Truncate the message and add "..."
                val truncatedMessage = message.substring(0, 300) + "..."

                markwon.setMarkdown(messageText, truncatedMessage)


                // Add a "Read more" button dynamically
                val readMoreButton = Button(itemView.context)
                readMoreButton.text = context.getString(R.string.read_more)

                readMoreButton.setBackgroundColor(Color.TRANSPARENT);
                readMoreButton.setTextColor(itemView.context.getColor(R.color.main))
                readMoreButton.textSize = 14f
                readMoreButton.setPadding(0, 0, 0, 0)
                readMoreButton.setTypeface(readMoreButton.typeface, Typeface.BOLD)

                // Set the listener for the "Read more" button
                readMoreButton.setOnClickListener {
                    // When the button is clicked, show the full message

                    markwon.setMarkdown(messageText, message)


                    // Optionally, you can hide the "Read more" button after it's clicked
                    // Or make the button text change to "Read less"
                    readMoreButton.text = context.getString(R.string.read_less)
                    readMoreButton.setOnClickListener {
                        markwon.setMarkdown(messageText, truncatedMessage)
                        readMoreButton.text = context.getString(R.string.read_more)
                    }

                }

                // Add the "Read more" button to the layout (ensure it has space in your layout)
                val buttonContainer = itemView.findViewById<LinearLayout>(R.id.buttonContainer)  // Make sure you have a container in your layout
                buttonContainer.removeAllViews()
                buttonContainer.addView(readMoreButton)
            } else {

                markwon.setMarkdown(messageText, message)


            }


            itemView.setOnLongClickListener {
                showPostOptions(timeLineItem, canChange, false)
                true // Return true to indicate the event is handled
            }

            messageText.setOnLongClickListener {
                showPostOptions(timeLineItem, canChange, false)
                true // Return true to indicate the event is handled
            }

            // Poll Handling
            val poll = timeLineItem.optJSONObject("poll")
            if (poll != null) {
                displayPoll(poll, false)  // Call displayPoll to handle the poll section
            } else {
                pollLayout.visibility = View.GONE  // Hide poll section if no poll data exists
            }

        }

        fun translatePostAsOption(message: String, timeLineItem: JSONObject, canChange: Boolean) {
            // Check if message is longer than 273 characters
            if (message.length > 273) {
                // Truncate the message and add "..."
                val truncatedMessage = message.substring(0, 300) + "..."

                markwon.setMarkdown(messageText, truncatedMessage)
                // Set the truncated message
                translateMessageInAdapter(truncatedMessage) { translatedText ->
                    // Set the translated text to the messageText TextView
                    markwon.setMarkdown(messageText, translatedText)
                }

                // Add a "Read more" button dynamically
                val readMoreButton = Button(itemView.context)
                readMoreButton.text = context.getString(R.string.read_more)

                readMoreButton.setBackgroundColor(Color.TRANSPARENT);
                readMoreButton.setTextColor(itemView.context.getColor(R.color.main))
                readMoreButton.textSize = 14f
                readMoreButton.setPadding(0, 0, 0, 0)
                readMoreButton.setTypeface(readMoreButton.typeface, Typeface.BOLD)

                // Set the listener for the "Read more" button
                readMoreButton.setOnClickListener {
                    // When the button is clicked, show the full message
                    translateMessageInAdapter(message) { translatedText ->
                        // Set the translated text to the messageText TextView
                        markwon.setMarkdown(messageText, translatedText)
                    }

                    // Optionally, you can hide the "Read more" button after it's clicked
                    // Or make the button text change to "Read less"
                    readMoreButton.text = context.getString(R.string.read_less)
                    readMoreButton.setOnClickListener {
                        translateMessageInAdapter(truncatedMessage) { translatedText ->
                            // Set the translated text to the messageText TextView
                            markwon.setMarkdown(messageText, translatedText)
                        }
                        readMoreButton.text = context.getString(R.string.read_more)
                    }

                }

                // Add the "Read more" button to the layout (ensure it has space in your layout)
                val buttonContainer = itemView.findViewById<LinearLayout>(R.id.buttonContainer)  // Make sure you have a container in your layout
                buttonContainer.removeAllViews()
                buttonContainer.addView(readMoreButton)
            } else {
                markwon.setMarkdown(messageText, message)
                translateMessageInAdapter(message) { translatedText ->
                    // Set the translated text to the messageText TextView
                    markwon.setMarkdown(messageText, translatedText)
                }

            }

            itemView.setOnLongClickListener {
                showPostOptions(timeLineItem, canChange, true)
                true // Return true to indicate the event is handled
            }

            messageText.setOnLongClickListener {
                showPostOptions(timeLineItem, canChange, true)
                true // Return true to indicate the event is handled
            }

            // Poll Handling
            val poll = timeLineItem.optJSONObject("poll")
            if (poll != null) {
                displayPoll(poll, true)  // Call displayPoll to handle the poll section
            } else {
                pollLayout.visibility = View.GONE  // Hide poll section if no poll data exists
            }
        }

        private fun sharePost(postId: Int) {
            val shareText = "Polled\nLook at this cool post I found on Polled, https://polled.wokki20.nl/?post=$postId"
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share post via"))
        }

        private fun deletePost(postId: Int, itemView: View) {
            val client = OkHttpClient()

            // Prepare the form data
            val formData = FormBody.Builder()
                .add("id", postId.toString())
                .build()

            // Create the request
            val request = Request.Builder()
                .url("https://wokki20.nl/polled/api/v1/timeline")
                .delete(formData)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            // Execute the request asynchronously
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Handle failure
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        // Handle successful response
                        val jsonResponse = JSONObject(responseData)
                        if (jsonResponse.optString("status") == "success") {
                            // Success
                            println("Post deleted successfully: $jsonResponse")

                            // Post to main thread to update the UI
                            itemView.post {
                                // Get the position of the current item
                                val position = adapterPosition

                                // If the position is valid, remove the item from the list
                                if (position != RecyclerView.NO_POSITION) {
                                    // Notify the adapter to remove the item
                                    val updatedList = currentList.toMutableList()
                                    updatedList.removeAt(position) // Remove item at position
                                    submitList(updatedList) // Submit the updated list to the adapter
                                } else {
                                    Log.d("TimelineAdapter", "Invalid position: $position")
                                }
                            }
                        } else {
                            // Failure
                            val error = jsonResponse.optString("error")
                            val message = jsonResponse.optString("message")
                            throw IOException("Error: $error\n$message")
                        }
                    } else {
                        // Handle non-200 response
                        println("Request failed with code: ${response.code}")
                    }
                }
            })
        }


        private fun displayPoll(poll: JSONObject, translated: Boolean) {
            pollLayout.visibility = View.VISIBLE  // Show the poll layout

            // Set the poll question
            pollQuestionText.text = poll.optString("question")

            // Set the decoded text to the option text view
            if (translated) {
                translateMessageInAdapter(poll.optString("question")) { translatedText ->
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
                val optionView = LayoutInflater.from(itemView.context).inflate(R.layout.poll_option_item, pollOptionsContainer, false)
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
                    translateMessageInAdapter(decodedText) { translatedText ->
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





        private fun translateMessageInAdapter(message: String, callback: (String) -> Unit) {
            if (context is MainActivity<*>) {
                context.translateMessage(message) { translatedText ->
                    callback(translatedText)  // Return the translated text via the callback
                }
            }
        }








    }


}
