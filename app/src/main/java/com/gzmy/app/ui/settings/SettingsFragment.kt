package com.gzmy.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gzmy.app.data.repository.ProfileRepository
import com.gzmy.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val repo = ProfileRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadProfileState()
        binding.switchDiscoverable.setOnCheckedChangeListener { _, isChecked ->
            setDiscoverable(isChecked)
        }
    }

    private fun loadProfileState() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repo.getMyProfile() }
                .onSuccess { profile ->
                    withContext(Dispatchers.Main) {
                        binding.switchDiscoverable.isChecked = profile.discoverable
                    }
                }
        }
    }

    private fun setDiscoverable(discoverable: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repo.setDiscoverable(discoverable) }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Setting update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
