# gzmy 💕

Sen ve sevgilin için özel yapılmış minimal couple app.

## Özellikler

- 💓 **Kalp Atışı (Titreşim)** - Partnerine dokunmatik titreşim gönder (app kapalıyken bile!)
- 💌 **Anlık Notlar** - Küçük romantik mesajlar gönder
- 🔔 **Push Bildirimler** - Uygulama kapalı olsa bile titreşim ve mesaj al
- 🔐 **Özel Bağlantı** - Sadece sizin kodunuzla eşleşme

## APK İndirme ve Kurulum

### 1. APK Derleme

```bash
# Projeyi Android Studio'da aç
# Build > Generate Signed Bundle/APK > APK
# Ya da komut satırından:
./gradlew assembleRelease
```

### 2. Manuel Kurulum

```bash
# Her iki telefona da APK'yı kopyala
adb install app-release.apk
```

### 3. Play Store (Opsiyonel)

- Geliştirici hesabı gerektirir
- $25 bir kerelik ücret

## Firebase Kurulumu (Zorunlu)

### Adım 1: Firebase Projesi Oluştur

1. https://console.firebase.google.com/ adresine git
2. "Proje Ekle" ye tıkla
3. Proje adı: `gzmy-couple`
4. Analytics'i etkinleştir (opsiyonel)

### Adım 2: Android Uygulaması Ekle

1. Android simgesine tıkla
2. Paket adı: `com.gzmy.app`
3. App nickname: `gzmy`
4. SHA-1 sertifikası (debug için):
   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore
   # Şifre: android
   ```

### Adım 3: google-services.json İndir

- İndirilen dosyayı `app/` klasörüne koy
- Android Studio'da sync yap

### Adım 4: Firestore Database Kurulumu

```
Firestore Database > Create Database > Start in production mode

Collections:

1. couples
   - document: {coupleCode}
     - partner1Id: string
     - partner1Name: string
     - partner2Id: string
     - partner2Name: string
     - createdAt: timestamp
     - lastActivity: timestamp

2. messages
   - document: auto-id
     - coupleCode: string
     - senderId: string
     - senderName: string
     - type: "vibration" | "note" | "heartbeat"
     - content: string
     - vibrationPattern: string
     - timestamp: timestamp
     - isRead: boolean

3. tokens
   - document: {userId}
     - fcmToken: string
     - lastUpdated: timestamp
```

### Adım 5: Cloud Functions Kurulumu (Bildirimler için)

```bash
# Firebase CLI kurulumu
npm install -g firebase-tools

# Login
firebase login

# Proje dizininde
firebase init functions

# functions/index.js dosyasını düzenle (aşağıdaki kodu kullan)

# Deploy
firebase deploy --only functions
```

**functions/index.js:**
```javascript
const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Yeni mesaj geldiğinde bildirim gönder
exports.sendNotification = functions.firestore
  .document('messages/{messageId}')
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const { coupleCode, senderId, senderName, type, content } = message;
    
    // Çift bilgilerini al
    const coupleDoc = await admin.firestore()
      .collection('couples')
      .doc(coupleCode)
      .get();
    
    if (!coupleDoc.exists) return;
    
    const couple = coupleDoc.data();
    
    // Alıcıyı belirle (gönderen dışındaki partner)
    let receiverId;
    if (couple.partner1Id === senderId) {
      receiverId = couple.partner2Id;
    } else {
      receiverId = couple.partner1Id;
    }
    
    if (!receiverId) return;
    
    // Alıcının FCM token'ını al
    const tokenDoc = await admin.firestore()
      .collection('tokens')
      .doc(receiverId)
      .get();
    
    if (!tokenDoc.exists) return;
    
    const { fcmToken } = tokenDoc.data();
    
    // Bildirim içeriği belirle
    let title, body, vibrationPattern;
    
    switch(type) {
      case 'vibration':
        title = '💓 ' + senderName;
        body = 'Sana bir titreşim gönderdi!';
        vibrationPattern = message.vibrationPattern || 'gentle';
        break;
      case 'heartbeat':
        title = '💗 ' + senderName;
        body = 'Kalp atışı gönderdi!';
        vibrationPattern = 'heartbeat';
        break;
      case 'note':
        title = '💌 ' + senderName;
        body = content;
        vibrationPattern = 'gentle';
        break;
      default:
        title = 'gzmy';
        body = 'Yeni mesaj!';
    }
    
    // Bildirimi gönder
    const payload = {
      token: fcmToken,
      notification: {
        title: title,
        body: body,
      },
      android: {
        notification: {
          channelId: 'gzmy_channel',
          priority: 'high',
          defaultVibrateTimings: true,
          vibrateTimingsMillis: getVibrationPattern(vibrationPattern),
        },
      },
      data: {
        type: type,
        vibrationPattern: vibrationPattern,
        senderId: senderId,
        senderName: senderName,
        messageId: context.params.messageId,
      },
    };
    
    try {
      await admin.messaging().send(payload);
      console.log('Bildirim gönderildi:', receiverId);
    } catch (error) {
      console.error('Bildirim hatası:', error);
    }
  });

