package com.wokki.polled.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.wokki.polled.LoginActivity
import com.wokki.polled.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private lateinit var bannedLayout: LinearLayout
    private lateinit var bannedMessageTextView: TextView
    private lateinit var bannedReasonTextView: TextView
    private lateinit var bannedUsernameTextView: TextView
    private lateinit var appealBanButton: Button

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bannedLayout = binding.bannedLayout
        bannedMessageTextView = binding.bannedMessage
        bannedReasonTextView = binding.bannedReason
        bannedUsernameTextView = binding.bannedUsername
        appealBanButton = binding.appealBanButton

        // Initially hide the banned layout
        bannedLayout.visibility = View.GONE
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the binding and set up the views
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Set up the profile ViewModel
        val profileViewModelFactory = ProfileViewModelFactory(requireContext())
        val profileViewModel = ViewModelProvider(this, profileViewModelFactory).get(ProfileViewModel::class.java)

        // Observe profile data and image URL LiveData
        profileViewModel.profileData.observe(viewLifecycleOwner) { profileInfo ->
            binding.textProfile.text = profileInfo
        }

        profileViewModel.imageUrl.observe(viewLifecycleOwner) { imageUrl ->
            Glide.with(this)
                .load(imageUrl)
                .circleCrop()  // Circular image
                .into(binding.profileImage)
        }

        // Fetch profile data
        profileViewModel.fetchProfileData()

        // Set up logout button click listener
        binding.logoutButton.setOnClickListener {
            val sharedPreferences = context?.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences?.edit()
            editor?.remove("access_token")
            editor?.putBoolean("is_logged_in", false)
            editor?.apply()

            navigateToLogin()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showBannedPage(username: String, reason: String) {
        if (_binding == null) return  // Avoid null pointer exception

        // Safely access views via the binding reference
        binding.bannedLayout.visibility = View.VISIBLE

        // Format the banned reason text
        val formattedReason = formatTextWithStyles(reason)

        // Enable the appeal ban button and set up click listener
        appealBanButton.isEnabled = true
        appealBanButton.requestFocus()
        appealBanButton.setOnClickListener {
            openMailTo(username)
        }

        // Set formatted reason and username on the views
        binding.bannedReason.text = formattedReason
        binding.bannedUsername.text = "Username: $username"
    }


    // Function to format text with bold/italic styles
    private fun formatTextWithStyles(text: String): CharSequence {
        var formattedText = text
        formattedText = formattedText.replace("\n", "<br>")
        formattedText = formattedText.replace("\\*\\*(.*?)\\*\\*".toRegex()) { match ->
            "<b>${match.groupValues[1]}</b>"
        }
        formattedText = formattedText.replace("([*_])(.*?)([*_])".toRegex()) { match ->
            "<i>${match.groupValues[2]}</i>"
        }
        return Html.fromHtml(formattedText, Html.FROM_HTML_MODE_LEGACY)
    }

    // Function to open a mailto link for ban appeal
    private fun openMailTo(username: String) {
        val emailUri = Uri.parse("mailto:info@levgames.nl?subject=Ban Appeal for $username&body=I would like to appeal my ban.")
        val emailIntent = Intent(Intent.ACTION_VIEW, emailUri)
        ContextCompat.startActivity(requireContext(), emailIntent, null)
    }

    // Function to navigate to the login activity
    private fun navigateToLogin() {
        val intent = Intent(context, LoginActivity::class.java)
        startActivity(intent)
        activity?.finish()  // Finish the current activity
    }
}

