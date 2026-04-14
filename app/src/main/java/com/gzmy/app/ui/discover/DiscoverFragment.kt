package com.gzmy.app.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gzmy.app.data.model.UserProfile
import com.gzmy.app.data.repository.DiscoverRepository
import com.gzmy.app.databinding.FragmentDiscoverBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiscoverFragment : Fragment() {
    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private val repository = DiscoverRepository()

    private val candidates = mutableListOf<UserProfile>()
    private var currentIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPass.setOnClickListener { performSwipe("pass") }
        binding.btnLike.setOnClickListener { performSwipe("like") }
        loadCandidates()
    }

    private fun loadCandidates() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.fetchCandidates() }
                .onSuccess { list ->
                    withContext(Dispatchers.Main) {
                        candidates.clear()
                        candidates.addAll(list)
                        currentIndex = 0
                        showCurrentCandidate()
                    }
                }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Discover load failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun performSwipe(direction: String) {
        val current = candidates.getOrNull(currentIndex) ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.swipe(current.uid, direction) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        currentIndex += 1
                        showCurrentCandidate()
                    }
                }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Swipe failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun showCurrentCandidate() {
        val user = candidates.getOrNull(currentIndex)
        if (user == null) {
            binding.tvCandidateName.text = "No candidates"
            binding.tvCandidateBio.text = "Try again later."
            binding.tvDiscoverEmpty.visibility = View.VISIBLE
            binding.btnLike.isEnabled = false
            binding.btnPass.isEnabled = false
            return
        }

        binding.tvDiscoverEmpty.visibility = View.GONE
        binding.btnLike.isEnabled = true
        binding.btnPass.isEnabled = true
        binding.tvCandidateName.text =
            if (user.displayName.isBlank()) "Unknown user" else user.displayName
        binding.tvCandidateBio.text =
            if (user.bio.isBlank()) "No bio yet." else user.bio
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
