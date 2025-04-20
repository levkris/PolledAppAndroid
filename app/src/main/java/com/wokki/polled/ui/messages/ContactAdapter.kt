package com.wokki.polled.ui.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.wokki.polled.R

class ContactAdapter(
    private val contacts: List<Contact>,
    private val onContactClick: (Contact) -> Unit,
    private val onProfileClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profilePicture: ImageView = itemView.findViewById(R.id.profile_picture)
        val username: TextView = itemView.findViewById(R.id.username)
        val lastMessage: TextView = itemView.findViewById(R.id.last_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.username.text = contact.username

        holder.lastMessage.text = if (contact.last_message == null || contact.last_message == "null") {
            getString(holder.itemView.context, R.string.no_messages)
        } else {
            contact.last_message
        }

        if (contact.verified) {
            holder.itemView.findViewById<ImageView>(R.id.verifiedIcon).visibility = View.VISIBLE
        } else {
            holder.itemView.findViewById<ImageView>(R.id.verifiedIcon).visibility = View.GONE
        }

        val imageUrl = "https://wokki20.nl/polled/api/v1/users/${contact.user_url}/${contact.user_image}"
        Glide.with(holder.itemView).load(imageUrl).centerInside().transform(RoundedCorners(28)).into(holder.profilePicture)

        holder.itemView.setOnClickListener { onContactClick(contact) }
        holder.profilePicture.setOnClickListener { onProfileClick(contact) }
        holder.username.setOnClickListener { onProfileClick(contact) }
    }

    override fun getItemCount() = contacts.size
}
