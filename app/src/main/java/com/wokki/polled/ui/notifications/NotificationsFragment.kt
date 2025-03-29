package com.wokki.polled.ui.notifications

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wokki.polled.R
import com.wokki.polled.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class Notification(
    val by_user: String,
    val parent_message_id: Int?,
    val target_message_id: Int,
    val type: String,
    val created_at: String,
    val is_read: Int,
    val by_user_url: String,
    val comment_message: String?,
    val post_message: String?
)

data class NotificationResponse(
    val status: String,
    val notifications: List<Notification>
)

class NotificationsFragment : Fragment(R.layout.fragment_notifications) {
    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationsAdapter: NotificationsAdapter
    private val notificationsList = mutableListOf<Notification>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the adapter with an empty list
        notificationsAdapter = NotificationsAdapter(notificationsList, requireContext())

        // Set up the RecyclerView
        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.notificationsRecyclerView.adapter = notificationsAdapter


        sharedPreferences = this.requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        accessToken = sharedPreferences.getString("access_token", null)


        // Fetch notifications from the API
        fetchNotifications()
    }

    private fun fetchNotifications() {
        CoroutineScope(Dispatchers.IO).launch {

            val client = OkHttpClient()

            // Create the request and add the Authorization header if the token is available
            val requestBuilder = Request.Builder()
                .url("https://wokki20.nl/polled/api/v1/notifications")

            if (accessToken != null) {
                requestBuilder.addHeader("Authorization", "Bearer $accessToken")
            }

            val request = requestBuilder.build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        val notificationResponse = Gson().fromJson<NotificationResponse>(
                            responseBody, object : TypeToken<NotificationResponse>() {}.type
                        )

                        // Check if the notifications list is null or empty
                        val notifications = notificationResponse.notifications
                        if (notifications != null) {
                            withContext(Dispatchers.Main) {
                                notificationsList.clear()
                                notificationsList.addAll(notifications) // Add only valid notifications
                                notificationsAdapter.notifyDataSetChanged()
                            }
                        } else {
                            // Handle the case where notifications are null
                            withContext(Dispatchers.Main) {
                                println("Error: No notifications found.")
                            }
                        }
                    } else {
                        // Handle empty response body
                        withContext(Dispatchers.Main) {
                            println("Error: Empty response body.")
                        }
                    }
                } else {
                    // Handle the case when the response is not successful
                    withContext(Dispatchers.Main) {
                        println("Error: Failed to fetch notifications. Response code: ${response.code}")
                    }
                }
            } catch (e: IOException) {
                // Handle network error
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    println("Error: Network failure.")
                }
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clean up the binding to avoid memory leaks
    }
}
