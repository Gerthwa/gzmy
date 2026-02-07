package com.gzmy.app.ui.setup

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.gzmy.app.R
import com.gzmy.app.data.model.Couple
import com.gzmy.app.databinding.FragmentSetupBinding
import com.gzmy.app.util.AnimationUtils as Anim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class SetupFragment : Fragment() {
    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCreateCouple.setOnClickListener { v ->
            Anim.pressScale(v) { showCreateCoupleView() }
        }

        binding.btnJoinCouple.setOnClickListener { v ->
            Anim.pressScale(v) { showJoinCoupleView() }
        }

        binding.btnCreateSubmit.setOnClickListener { v ->
            Anim.pressScale(v) { createCouple() }
        }

        binding.btnJoinSubmit.setOnClickListener { v ->
            Anim.pressScale(v) { joinCouple() }
        }

        binding.btnCopyCode.setOnClickListener { v ->
            Anim.pressScale(v) { copyCodeToClipboard() }
        }

        binding.btnContinueAfterCreate.setOnClickListener { v ->
            Anim.pressScale(v) { navigateToMain() }
        }

        // Welcome ekranı giriş animasyonu
        animateEntrance()
    }

    private fun animateEntrance() {
        val root = binding.root as? ViewGroup ?: return
        val scrollContent = root.getChildAt(0) as? ViewGroup ?: return
        val views = mutableListOf<View>()
        for (i in 0 until scrollContent.childCount) {
            views.add(scrollContent.getChildAt(i))
        }
        Anim.staggeredEntrance(views, staggerDelay = 100L)
    }

    private fun showCreateCoupleView() {
        binding.layoutInitialButtons.visibility = View.GONE
        binding.layoutJoinCouple.visibility = View.GONE
        Anim.slideUp(binding.layoutCreateCouple)
    }

    private fun showJoinCoupleView() {
        binding.layoutInitialButtons.visibility = View.GONE
        binding.layoutCreateCouple.visibility = View.GONE
        Anim.slideUp(binding.layoutJoinCouple)
    }

    private fun createCouple() {
        val name = binding.etYourNameCreate.text.toString().trim()
        if (name.isEmpty()) {
            binding.etYourNameCreate.error = "Adınızı girin"
            return
        }

        val code = generateCoupleCode()
        val userId = UUID.randomUUID().toString()

        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val couple = Couple(
                        code = code,
                        partner1Id = userId,
                        partner1Name = name,
                        createdAt = Timestamp.now(),
                        lastActivity = Timestamp.now()
                    )
                    db.collection("couples").document(code).set(couple).await()
                }

                saveUserData(code, userId, name)
                showCodeCreated(code)

            } catch (e: Exception) {
                Log.e("Gzmy", "Couple creation error: ${e.message}", e)
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (_binding != null) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun joinCouple() {
        val name = binding.etYourNameJoin.text.toString().trim()
        val code = binding.etCoupleCode.text.toString().trim().uppercase()

        if (name.isEmpty()) {
            binding.etYourNameJoin.error = "Adınızı girin"
            return
        }
        if (code.isEmpty()) {
            binding.etCoupleCode.error = "Kodu girin"
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userId = UUID.randomUUID().toString()

                withContext(Dispatchers.IO) {
                    val doc = db.collection("couples").document(code).get().await()

                    if (!doc.exists()) {
                        throw Exception("Çift bulunamadı")
                    }

                    val couple = doc.toObject(Couple::class.java)
                        ?: throw Exception("Veri hatası")

                    if (couple.partner2Id.isNotEmpty()) {
                        throw Exception("Bu çift zaten dolu")
                    }

                    db.collection("couples").document(code).update(
                        "partner2Id", userId,
                        "partner2Name", name,
                        "lastActivity", Timestamp.now()
                    ).await()
                }

                saveUserData(code, userId, name)
                Toast.makeText(context, "Çifte katıldın! 💕", Toast.LENGTH_SHORT).show()
                navigateToMain()

            } catch (e: Exception) {
                Log.e("Gzmy", "Join couple error: ${e.message}", e)
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (_binding != null) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun generateCoupleCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun saveUserData(code: String, userId: String, name: String) {
        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("couple_code", code)
            putString("user_id", userId)
            putString("user_name", name)
            apply()
        }

        // FCM token'ı Firestore'a kaydet
        saveTokenToFirestore(userId)
    }

    private fun saveTokenToFirestore(userId: String) {
        val prefs = requireActivity().getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        val fcmToken = prefs.getString("fcm_token", null)

        if (fcmToken != null) {
            db.collection("tokens").document(userId)
                .set(
                    mapOf(
                        "fcmToken" to fcmToken,
                        "lastUpdated" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d("Gzmy", "FCM token saved successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("Gzmy", "Failed to save FCM token: ${e.message}")
                }
        } else {
            Log.w("Gzmy", "FCM token not available yet, will retry on next launch")
        }
    }

    private fun showCodeCreated(code: String) {
        if (_binding != null) {
            binding.layoutCreateForm.visibility = View.GONE
            binding.tvGeneratedCode.text = code
            Anim.slideUp(binding.layoutCodeSuccess)
            Anim.popIn(binding.tvGeneratedCode, startDelay = 200L)
        }
    }

    private fun copyCodeToClipboard() {
        val code = binding.tvGeneratedCode.text.toString()
        val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Çift Kodu", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Kod kopyalandı!", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.container, MainFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
