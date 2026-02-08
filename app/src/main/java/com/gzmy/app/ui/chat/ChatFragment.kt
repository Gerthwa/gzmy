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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gzmy.app.GzmyApplication
import com.gzmy.app.R
import com.gzmy.app.data.model.Message
import com.gzmy.app.data.repository.MessageRepository
import com.gzmy.app.databinding.FragmentChatBinding
import com.gzmy.app.util.AnimationUtils as Anim
import com.gzmy.app.util.VibrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var repo: MessageRepository

    private var coupleCode: String = ""
    private var userId: String = ""
    private var userName: String = ""
    private var partnerName: String = ""
    private var remoteSyncListener: ListenerRegistration? = null

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

        repo = MessageRepository(requireContext())

        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        coupleCode = prefs.getString("couple_code", "") ?: ""
        userId = prefs.getString("user_id", "") ?: ""
        userName = prefs.getString("user_name", "") ?: ""

        loadPartnerName()
        setupRecyclerView()
        setupSendButton()
        setupBackButton()

        // Room Flow: UI her zaman güncel (offline dahil)
        observeChatMessages()

        // Firestore → Room sync (remote mesajları Room'a yazar)
        if (coupleCode.isNotEmpty()) {
            remoteSyncListener = repo.startRemoteSync(coupleCode)
        }

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
        FirebaseFirestore.getInstance().collection("couples").document(coupleCode).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val couple = doc.toObject(com.gzmy.app.data.model.Couple::class.java)
                    couple?.let {
                        partnerName = if (it.partner1Id == userId) it.partner2Name else it.partner1Name
                        if (_binding != null && partnerName.isNotEmpty()) {
                            binding.tvChatPartnerName.text = partnerName
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
            itemAnimator?.changeDuration = 0
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

    /** Offline-First: Room'a yaz → Firestore'a göndermeyi dene */
    private fun sendChatMessage(content: String) {
        if (coupleCode.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                repo.sendMessage(
                    coupleCode = coupleCode,
                    senderId = userId,
                    senderName = userName,
                    type = Message.MessageType.CHAT,
                    content = content
                )
            } catch (e: Exception) {
                Log.e("ChatFragment", "Mesaj gönderme hatası: ${e.message}", e)
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Room Flow ile chat mesajlarını dinle — offline'da da çalışır */
    private fun observeChatMessages() {
        if (coupleCode.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.getChatMessages(coupleCode).collectLatest { messages ->
                    chatAdapter.submitList(messages) {
                        if (messages.isNotEmpty() && _binding != null) {
                            binding.rvMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
            }
        }
    }

    private val newMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            ctx?.let { VibrationManager.performLightTap(it) }
        }
    }

    override fun onDestroyView() {
        remoteSyncListener?.remove()
        context?.let {
            try { LocalBroadcastManager.getInstance(it).unregisterReceiver(newMessageReceiver) }
            catch (_: Exception) {}
        }
        _binding = null
        super.onDestroyView()
    }
}
