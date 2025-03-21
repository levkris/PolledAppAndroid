package com.wokki.polled

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FullPostActivity : AppCompatActivity() {

    val context = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_post)

        supportActionBar?.hide()

        // Retrieve the JSON string from the intent
        val postString = intent.getStringExtra("POST_DATA")

        // Convert the string back to a JSONObject
        val timelineItem = if (postString != null) {
            JSONObject(postString)
        } else {
            JSONObject()  // Default to an empty JSONObject if no data is found
        }

        // Get the views from activity_full_post.xml
        val profilePic: ImageView = findViewById(R.id.profilePic)
        val userName: TextView = findViewById(R.id.userName)
        val messageText: TextView = findViewById(R.id.messageText)
        val verifiedIcon: ImageView = findViewById(R.id.verifiedIcon)
        val dateText: TextView = findViewById(R.id.dateText)
        val pollLayout: LinearLayout = findViewById(R.id.pollLayout)
        val buttonContainer: LinearLayout = findViewById(R.id.buttonContainer)

        // Construct the profile picture URL dynamically
        val profilePictureUrl = "https://levgames.nl/polled/api/v1/users/" + timelineItem.optString("maker_url") + "/" + timelineItem.optString("maker_image")

        // Load the profile picture using Glide
        Glide.with(this)
            .load(profilePictureUrl)
            .circleCrop()
            .into(profilePic)

        // Username
        val name = timelineItem.optString("maker")
        userName.text = name

        // Message Text
        val message = timelineItem.optString("message")
        messageText.text = if (message.length > 273) {
            message.substring(0, 300) + "..."
        } else {
            message
        }

        // Handle "Read More" / "Read Less"
        if (message.length > 273) {
            val readMoreButton = Button(this)
            readMoreButton.text = getString(R.string.read_more)
            readMoreButton.setOnClickListener {
                if (messageText.text.toString() == message) {
                    messageText.text = message.substring(0, 300) + "..."
                    readMoreButton.text = getString(R.string.read_more)
                } else {
                    messageText.text = message
                    readMoreButton.text = getString(R.string.read_less)
                }
            }

            buttonContainer.removeAllViews()
            buttonContainer.addView(readMoreButton)
        }

        // Check for verification status
        val isVerified = timelineItem.optInt("verified") == 1
        verifiedIcon.visibility = if (isVerified) View.VISIBLE else View.GONE

        val edited = timelineItem.optInt("edited") == 1

        val date = timelineItem.optString("created_at")
        dateText.text = formatDate(date, edited)
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
}