function getVibrationPattern(pattern) {
  switch(pattern) {
    case 'gentle':
      return [0, 200];
    case 'heartbeat':
      return [0, 100, 100, 100, 300, 200];
    case 'intense':
      return [0, 500];
    default:
      return [0, 200];
  }
}
```

**functions/package.json:**
```json
{
  "name": "gzmy-functions",
  "version": "1.0.0",
  "dependencies": {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^4.5.0"
  },
  "engines": {
    "node": "18"
  }
}
```

## Nasıl Kullanılır?

### İlk Kurulum (2 kişi için de yapılmalı)

1. **Uygulamayı aç**
2. **Adını gir**
3. **Seçenek 1: Yeni çift oluştur**
   - Otomatik 6 haneli kod oluşturulur
   - Kodu partnerinle paylaş
4. **Seçenek 2: Çifte katıl**
   - Partnerinin verdiği kodu gir

### Titreşim Gönderme

- **🥰 Yumuşak**: Hafif, nazikçe titreşim
- **💓 Kalp Atışı**: Gerçek kalp ritmi gibi (tik-tik-tok)
- **💪 Yoğun**: Güçlü, uzun titreşim

### Not Gönderme

- Hızlı emoji: ❤️ 💋 🥰 🥺
- Özel not yaz ve gönder

## Özellikler

### App Kapalıyken Bildirim

- Uygulama kapalı olsa bile titreşim ve mesaj alırsın
- Bildirime dokunarak uygulamayı açabilirsin
- Gelen titreşim telefonunun titreşim ayarlarına göre değişir

### Gizlilik

- Sadece eşleşen 2 kişi arasında çalışır
- Veriler Firebase'de şifreli saklanır
- Üçüncü taraflar mesajları göremez

## Gereksinimler

- Android 8.0+ (API 26)
- İnternet bağlantısı
- Bildirim ve titreşim izinleri

## Sorun Giderme

### Bildirimler Gelmiyor

1. Ayarlar > Uygulamalar > gzmy > Bildirimler
2. Tüm bildirimleri aç
3. Arka planda çalışma izni ver
4. Pil optimizasyonunu devre dışı bırak

### Titreşim Çalışmıyor

1. Ayarlar > Ses ve Titreşim > Titreşim
2. Titreşim seviyesini kontrol et
3. Rahatsız Etme modunu kapat

## Gelecek Güncellemeler

- [ ] Widget desteği
- [ ] Karanlık/aydınlık tema seçeneği
- [ ] Ses kaydı gönderme
- [ ] Fotoğraf paylaşımı
- [ ] Konum paylaşımı (güvenli)
- [ ] Özel tema renkleri

## Destek

Sorun yaşarsanız veya öneriniz varsa:
- GitHub Issues
- E-posta: destek@gzmy.app

---

💕 gzmy - Kalp atışını hisset
