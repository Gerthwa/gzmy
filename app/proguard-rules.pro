# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep model classes
-keep class com.gzmy.app.data.model.** { *; }

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    *;
}

# Keep FCM Service
-keep class com.gzmy.app.service.FCMService { *; }

# Keep Widget Provider
-keep class com.gzmy.app.widget.GzmyWidgetProvider { *; }

# Keep VibrationManager
-keep class com.gzmy.app.util.VibrationManager { *; }

# Keep Activities & Fragments
-keep class com.gzmy.app.ui.** { *; }

# Firebase Messaging
-keep class com.google.firebase.messaging.FirebaseMessagingService { *; }
-keep class com.google.firebase.messaging.RemoteMessage { *; }
