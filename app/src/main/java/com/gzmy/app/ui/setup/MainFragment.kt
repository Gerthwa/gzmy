package com.gzmy.app.ui.setup

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.gzmy.app.GzmyApplication
import com.gzmy.app.R
import com.gzmy.app.data.model.Message
import com.gzmy.app.databinding.FragmentMainBinding
import com.gzmy.app.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var vibrator: Vibrator
    private var coupleCode: String = ""
    private var userId: String = ""
    private var userName: String = ""
    private var partnerName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        coupleCode = prefs.getString("couple_code", "") ?: ""
        userId = prefs.getString("user_id", "") ?: ""
        userName = prefs.getString("user_name", "") ?: ""

        Log.d("Gzmy", "MainFragment loaded - coupleCode: '$coupleCode', userId: '$userId', userName: '$userName'")

        // Eğer değerler boşsa setup'a geri dön
        if (coupleCode.isEmpty() || userId.isEmpty()) {
            Log.e("Gzmy", "Missing user data, returning to setup")
            Toast.makeText(context, "Oturum bilgileri eksik, lütfen tekrar giriş yapın", Toast.LENGTH_LONG).show()
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, SetupFragment())
                .commit()
            return
        }

        // Partner adını al ve FCM token'ı kaydet
        loadPartnerName()
        saveTokenToFirestore()

        // Lottie animasyonu başlat
        setupHeartAnimation()

        // Update status text
        binding.tvLastMessage.text = "Bağlanıyor..."

        // Vibrator başlat (API 31+ uyumlu)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        setupVibrationButtons()
        setupEmojiButtons()
        setupNoteButton()
        setupLogoutButton()

        listenForMessages()
    }

    private fun setupHeartAnimation() {
        try {
            // Lottie animasyonu raw kaynağından yükle
            binding.lottieHeart.setAnimation(R.raw.heart_pulse)
            binding.lottieHeart.playAnimation()
        } catch (e: Exception) {
            Log.w("Gzmy", "Lottie yüklenemedi, ObjectAnimator fallback kullanılıyor: ${e.message}")
            // Fallback: ObjectAnimator ile pulse efekti
            setupFallbackPulseAnimation()
        }
    }

    private fun setupFallbackPulseAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(binding.lottieHeart, scaleX, scaleY).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun loadPartnerName() {
        db.collection("couples").document(coupleCode).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val couple = doc.toObject(com.gzmy.app.data.model.Couple::class.java)
                    couple?.let {
                        partnerName = if (it.partner1Id == userId) {
                            it.partner2Name
                        } else {
                            it.partner1Name
                        }
                        if (_binding != null && partnerName.isNotEmpty()) {
                            binding.tvLastMessage.text = "$partnerName ile bağlısınız 💕"
                        }
                    }
                }
            }
    }

    private fun saveTokenToFirestore() {
        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        val fcmToken = prefs.getString("fcm_token", null)

        if (fcmToken != null && userId.isNotEmpty()) {
            db.collection("tokens").document(userId)
                .set(
                    mapOf(
                        "fcmToken" to fcmToken,
                        "lastUpdated" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d("Gzmy", "FCM token saved to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("Gzmy", "Failed to save FCM token: ${e.message}")
                }
        }
    }

    private fun setupVibrationButtons() {
        binding.btnGentle.setOnClickListener {
            sendVibration(Message.VibrationPattern.GENTLE)
        }

        binding.btnHeartbeat.setOnClickListener {
            sendVibration(Message.VibrationPattern.HEARTBEAT)
        }

        binding.btnIntense.setOnClickListener {
            sendVibration(Message.VibrationPattern.INTENSE)
        }
    }

    private fun setupEmojiButtons() {
        binding.btnHeart.setOnClickListener { sendQuickEmoji("❤️") }
        binding.btnKiss.setOnClickListener { sendQuickEmoji("💋") }
        binding.btnLove.setOnClickListener { sendQuickEmoji("🥰") }
        binding.btnPlease.setOnClickListener { sendQuickEmoji("🥺") }
    }

    private fun setupNoteButton() {
        binding.btnSendNote.setOnClickListener {
            val note = binding.etNote.text.toString().trim()
            if (note.isNotEmpty()) {
                sendNote(note)
                binding.etNote.text?.clear()
            }
        }
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Çıkış Yap")
                .setMessage("Çıkış yapmak istediğine emin misin?")
                .setPositiveButton("Evet") { _, _ ->
                    logout()
                }
                .setNegativeButton("Hayır", null)
                .show()
        }
    }

    private fun sendVibration(pattern: Message.VibrationPattern) {
        val patternLabel = when (pattern) {
            Message.VibrationPattern.GENTLE -> "Yumuşak titreşim"
            Message.VibrationPattern.HEARTBEAT -> "Kalp atışı"
            Message.VibrationPattern.INTENSE -> "Yoğun titreşim"
        }

        if (coupleCode.isEmpty()) {
            Toast.makeText(context, "Hata: Çift kodu bulunamadı", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Gönderiliyor...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val message = Message(
                        id = UUID.randomUUID().toString(),
                        coupleCode = coupleCode,
                        senderId = userId,
                        senderName = userName,
                        type = Message.MessageType.VIBRATION,
                        vibrationPattern = pattern,
                        content = "$patternLabel gönderdi",
                        timestamp = Timestamp.now()
                    )
                    db.collection("messages").add(message).await()
                }
                Toast.makeText(context, "$patternLabel gönderildi! 💕", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("Gzmy", "Error: ${e.message}", e)
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendQuickEmoji(emoji: String) {
        sendNote(emoji)
    }

    private fun sendNote(content: String) {
        if (coupleCode.isEmpty()) {
            Toast.makeText(context, "Hata: Çift kodu bulunamadı", Toast.LENGTH_LONG).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val message = Message(
                        id = UUID.randomUUID().toString(),
                        coupleCode = coupleCode,
                        senderId = userId,
                        senderName = userName,
                        type = Message.MessageType.NOTE,
                        content = content,
                        timestamp = Timestamp.now()
                    )
                    db.collection("messages").add(message).await()
                }
                Toast.makeText(context, "Mesaj gönderildi! 💕", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("Gzmy", "Error sending note: ${e.message}", e)
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun listenForMessages() {
        if (coupleCode.isEmpty()) {
            Log.e("Gzmy", "Cannot listen for messages: coupleCode is empty")
            return
        }

        Log.d("Gzmy", "Listening for messages with coupleCode: $coupleCode")

        db.collection("messages")
            .whereEqualTo("coupleCode", coupleCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Gzmy", "Listen error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    return@addSnapshotListener
                }

                // Son mesajı al (sadece karşıdan gelen)
                val lastMessage = snapshot.documents
                    .mapNotNull { it.toObject(Message::class.java) }
                    .filter { it.senderId != userId }
                    .maxByOrNull { it.timestamp?.toDate()?.time ?: 0 }

                lastMessage?.let { message ->
                    Log.d("Gzmy", "New message from ${message.senderName}: ${message.content}")

                    // Titreşim çal (UYGULAMA AÇIKKEN)
                    when (message.type) {
                        Message.MessageType.VIBRATION -> {
                            message.vibrationPattern?.let {
                                vibrate(it)
                                showNotification("💓 ${message.senderName}", "Titreşim gönderdi!", message)
                            }
                            showReceivedMessage("${message.senderName} sana titreşim gönderdi! 💓")
                        }
                        Message.MessageType.NOTE -> {
                            vibrateGentle()
                            showNotification("💌 ${message.senderName}", message.content, message)
                            showReceivedMessage("${message.senderName}: ${message.content}")
                        }
                        else -> {}
                    }
                }
            }
    }

    private fun vibrate(pattern: Message.VibrationPattern) {
        when (pattern) {
            Message.VibrationPattern.GENTLE -> vibrateDevice(longArrayOf(0, 200))
            Message.VibrationPattern.HEARTBEAT -> vibrateDevice(longArrayOf(0, 100, 100, 100, 200, 100, 100))
            Message.VibrationPattern.INTENSE -> vibrateDevice(longArrayOf(0, 800))
        }
    }

    private fun vibrateGentle() {
        vibrateDevice(longArrayOf(0, 200))
    }

    private fun vibrateDevice(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e("Gzmy", "Vibration error: ${e.message}")
        }
    }

    private fun showNotification(title: String, body: String, message: Message) {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            requireContext(),
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(requireContext(), GzmyApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun showReceivedMessage(messageText: String) {
        if (_binding != null) {
            binding.tvLastMessage.text = messageText
            binding.tvLastMessage.visibility = View.VISIBLE
        }
    }

    private fun logout() {
        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        parentFragmentManager.beginTransaction()
            .replace(R.id.container, SetupFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
