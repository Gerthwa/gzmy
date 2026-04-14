package com.gzmy.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gzmy.app.data.repository.ProfileRepository
import com.gzmy.app.databinding.FragmentProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val repo = ProfileRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSaveProfile.setOnClickListener { saveProfile() }
        loadProfile()
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repo.getMyProfile() }
                .onSuccess { profile ->
                    withContext(Dispatchers.Main) {
                        binding.etDisplayName.setText(profile.displayName)
                        binding.etBio.setText(profile.bio)
                        binding.etPhotoUrl.setText(profile.photoUrl)
                    }
                }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Profile load failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun saveProfile() {
        val name = binding.etDisplayName.text?.toString().orEmpty().trim()
        val bio = binding.etBio.text?.toString().orEmpty().trim()
        val photo = binding.etPhotoUrl.text?.toString().orEmpty().trim()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repo.updateProfile(name, bio, photo) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
