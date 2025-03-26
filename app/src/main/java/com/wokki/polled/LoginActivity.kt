package com.wokki.polled

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wokki.polled.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            // Redirect to the login website
            val loginIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://polled.wokki20.nl/loginApp"))
            startActivity(loginIntent)
        }

        binding.createAccountButton.setOnClickListener {
            // Redirect to CreateAccountActivity
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
        }

    }
}
