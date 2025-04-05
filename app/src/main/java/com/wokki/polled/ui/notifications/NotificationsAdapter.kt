package com.wokki.polled.ui.notifications

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.wokki.polled.FullPostActivity
import com.wokki.polled.R
import com.wokki.polled.UserActivity
import com.wokki.polled.databinding.NotificationItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NotificationsAdapter(private val notifications: List<Notification>, private val context: Context) :
    RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {
    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = NotificationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification)
    }



    override fun getItemCount(): Int = notifications.size

    inner class NotificationViewHolder(private val binding: NotificationItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notification) {
            // Bind the data to the view
            val title = getCorrectTitle(notification.type, notification.by_user)
            val notificationTime = formatDate(notification.created_at)

            val commentMessage = notification.comment_message?.replace("%20", " ") ?: context.getString(R.string.deleted_comment)
            val postMessage = notification.post_message?.replace("%20", " ") ?: context.getString(R.string.deleted_message)

            val correctMessage = if (notification.type == "comment") {
                commentMessage
            } else {
                postMessage
            }

            val message = getCorrectMessage(notification.type, correctMessage, notification.by_user)

            val notificationImage = getCorrectNotificationImage(notification.type)

            binding.notificationMessage.text = message

            binding.notificationTitle.text = title

            binding.notificationTime.text = notificationTime
            binding.notificationIcon.setImageResource(notificationImage)

            val postId = notification.target_message_id


            sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            accessToken = sharedPreferences.getString("access_token", null)


            binding.root.setOnClickListener {
                val action = getCorrectAction(notification.type)

                // Handle the click action based on the notification type
                if (action == "user") {
                    val intent = Intent(context, UserActivity::class.java)
                    intent.putExtra("userUrl", notification.by_user_url)
                    context.startActivity(intent)
                } else if (action == "post") {
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
                                    Toast.makeText(context, "This post has been deleted", Toast.LENGTH_LONG).show()

                                }
                            } catch (e: Exception) {
                                Log.e("Error", "Error parsing response: ${e.message}")
                            }
                        }
                    }
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

    fun getCorrectTitle(type: String, byUser: String): String {
        if (type == "follow") {
            return context.getString(R.string.follow_notification_title, byUser)
        } else if (type == "unfollow") {
            return context.getString(R.string.unfollow_notification_title, byUser)
        } else if (type == "like") {
            return context.getString(R.string.like_notification_title)
        } else if (type == "comment") {
            return context.getString(R.string.commented_notification_title, byUser)
        } else if (type == "mention") {
            return context.getString(R.string.mention_notification_title, byUser)
        } else if (type == "vote") {
            return context.getString(R.string.voted_notification_title, byUser)
        } else if (type == "new_post") {
            return context.getString(R.string.new_post_notification_title)
        }
        return ""
    }

    fun getCorrectMessage(type: String, message: String?, byUser: String): String {
        return when (type) {
            "follow", "unfollow" -> {
                context.getString(R.string.follow_notification_message, byUser)
            }
            "like" -> {
                context.getString(R.string.like_notification_message, byUser)
            }
            "comment" -> {
                if (message == context.getString(R.string.deleted_comment)) message ?: "" else "\"$message\""
            }
            "mention" -> {
                context.getString(R.string.mention_notification_message, byUser)
            }
            "vote" -> {
                context.getString(R.string.voted_notification_message, byUser)
            }
            "new_post" -> {
                if (message == context.getString(R.string.deleted_message)) {
                    "$byUser $message"
                } else {
                    context.getString(R.string.posted_notification_message, byUser) + "\"$message\""
                }
            }
            else -> message ?: ""
        }
    }

    fun getCorrectNotificationImage(type: String): Int {
        return when (type) {
            "follow" -> R.drawable.ic_notification_follow
            "unfollow" -> R.drawable.ic_notification_unfollow
            "like" -> R.drawable.ic_notification_like
            "comment" -> R.drawable.ic_notification_comment
            "mention" -> R.drawable.ic_notification_mention
            "vote" -> R.drawable.ic_notification_vote
            "new_post" -> R.drawable.ic_notification_newpost
            else -> R.drawable.ic_notification_follow
        }
    }

    fun getCorrectAction(type: String): String {
        return when (type) {
            "follow" -> "user"
            "unfollow" -> "user"
            else -> "post"
        }

    }

}
