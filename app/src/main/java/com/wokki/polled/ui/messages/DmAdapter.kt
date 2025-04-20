package com.wokki.polled.ui.messages

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.wokki.polled.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DmAdapter(
    private val dms: List<Dm>,
    private val user: String,
    private val userUrl: String,
    private val userImage: String,
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ME = 0
        private const val VIEW_TYPE_OTHER = 1
    }


    // --- VIEW HOLDERS ---
    class DmMeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val message: TextView = itemView.findViewById(R.id.dm_message)
        val createdAt: TextView = itemView.findViewById(R.id.dm_created_at)
        val status: ImageView = itemView.findViewById(R.id.status)
    }

    class DmOtherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profilePicture: ImageView = itemView.findViewById(R.id.dm_profile_picture)
        val username: TextView = itemView.findViewById(R.id.dm_username)
        val message: TextView = itemView.findViewById(R.id.dm_message)
        val createdAt: TextView = itemView.findViewById(R.id.dm_created_at)
        val verifiedIcon: ImageView = itemView.findViewById(R.id.dm_verified_icon)
        val dmHeader: View = itemView.findViewById(R.id.dm_header)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ME -> {
                val view = inflater.inflate(R.layout.item_dm_you, parent, false)
                DmMeViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_dm, parent, false)
                DmOtherViewHolder(view)
            }
        }
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


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val dm = dms[position]

        if (holder is DmMeViewHolder) {
            holder.message.text = dm.message
            holder.createdAt.text = formatDate(dm.createdAt)
            holder.createdAt.visibility = View.VISIBLE

            val currentMessageDate = parseDate(dm.createdAt)
            val nextDm = if (position < dms.size - 1) dms[position + 1] else null
            val isLastMessageOfDayForUser = nextDm?.let {
                val nextDate = parseDate(nextDm.createdAt)
                currentMessageDate.day != nextDate.day || currentMessageDate.month != nextDate.month || currentMessageDate.year != nextDate.year
            } ?: true

            holder.createdAt.visibility = if (isLastMessageOfDayForUser) View.VISIBLE else View.GONE

            holder.status.setImageDrawable(
                if (dm.status == "read") context.getDrawable(R.drawable.ic_done_all)
                else context.getDrawable(R.drawable.ic_check)
            )


        } else if (holder is DmOtherViewHolder) {
            val previousDm = if (position > 0) dms[position - 1] else null
            val isSameUserAsPrevious = previousDm?.sender == dm.sender
            val isSameDateAsPrevious = previousDm?.createdAt?.let {
                val currentDate = parseDate(dm.createdAt)
                val previousDate = parseDate(it)
                currentDate.day == previousDate.day && currentDate.month == previousDate.month && currentDate.year == previousDate.year
            } ?: false

            val currentMessageDate = parseDate(dm.createdAt)
            val nextDm = if (position < dms.size - 1) dms[position + 1] else null
            val isLastMessageOfDayForUser = nextDm?.let {
                val nextDate = parseDate(nextDm.createdAt)
                currentMessageDate.day != nextDate.day || currentMessageDate.month != nextDate.month || currentMessageDate.year != nextDate.year
            } ?: true

            holder.dmHeader.visibility = if (previousDm != null && isSameUserAsPrevious && isSameDateAsPrevious) View.GONE else View.VISIBLE
            holder.createdAt.visibility = if (isLastMessageOfDayForUser) View.VISIBLE else View.GONE

            holder.username.text = dm.sender
            holder.message.text = dm.message
            holder.createdAt.text = formatDate(dm.createdAt)
            holder.verifiedIcon.visibility = if (dm.senderVerified) View.VISIBLE else View.GONE

            val imageUrl = "https://wokki20.nl/polled/api/v1/users/${dm.senderUrl}/${dm.senderImage}"
            Glide.with(holder.itemView)
                .load(imageUrl)
                .centerCrop()
                .circleCrop()
                .into(holder.profilePicture)

            holder.itemView.post {
                holder.message.minWidth = holder.dmHeader.width
            }
        }
        

    }

    override fun getItemCount() = dms.size

    override fun getItemViewType(position: Int): Int {
        return if (dms[position].you) VIEW_TYPE_ME else VIEW_TYPE_OTHER
    }

}
