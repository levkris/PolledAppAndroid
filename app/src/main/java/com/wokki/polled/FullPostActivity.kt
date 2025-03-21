package com.wokki.polled

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import org.json.JSONObject

class FullPostActivity : AppCompatActivity() {

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

        // Optionally, set the date text if needed
        val date = timelineItem.optString("date")
        dateText.text = date
    }
}

