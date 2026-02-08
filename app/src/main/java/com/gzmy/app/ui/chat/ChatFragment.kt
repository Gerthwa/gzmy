package com.gzmy.app.ui.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.gzmy.app.GzmyApplication
import com.gzmy.app.R
import com.gzmy.app.data.model.Message
import com.gzmy.app.databinding.FragmentChatBinding
import com.gzmy.app.util.AnimationUtils as Anim
import com.gzmy.app.util.VibrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var chatAdapter: ChatAdapter

    private var coupleCode: String = ""
    private var userId: String = ""
    private var userName: String = ""
    private var partnerName: String = ""
    private var chatListener: ListenerRegistration? = null

    /** ChatFragment açıkken true — FCMService bunu kontrol eder */
    companion object {
        @Volatile
        var isChatScreenActive: Boolean = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        coupleCode = prefs.getString("couple_code", "") ?: ""
        userId = prefs.getString("user_id", "") ?: ""
        userName = prefs.getString("user_name", "") ?: ""

        loadPartnerName()
        setupRecyclerView()
        setupSendButton()
        setupBackButton()
        listenForChatMessages()

        // LocalBroadcast'i dinle (foreground'da sessiz mesaj ekleme için)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            newMessageReceiver,
            IntentFilter(GzmyApplication.ACTION_NEW_MESSAGE)
        )
    }

    override fun onResume() {
        super.onResume()
        isChatScreenActive = true
    }

    override fun onPause() {
        super.onPause()
        isChatScreenActive = false
    }

    private fun loadPartnerName() {
        db.collection("couples").document(coupleCode).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val couple = doc.toObject(com.gzmy.app.data.model.Couple::class.java)
                    couple?.let {
                        partnerName = if (it.partner1Id == userId) it.partner2Name else it.partner1Name
                        if (_binding != null && partnerName.isNotEmpty()) {
                            binding.tvChatPartnerName.text = "💬 $partnerName"
                        }
                    }
                }
            }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(userId)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            setHasFixedSize(true)
            itemAnimator?.changeDuration = 0 // Flicker onleme
            setItemViewCacheSize(20)
        }
    }

    private fun setupSendButton() {
        binding.btnSendChat.setOnClickListener { v ->
            val text = binding.etChatMessage.text?.toString()?.trim() ?: return@setOnClickListener
            if (text.isEmpty()) return@setOnClickListener

            Anim.pressScale(v) {
                VibrationManager.performLightTap(requireContext())
                sendChatMessage(text)
                binding.etChatMessage.text?.clear()
            }
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun sendChatMessage(content: String) {
        if (coupleCode.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val message = Message(
                        id = UUID.randomUUID().toString(),
                        coupleCode = coupleCode,
                        senderId = userId,
                        senderName = userName,
                        type = Message.MessageType.CHAT,
                        content = content,
                        timestamp = Timestamp.now()
                    )
                    db.collection("messages").add(message).await()
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Mesaj gönderme hatası: ${e.message}", e)
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenForChatMessages() {
        if (coupleCode.isEmpty()) return

        // NOT: whereEqualTo("type", "CHAT") kaldırıldı.
        // Çünkü (coupleCode + type + timestamp) composite index'i olmadan
        // Firestore listener FAILED_PRECONDITION hatası verip hiç çalışmıyordu.
        // Şimdi mevcut (coupleCode, timestamp) index'ini kullanıyoruz
        // ve type filtresini Kotlin tarafında yapıyoruz.
        chatListener = db.collection("messages")
            .whereEqualTo("coupleCode", coupleCode)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatFragment", "Listen hatası: ${error.message}")
                    return@addSnapshotListener
                }

                // Kotlin tarafında CHAT mesajlarını filtrele
                val messages = snapshot?.documents
                    ?.mapNotNull { it.toObject(Message::class.java) }
                    ?.filter { it.type == Message.MessageType.CHAT }
                    ?: emptyList()

                chatAdapter.submitList(messages) {
                    // Listeye yeni mesaj eklenince en alta kaydır
                    if (messages.isNotEmpty() && _binding != null) {
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
    }

    /** Foreground'da FCMService'ten gelen broadcast */
    private val newMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            // SnapshotListener zaten mesajları güncelliyor,
            // burada sadece hafif haptic feedback veriyoruz
            ctx?.let { VibrationManager.performLightTap(it) }
        }
    }

    override fun onDestroyView() {
        chatListener?.remove()
        context?.let {
            try { LocalBroadcastManager.getInstance(it).unregisterReceiver(newMessageReceiver) }
            catch (_: Exception) { /* receiver zaten kayıtlı değildi */ }
        }
        _binding = null
        super.onDestroyView()
    }
}
