package com.gzmy.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gzmy.app.R
import com.gzmy.app.data.model.Message
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String
) : ListAdapter<Message, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun formatTime(message: Message): String {
            return try {
                timeFormat.format(message.timestamp.toDate())
            } catch (e: Exception) {
                ""
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)

        fun bind(message: Message) {
            tvContent.text = message.content
            tvTime.text = formatTime(message)
        }
    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tvSenderName)
        private val tvContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)

        fun bind(message: Message) {
            tvSender.text = message.senderName
            tvContent.text = message.content
            tvTime.text = formatTime(message)
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}
