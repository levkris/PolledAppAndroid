package com.wokki.polled.ui.messages

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wokki.polled.R
import com.wokki.polled.UserActivity
import com.wokki.polled.databinding.FragmentMessagesBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

class MessagesFragment : Fragment() {

    private val client = OkHttpClient()
    private lateinit var recyclerView: RecyclerView
    private val contacts = mutableListOf<Contact>()
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_messages, container, false)
        recyclerView = view.findViewById(R.id.contactRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)


        fetchContacts()

        return view
    }

    private fun fetchContacts() {
        val accessToken = getAccessTokenFromStorage()
        val request = Request.Builder()
            .url("https://wokki20.nl/polled/api/v1/contacts")
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    val jsonArray = JSONArray(responseBody)
                    contacts.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        contacts.add(
                            Contact(
                                user_url = obj.getString("user_url"),
                                user_image = obj.getString("user_image"),
                                username = obj.getString("username"),
                                verified = obj.optInt("verified", 0) == 1,
                                last_message = obj.optString("last_message", null)
                            )
                        )
                    }

                    requireActivity().runOnUiThread {
                        recyclerView.adapter = ContactAdapter(
                            contacts,
                            onContactClick = { contact ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wokki20.nl/messages/${contact.user_url}"))
                                startActivity(intent)
                            },
                            onProfileClick = { contact ->

                                val intent = Intent(context, UserActivity::class.java)
                                intent.putExtra("userUrl", contact.user_url)
                                context?.startActivity(intent)
                            }
                        )
                    }
                }
            }
        })
    }

    private fun getAccessTokenFromStorage(): String? {
        val sharedPref = requireActivity().getSharedPreferences("user_prefs", 0)
        return sharedPref.getString("access_token", null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handler.removeCallbacks(internetCheckRunnable) // Stop checking when fragment is destroyed
    }
}


data class Contact(
    val user_url: String,
    val user_image: String,
    val username: String,
    val verified: Boolean,
    val last_message: String?
)
