package com.wokki.polled  // Ensure this matches the package name in your project

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class FullscreenImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)
        supportActionBar?.hide()

        val imageUrl = intent.getStringExtra("image_url")

        val photoView: PhotoView = findViewById(R.id.fullscreen_image)
        val closeButton: ImageButton = findViewById(R.id.close_button)

        // Load the image with Glide into the PhotoView
        Glide.with(this)
            .load(imageUrl)
            .into(photoView)

        closeButton.setOnClickListener {
            finish()  // Close the activity when the button is clicked
        }
    }
}
