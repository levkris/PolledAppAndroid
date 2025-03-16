package com.wokki.polled.ui.profile

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.wokki.polled.LoginActivity
import com.wokki.polled.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

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


    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Use the factory to create the ViewModel with context
        val profileViewModelFactory = ProfileViewModelFactory(requireContext())
        val profileViewModel = ViewModelProvider(this, profileViewModelFactory).get(ProfileViewModel::class.java)

        handler.post(internetCheckRunnable)


        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textProfile
        val imageView: ImageView = binding.profileImage
        val logoutButton: Button = binding.logoutButton // Assuming you have a Button with id 'logoutButton' in your layout

        // Observe the profile data LiveData
        profileViewModel.profileData.observe(viewLifecycleOwner) { profileInfo ->
            textView.text = profileInfo
        }

        // Observe the image URL LiveData
        profileViewModel.imageUrl.observe(viewLifecycleOwner) { imageUrl ->
            // Use Glide to load the image into the ImageView with circle crop transformation
            Glide.with(this)
                .load(imageUrl)
                .circleCrop()  // This applies the circular crop
                .into(imageView)
        }


        // Fetch profile data
        profileViewModel.fetchProfileData()

        // Set the logout button click listener
        logoutButton.setOnClickListener {
            // Get access token from SharedPreferences
            val sharedPreferences = context?.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences?.edit()

            // Remove the access token and set is_logged_in to false
            editor?.remove("access_token") // Adjust this key if needed
            editor?.putBoolean("is_logged_in", false)
            editor?.apply()

            navigateToLogin()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(internetCheckRunnable) // Stop checking when fragment is destroyed
        _binding = null
    }

    // Function to navigate to the LoginActivity
    private fun navigateToLogin() {
        val intent = Intent(context, LoginActivity::class.java)
        startActivity(intent)
        activity?.finish() // Finish the current activity to remove it from the back stack
    }
}
