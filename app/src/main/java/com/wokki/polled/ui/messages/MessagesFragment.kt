package com.wokki.polled.ui.messages

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
import androidx.activity.OnBackPressedCallback
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
import org.json.JSONObject
import java.io.IOException

class MessagesFragment : Fragment() {

    private val client = OkHttpClient()
    private lateinit var recyclerView: RecyclerView
    private val contacts = mutableListOf<Contact>()
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var contactAdapter: ContactAdapter
    private var viewingMessages = false
    private var currentUserUrl: String? = null

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
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        val view = binding.root
        recyclerView = view.findViewById(R.id.contactRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        binding.backButton.setOnClickListener {
            fetchContacts() // re-load contacts
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewingMessages) {
                    fetchContacts()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressed() // let system handle it normally
                }
            }
        })

        binding.sendButton.setOnClickListener {
            if (viewingMessages && currentUserUrl != null) {
                val message = binding.messageInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    sendMessage(currentUserUrl!!, message)
                }
            }
        }


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

                    contactAdapter = ContactAdapter(
                        contacts,
                        onContactClick = { contact ->
                            fetchMessages(contact.user_url)
                        },
                        onProfileClick = { contact ->
                            val intent = Intent(context, UserActivity::class.java)
                            intent.putExtra("userUrl", contact.user_url)
                            context?.startActivity(intent)
                        }
                    )

                    requireActivity().runOnUiThread {
                        binding.backButtonContainer.visibility = View.GONE
                        binding.inputContainer.visibility = View.GONE
                        binding.messageInput.text.clear()
                        val layoutParams = binding.contactRecyclerView.layoutParams as ViewGroup.MarginLayoutParams
                        layoutParams.bottomMargin = 50
                        binding.contactRecyclerView.layoutParams = layoutParams
                        recyclerView.adapter = contactAdapter
                        viewingMessages = false
                    }

                }
            }

        })
    }

    private fun fetchMessages(userUrl: String) {
        val accessToken = getAccessTokenFromStorage()
        val request = Request.Builder()
            .url("https://wokki20.nl/polled/api/v1/direct-message?user_url=$userUrl")
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val obj = JSONObject(responseBody)

                        if (obj.getString("status") == "success") {
                            val user = obj.getString("user")
                            val userUrl = obj.getString("user_url")
                            val userImage = obj.getString("user_image")

                            val dms = mutableListOf<Dm>()
                            val dmsArray = obj.getJSONArray("dms")
                            currentUserUrl = userUrl


                            for (i in 0 until dmsArray.length()) {
                                val dmObj = dmsArray.getJSONObject(i)
                                dms.add(
                                    Dm(
                                        id = dmObj.getInt("id"),
                                        message = dmObj.getString("message"),
                                        createdAt = dmObj.getString("created_at"),
                                        status = dmObj.getString("status"),
                                        sender = dmObj.getString("sender"),
                                        senderImage = dmObj.getString("sender_image"),
                                        senderUrl = dmObj.getString("sender_url"),
                                        senderVerified = dmObj.optInt("sender_verified", 0) == 1,
                                        you = dmObj.getBoolean("you")
                                    )
                                )
                            }

                            requireActivity().runOnUiThread {
                                binding.backButtonContainer.visibility = View.VISIBLE
                                binding.inputContainer.visibility = View.VISIBLE
                                val layoutParams = binding.contactRecyclerView.layoutParams as ViewGroup.MarginLayoutParams
                                layoutParams.bottomMargin = 0
                                binding.contactRecyclerView.layoutParams = layoutParams
                                recyclerView.adapter = DmAdapter(
                                    dms = dms,
                                    user = user,
                                    userUrl = userUrl,
                                    userImage = userImage,
                                    context = requireContext()
                                )
                                viewingMessages = true
                                binding.contactRecyclerView.scrollToPosition(dms.size - 1)
                            }



                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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

    private fun sendMessage(userUrl: String, message: String) {
        val accessToken = getAccessTokenFromStorage()
        val requestBody = okhttp3.FormBody.Builder()
            .add("user_url", userUrl)
            .add("message", message)
            .build()

        val request = Request.Builder()
            .url("https://wokki20.nl/polled/api/v1/direct-message")
            .post(requestBody)
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val data = JSONObject(responseBody)
                        if (data.getString("status") == "success") {
                            requireActivity().runOnUiThread {
                                binding.messageInput.text.clear()
                                // Optionally, update the message list (you could re-call fetchMessages)
                                fetchMessages(userUrl)
                            }
                        } else {
                            println("Server error: ${data.optString("error")}")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }


}


data class Contact(
    val user_url: String,
    val user_image: String,
    val username: String,
    val verified: Boolean,
    val last_message: String?
)

data class Dm(
    val id: Int,
    val message: String,
    val createdAt: String,
    val status: String,
    val sender: String,
    val senderImage: String,
    val senderUrl: String,
    val senderVerified: Boolean,
    val you: Boolean
)
