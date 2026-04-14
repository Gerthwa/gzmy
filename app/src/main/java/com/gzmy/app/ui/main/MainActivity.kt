package com.gzmy.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.gzmy.app.R
import com.gzmy.app.data.AuthSessionManager
import com.gzmy.app.databinding.ActivityMainBinding
import com.gzmy.app.ui.chat.ChatFragment
import com.gzmy.app.ui.discover.DiscoverFragment
import com.gzmy.app.ui.profile.ProfileFragment
import com.gzmy.app.ui.settings.SettingsFragment
import com.gzmy.app.ui.setup.MainFragment
import com.gzmy.app.ui.setup.SetupFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), AvatarHost {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG = "MainActivity"

        const val TAB_HOME = "tab_home"
        const val TAB_CHAT = "tab_chat"
        const val TAB_DISCOVER = "tab_discover"
    }

    // Android 13+ bildirim izni launcher'ı
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Bildirim izni: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: immersive status/nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemInsets()
        setupAvatarMenu()

        lifecycleScope.launch {
            runCatching {
                val authUid = AuthSessionManager.ensureSignedIn(this@MainActivity)
                migrateLegacyUserIdIfNeeded(authUid)
            }.onFailure { e ->
                Log.e(TAG, "Firebase auth bootstrap failed: ${e.message}", e)
            }
        }

        // Android 13+ için bildirim izni iste
        requestNotificationPermission()

        setupBottomNav()

        if (savedInstanceState == null) {
            showInitialScreen()
            handleLaunchIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showInitialScreen() {
        val prefs = getSharedPreferences("gzmy_prefs", MODE_PRIVATE)
        val coupleCode = prefs.getString("couple_code", null)
        val userId = prefs.getString("user_id", null)

        if (coupleCode.isNullOrEmpty() || userId.isNullOrEmpty()) {
            showSetupScreen()
        } else {
            showAppShell()
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    fun showSetupScreen() {
        updateChromeForSetup(isSetup = true)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.container, SetupFragment())
            .commit()
    }

    fun showAppShell(defaultTabId: Int = R.id.nav_home) {
        updateChromeForSetup(isSetup = false)
        binding.bottomNav.selectedItemId = defaultTabId
        if (supportFragmentManager.findFragmentByTag(currentTabTag()) == null) {
            selectTabByMenuId(defaultTabId)
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            selectTabByMenuId(item.itemId)
            true
        }
    }

    private fun selectTabByMenuId(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> switchRootTab(TAB_HOME)
            R.id.nav_chat -> switchRootTab(TAB_CHAT)
            R.id.nav_discover -> switchRootTab(TAB_DISCOVER)
        }
    }

    private fun switchRootTab(tabTag: String) {
        val fragment = when (tabTag) {
            TAB_CHAT -> ChatFragment()
            TAB_DISCOVER -> DiscoverFragment()
            else -> MainFragment()
        }

        supportFragmentManager.popBackStack()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.container, fragment, tabTag)
            .commit()
        applyTopBarTitle(tabTag)
    }

    fun openProfile() {
        openOverlay(ProfileFragment())
    }

    fun openSettings() {
        openOverlay(SettingsFragment())
    }

    private fun openOverlay(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right
            )
            .replace(R.id.container, fragment)
            .addToBackStack(fragment::class.java.simpleName)
            .commit()
    }

    fun navigateToChatTab() {
        updateChromeForSetup(isSetup = false)
        binding.bottomNav.selectedItemId = R.id.nav_chat
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type") ?: return

        val prefs = getSharedPreferences("gzmy_prefs", MODE_PRIVATE)
        val isConfigured = !prefs.getString("couple_code", null).isNullOrEmpty() &&
            !prefs.getString("user_id", null).isNullOrEmpty()
        if (!isConfigured) return

        if (type == "chat" || type == "note" || type == "vibration" || type == "heartbeat") {
            showAppShell(R.id.nav_chat)
        }
    }

    private fun applyTopBarTitle(tabTag: String) {
        val title = when (tabTag) {
            TAB_CHAT -> "Chat"
            TAB_DISCOVER -> "Discover"
            else -> getString(R.string.app_name)
        }
        binding.topBar.title = title
    }

    private fun setupAvatarMenu() {
        binding.ivAvatarMenu.setOnClickListener { anchor ->
            val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.menu_avatar, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_profile -> openProfile()
                    R.id.action_settings -> openSettings()
                }
                true
            }
            popup.show()
        }
    }

    private fun updateChromeForSetup(isSetup: Boolean) {
        binding.topBar.visibility = if (isSetup) android.view.View.GONE else android.view.View.VISIBLE
        binding.bottomNav.visibility = if (isSetup) android.view.View.GONE else android.view.View.VISIBLE
        binding.ivAvatarMenu.isEnabled = !isSetup
        binding.ivAvatarMenu.alpha = if (isSetup) 0.4f else 1f
    }

    override fun setAvatarEnabled(enabled: Boolean) {
        binding.ivAvatarMenu.isEnabled = enabled
        binding.ivAvatarMenu.alpha = if (enabled) 1f else 0.4f
    }

    private fun currentTabTag(): String {
        return when (binding.bottomNav.selectedItemId) {
            R.id.nav_chat -> TAB_CHAT
            R.id.nav_discover -> TAB_DISCOVER
            else -> TAB_HOME
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                bars.top,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            binding.bottomNav.setPadding(
                binding.bottomNav.paddingLeft,
                binding.bottomNav.paddingTop,
                binding.bottomNav.paddingRight,
                bars.bottom
            )
            insets
        }
    }

    private fun migrateLegacyUserIdIfNeeded(authUid: String) {
        val prefs = getSharedPreferences("gzmy_prefs", MODE_PRIVATE)
        val oldUserId = prefs.getString("user_id", null) ?: return
        if (oldUserId == authUid) return

        val coupleCode = prefs.getString("couple_code", null).orEmpty()
        prefs.edit()
            .putString("legacy_user_id", oldUserId)
            .putString("user_id", authUid)
            .putString("auth_uid", authUid)
            .apply()

        if (coupleCode.isNotEmpty()) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("couples").document(coupleCode).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) return@addOnSuccessListener
                    val updates = mutableMapOf<String, Any>()
                    val p1 = doc.getString("partner1Id")
                    val p2 = doc.getString("partner2Id")
                    if (p1 == oldUserId) updates["partner1Id"] = authUid
                    if (p2 == oldUserId) updates["partner2Id"] = authUid
                    if (updates.isNotEmpty()) {
                        db.collection("couples").document(coupleCode).update(updates)
                    }
                }
        }

        Log.d(TAG, "Migrated legacy user id to auth uid")
    }

}
