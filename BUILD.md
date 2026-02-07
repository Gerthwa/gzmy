# gzmy - APK Derleme Talimatları

## Gereksinimler

- Android Studio (Ladybug veya daha yeni)
- Android SDK (API 26-34)
- Java 17+
- Firebase Hesabı

## Adım Adım Kurulum

### 1. Projeyi Aç

Android Studio'da `gzmy-couple-app` klasörünü aç.

### 2. Firebase Kurulumu

```bash
# 1. Firebase Console'a git:
# https://console.firebase.google.com/

# 2. Yeni proje oluştur:
# - Proje adı: gzmy-couple
# - Analytics: İsteğe bağlı (önerilir)

# 3. Android uygulaması ekle:
# - Paket adı: com.gzmy.app
# - App nickname: gzmy
# - SHA-1 sertifikası gerekli (aşağıda)

# 4. Debug SHA-1 al:
keytool -list -v \
  -alias androiddebugkey \
  -keystore ~/.android/debug.keystore
# Şifre: android

# 5. google-services.json dosyasını indir ve app/ klasörüne koy

# 6. Android Studio'da "Sync Project with Gradle Files"
```

### 3. Firebase Cloud Functions Kurulumu (Bildirimler için ZORUNLU)

```bash
# Firebase CLI kurulumu
npm install -g firebase-tools

# Login
firebase login

# Yeni terminal'de proje klasörüne git
cd gzmy-couple-app

# Firebase init
firebase init functions

# functions/index.js dosyasını README.md'deki kodla değiştir

# Deploy
firebase deploy --only functions
```

### 4. Debug APK Derleme

```bash
# Terminal'de
./gradlew assembleDebug

# Veya Android Studio'da:
# Build → Build Bundle(s) / APK(s) → Build APK(s)

# Çıktı konumu:
# app/build/outputs/apk/debug/gzmy-1.0.0-debug.apk
```

### 5. Release APK Derleme (Play Store için)

```bash
# 1. Keystore oluştur (ilk kez yapıyorsan)
keytool -genkey -v \
  -keystore gzmy-release.keystore \
  -alias gzmy \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# 2. Keystore bilgilerini local.properties'e ekle
# (projeye dahil edilmez, .gitignore'da olur)

echo "RELEASE_STORE_FILE=gzmy-release.keystore" >> local.properties
echo "RELEASE_KEY_ALIAS=gzmy" >> local.properties
echo "RELEASE_STORE_PASSWORD=SIFREN" >> local.properties
echo "RELEASE_KEY_PASSWORD=SIFREN" >> local.properties

# 3. Release build
./gradlew assembleRelease

# Çıktı konumu:
# app/build/outputs/apk/release/gzmy-1.0.0-release.apk
```

## APK İmzalama (Manuel)

```bash
# Debug APK zaten imzalıdır

# Release APK imzalama:
jarsigner -verbose \
  -sigalg SHA1withRSA \
  -digestalg SHA1 \
  -keystore gzmy-release.keystore \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  gzmy

# Zipalign (optimizasyon)
~/Library/Android/sdk/build-tools/34.0.0/zipalign -v 4 \
  app-release-unsigned.apk \
  gzmy-v1.0.0.apk
```

## İki Telefona Kurulum

### Yöntem 1: USB ile

```bash
# Telefonu USB ile bağla
# Geliştirici seçenekleri > USB hata ayıklama açık olsun

# Kurulum
adb install app/build/outputs/apk/debug/gzmy-1.0.0-debug.apk

# Veya her iki telefona da
adb -s TELEFON1_SERIAL install app/build/outputs/apk/debug/gzmy-1.0.0-debug.apk
adb -s TELEFON2_SERIAL install app/build/outputs/apk/debug/gzmy-1.0.0-debug.apk
```

### Yöntem 2: Email/WhatsApp ile

1. APK dosyasını email/WhatsApp ile gönder
2. Telefonda indir
3. "Bilinmeyen kaynaklardan yükleme" izni ver
4. Yükle

### Yöntem 3: Firebase App Distribution (Önerilen)

```bash
# Firebase CLI ile dağıtım
firebase appdistribution:distribute app/build/outputs/apk/release/gzmy-1.0.0-release.apk \
  --app 1:123456789:android:abcdef \
  --release-notes "İlk sürüm 💕" \
  --testers "email1@gmail.com, email2@gmail.com"

# Tester'lara email ile davet gönderilir
```

## Test

### Uygulama Kapalıyken Bildirim Testi

1. Telefon A'da gzmy'yi aç ve çift oluştur
2. Telefon B'de koda katıl
3. Telefon B'yi kapat (uygulamayı sonlandır)
4. Telefon A'dan titreşim gönder
5. Telefon B'de bildirim gelmeli ve titreşim çalmalı

### Sorun Giderme

**"App not installed" hatası:**
- Debug ve release APK karışmış olabilir
- Önce eski sürümü kaldır: `adb uninstall com.gzmy.app`

**Bildirimler gelmiyor:**
- Firebase Console > Cloud Messaging > API'yi etkinleştir
- Cloud Functions deploy edilmiş mi kontrol et
- Telefon bildirim izinlerini kontrol et

**Titreşim çalışmıyor:**
- Ayarlar > Ses > Titreşim seviyesi
- Rahatsız Etme modu kapalı mı?
- Pil optimizasyonunu devre dışı bırak

## Notlar

- Debug APK her 24 saatte bir yeniden imzalanmalı (Firebase test için)
- Release APK için keystore dosyasını güvenli yerde sakla
- Google Play Store'a yüklemek için App Bundle (AAB) gerekir

## İletişim

Sorun yaşarsanız veya yardım gerekirse:
- GitHub Issues
- E-posta: destek@gzmy.app
