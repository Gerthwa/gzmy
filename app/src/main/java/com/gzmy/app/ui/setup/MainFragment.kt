package com.gzmy.app.ui.setup

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gzmy.app.GzmyApplication
import com.gzmy.app.R
import com.gzmy.app.data.LiveStatusManager
import com.gzmy.app.data.model.Couple
import com.gzmy.app.data.model.Message
import com.gzmy.app.data.repository.MessageRepository
import com.gzmy.app.databinding.FragmentMainBinding
import com.gzmy.app.location.LocationTracker
import com.gzmy.app.ui.chat.ChatFragment
import com.gzmy.app.ui.drawing.DrawingActivity
import com.gzmy.app.util.AnimationUtils as Anim
import com.gzmy.app.util.GeoUtils
import com.gzmy.app.util.VibrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var repo: MessageRepository
    private var coupleCode: String = ""
    private var userId: String = ""
    private var userName: String = ""
    private var partnerName: String = ""

    // Slider debounce
    private var sliderWriteJob: Job? = null
    private var messagesListener: ListenerRegistration? = null
    private var isUpdatingFromRemote = false
    private var currentArrowRotation = 0f

    // Location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            startLocationTracking()
        } else {
            if (_binding != null) {
                binding.tvLocationStatus.text = "📍 Konum izni reddedildi"
            }
        }
    }

    /** LiveStatusManager observer — receives real-time couple updates */
    private val coupleObserver = object : LiveStatusManager.StatusObserver {
        override fun onCoupleUpdated(couple: Couple, myLevel: Int, partnerLevel: Int, partnerName: String) {
            if (_binding == null) return

            // Partner miss level UI
            val partnerEmoji = when {
                partnerLevel < 20 -> "🤍"
                partnerLevel < 40 -> "💛"
                partnerLevel < 60 -> "🧡"
                partnerLevel < 80 -> "❤️"
                else -> "❤️‍🔥"
            }
            val pName = partnerName.ifEmpty { "Partner" }
            binding.tvPartnerMissLevel.text = "$pName: $partnerEmoji $partnerLevel"

            // Sync my slider from remote (only if I didn't change it)
            isUpdatingFromRemote = true
            if (binding.sliderMissYou.value.toInt() != myLevel) {
                binding.sliderMissYou.value = myLevel.toFloat()
                updateMissYouLabel(myLevel)
            }
            isUpdatingFromRemote = false
        }

        override fun onPartnerLocationUpdated(lat: Double, lng: Double) {
            if (_binding == null) return
            updateCompassUI(lat, lng)
        }
    }

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

        repo = MessageRepository(requireContext())
        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        coupleCode = prefs.getString("couple_code", "") ?: ""
        userId = prefs.getString("user_id", "") ?: ""
        userName = prefs.getString("user_name", "") ?: ""

        Log.d("Gzmy", "MainFragment loaded - coupleCode: '$coupleCode', userId: '$userId', userName: '$userName'")

        if (coupleCode.isEmpty() || userId.isEmpty()) {
            Log.e("Gzmy", "Missing user data, returning to setup")
            Toast.makeText(context, "Oturum bilgileri eksik, lütfen tekrar giriş yapın", Toast.LENGTH_LONG).show()
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.container, SetupFragment())
                .commit()
            return
        }

        loadPartnerName()
        saveTokenToFirestore()
        setupHeartAnimation()

        binding.tvLastMessage.text = "Bağlanıyor..."

        setupSlider()
        setupChatButton()
        setupVibrationButtons()
        setupEmojiButtons()
        setupNoteButton()
        setupDrawingButton()
        setupLogoutButton()

        listenForMessages()
        listenForCoupleUpdates()
        requestLocationPermission()

        // Ambient animations
        Anim.floatUpDown(binding.lottieHeart, amplitude = 6f, duration = 3500L)

        // Foreground broadcast'i dinle
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            newMessageReceiver,
            IntentFilter(GzmyApplication.ACTION_NEW_MESSAGE)
        )

        // Kartların staggered giriş animasyonu
        animateEntrance()
    }

    private fun animateEntrance() {
        val root = binding.root as? android.view.ViewGroup ?: return
        val scrollContent = root.getChildAt(0) as? android.view.ViewGroup ?: return

        val cards = mutableListOf<View>()
        for (i in 0 until scrollContent.childCount) {
            cards.add(scrollContent.getChildAt(i))
        }
        Anim.staggeredEntrance(cards, staggerDelay = 65L)
    }

    // =============== ÖZELLİK 1: MISS YOU SLIDER ===============

    private fun setupSlider() {
        binding.sliderMissYou.value = 0f
        binding.tvMissYouValue.text = "🤍 0"

        binding.sliderMissYou.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isUpdatingFromRemote) return@addOnChangeListener

            val level = value.toInt()
            updateMissYouLabel(level)

            // Her 10 birimde bir hafif tıkırtı
            if (level % 10 == 0) {
                VibrationManager.performSliderTick(requireContext())
            }

            // Debounce: 500ms bekle, sonra Firestore'a yaz
            sliderWriteJob?.cancel()
            sliderWriteJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(500)
                writeMissYouLevel(level)
            }
        }
    }

    private fun updateMissYouLabel(level: Int) {
        val emoji = when {
            level < 20 -> "🤍"
            level < 40 -> "💛"
            level < 60 -> "🧡"
            level < 80 -> "❤️"
            else -> "❤️‍🔥"
        }
        binding.tvMissYouValue.text = "$emoji $level"
    }

    private fun writeMissYouLevel(level: Int) {
        LiveStatusManager.writeMissLevel(requireContext(), level)
    }

    /** LiveStatusManager üzerinden couple dokümanını dinle */
    private fun listenForCoupleUpdates() {
        LiveStatusManager.addObserver(coupleObserver)
        LiveStatusManager.start(requireContext())
    }

    // =============== ÖZELLİK 2: CHAT BUTONU ===============

    private fun setupChatButton() {
        binding.btnChat.setOnClickListener {
            VibrationManager.performHeavyClick(requireContext())
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right
                )
                .replace(R.id.container, ChatFragment())
                .addToBackStack("chat")
                .commit()
        }
    }

    // =============== MEVCUT FONKSİYONLAR ===============

    private fun setupHeartAnimation() {
        try {
            binding.lottieHeart.setAnimation(R.raw.heart_pulse)
            binding.lottieHeart.playAnimation()
        } catch (e: Exception) {
            Log.w("Gzmy", "Lottie yüklenemedi, ObjectAnimator fallback kullanılıyor: ${e.message}")
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
                    val couple = doc.toObject(Couple::class.java)
                    couple?.let {
                        partnerName = if (it.partner1Id == userId) it.partner2Name else it.partner1Name
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
                .addOnSuccessListener { Log.d("Gzmy", "FCM token saved to Firestore") }
                .addOnFailureListener { e -> Log.e("Gzmy", "Failed to save FCM token: ${e.message}") }
        }
    }

    private fun setupVibrationButtons() {
        binding.btnGentle.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performHeavyClick(requireContext())
                sendVibration(Message.VibrationPattern.GENTLE)
            }
        }
        binding.btnHeartbeat.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performHeartbeat(requireContext())
                sendVibration(Message.VibrationPattern.HEARTBEAT)
            }
        }
        binding.btnIntense.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performHeavyClick(requireContext())
                sendVibration(Message.VibrationPattern.INTENSE)
            }
        }
    }

    private fun setupEmojiButtons() {
        binding.btnHeart.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performLightTap(requireContext())
                sendQuickEmoji("❤️")
            }
        }
        binding.btnKiss.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performLightTap(requireContext())
                sendQuickEmoji("💋")
            }
        }
        binding.btnLove.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performLightTap(requireContext())
                sendQuickEmoji("🥰")
            }
        }
        binding.btnPlease.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performLightTap(requireContext())
                sendQuickEmoji("🥺")
            }
        }
    }

    private fun setupNoteButton() {
        binding.btnSendNote.setOnClickListener { v ->
            val note = binding.etNote.text.toString().trim()
            if (note.isNotEmpty()) {
                Anim.pressScale(v) {
                    VibrationManager.performHeavyClick(requireContext())
                    sendNote(note)
                    binding.etNote.text?.clear()
                }
            }
        }
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Çıkış Yap")
                .setMessage("Çıkış yapmak istediğine emin misin?")
                .setPositiveButton("Evet") { _, _ -> logout() }
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

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                repo.sendMessage(
                    coupleCode = coupleCode,
                    senderId = userId,
                    senderName = userName,
                    type = Message.MessageType.VIBRATION,
                    content = "$patternLabel gönderdi",
                    vibrationPattern = pattern
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$patternLabel gönderildi! 💕", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Gzmy", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
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

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                repo.sendMessage(
                    coupleCode = coupleCode,
                    senderId = userId,
                    senderName = userName,
                    type = Message.MessageType.NOTE,
                    content = content
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Mesaj gönderildi! 💕", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Gzmy", "Error sending note: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun listenForMessages() {
        if (coupleCode.isEmpty()) {
            Log.e("Gzmy", "Cannot listen for messages: coupleCode is empty")
            return
        }

        Log.d("Gzmy", "Listening for messages with coupleCode: $coupleCode")

        messagesListener = db.collection("messages")
            .whereEqualTo("coupleCode", coupleCode)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20) // Performans: sadece son 20 mesaj
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Gzmy", "Listen error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                val lastMessage = snapshot.documents
                    .mapNotNull { it.toObject(Message::class.java) }
                    .filter { it.senderId != userId }
                    .maxByOrNull { it.timestamp?.toDate()?.time ?: 0 }

                lastMessage?.let { message ->
                    Log.d("Gzmy", "New message from ${message.senderName}: ${message.content}")

                    when (message.type) {
                        Message.MessageType.VIBRATION -> {
                            VibrationManager.vibratePattern(requireContext(),
                                when (message.vibrationPattern) {
                                    Message.VibrationPattern.GENTLE -> longArrayOf(0, 200)
                                    Message.VibrationPattern.HEARTBEAT -> longArrayOf(0, 100, 100, 100, 200, 100, 100)
                                    Message.VibrationPattern.INTENSE -> longArrayOf(0, 800)
                                }
                            )
                            showReceivedMessage("${message.senderName} sana titreşim gönderdi! 💓")
                        }
                        Message.MessageType.NOTE -> {
                            VibrationManager.performLightTap(requireContext())
                            showReceivedMessage("${message.senderName}: ${message.content}")
                        }
                        Message.MessageType.HEARTBEAT -> {
                            VibrationManager.performHeartbeat(requireContext())
                            showReceivedMessage("${message.senderName} kalp atışı gönderdi! 💓")
                        }
                        Message.MessageType.CHAT -> {
                            // Chat mesajları ayrı ekranda gösterilir
                            showReceivedMessage("${message.senderName}: ${message.content}")
                        }
                    }
                }
            }
    }

    private fun showReceivedMessage(messageText: String) {
        if (_binding != null) {
            binding.tvLastMessage.text = messageText
            Anim.shimmerFlash(binding.tvLastMessage)
            binding.tvLastMessage.visibility = View.VISIBLE
        }
    }

    /** Foreground'da sessiz mesaj alımı */
    private val newMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val body = intent?.getStringExtra("body") ?: return
            showReceivedMessage(body)
        }
    }

    // =============== KONUM / PUSULA ===============

    private fun requestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationTracking() {
        if (_binding != null) {
            binding.tvLocationStatus.text = "📍 Konum takibi aktif"
        }

        LocationTracker.addListener(locationListener)
        LocationTracker.start(requireContext())

        // If partner location is already cached, update UI
        val pLat = LiveStatusManager.partnerLat
        val pLng = LiveStatusManager.partnerLng
        if (pLat != 0.0 && pLng != 0.0) {
            updateCompassUI(pLat, pLng)
        }
    }

    private val locationListener = object : LocationTracker.LocationListener {
        override fun onMyLocationUpdated(lat: Double, lng: Double) {
            // Re-calculate distance/bearing with partner
            val pLat = LiveStatusManager.partnerLat
            val pLng = LiveStatusManager.partnerLng
            if (pLat != 0.0 && pLng != 0.0) {
                updateCompassUI(pLat, pLng)
            }
        }
    }

    private fun updateCompassUI(partnerLat: Double, partnerLng: Double) {
        if (_binding == null) return

        val myLat = LocationTracker.lastLat
        val myLng = LocationTracker.lastLng
        if (myLat == 0.0 && myLng == 0.0) {
            binding.tvDistance.text = "—"
            binding.tvDirection.text = "Konumun henüz alınamadı"
            return
        }

        val distance = GeoUtils.haversineDistance(myLat, myLng, partnerLat, partnerLng)
        val bearing = GeoUtils.bearing(myLat, myLng, partnerLat, partnerLng)
        val direction = GeoUtils.bearingToDirection(bearing)

        binding.tvDistance.text = GeoUtils.formatDistance(distance)
        binding.tvDirection.text = "$direction yönünde"
        binding.tvLocationStatus.text = "📍 Konum aktif"

        // Smooth rotate arrow
        val targetRotation = bearing
        ObjectAnimator.ofFloat(binding.ivDirectionArrow, View.ROTATION, currentArrowRotation, targetRotation).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        currentArrowRotation = targetRotation
    }

    // =============== ÇİZİM ===============

    private fun setupDrawingButton() {
        binding.btnOpenDrawing.setOnClickListener { v ->
            Anim.pressScale(v) {
                VibrationManager.performHeavyClick(requireContext())
                val intent = Intent(requireContext(), DrawingActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun logout() {
        LiveStatusManager.reset()
        LocationTracker.removeListener(locationListener)
        LocationTracker.stop()
        com.gzmy.app.widget.WidgetUpdateWorker.cancel(requireContext())

        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.container, SetupFragment())
            .commit()
    }

    override fun onDestroyView() {
        LiveStatusManager.removeObserver(coupleObserver)
        LiveStatusManager.stop()
        LocationTracker.removeListener(locationListener)
        LocationTracker.stop()
        messagesListener?.remove()
        sliderWriteJob?.cancel()
        context?.let {
            try { LocalBroadcastManager.getInstance(it).unregisterReceiver(newMessageReceiver) }
            catch (_: Exception) { /* receiver zaten kayıtlı değildi */ }
        }
        _binding = null
        super.onDestroyView()
    }
}
