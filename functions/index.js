const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

/**
 * Yeni mesaj oluşturulduğunda push bildirimi gönder
 * Bu fonksiyon Firestore'daki 'messages' koleksiyonunda yeni doküman oluştuğunda tetiklenir
 */
exports.sendNotification = functions.firestore
  .document('messages/{messageId}')
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const { coupleCode, senderId, senderName, type, content, vibrationPattern } = message;
    
    // Kotlin enum'lar Firestore'a BÜYÜK HARF olarak yazılır (VIBRATION, NOTE, HEARTBEAT)
    // Tüm karşılaştırmalar için küçük harfe çevir
    const typeLower = (type || 'note').toLowerCase();
    const vibPatternLower = (vibrationPattern || 'gentle').toLowerCase();
    
    console.log('Yeni mesaj:', { coupleCode, senderId, type: typeLower, vibrationPattern: vibPatternLower });
    
    try {
      // Çift bilgilerini al
      const coupleDoc = await admin.firestore()
        .collection('couples')
        .doc(coupleCode)
        .get();
      
      if (!coupleDoc.exists) {
        console.log('Çift bulunamadı:', coupleCode);
        return null;
      }
      
      const couple = coupleDoc.data();
      
      // Alıcıyı belirle (gönderen dışındaki partner)
      let receiverId;
      if (couple.partner1Id === senderId) {
        receiverId = couple.partner2Id;
      } else {
        receiverId = couple.partner1Id;
      }
      
      if (!receiverId) {
        console.log('Alıcı bulunamadı');
        return null;
      }
      
      // Alıcının FCM token'ını al
      const tokenDoc = await admin.firestore()
        .collection('tokens')
        .doc(receiverId)
        .get();
      
      if (!tokenDoc.exists) {
        console.log('Token bulunamadı:', receiverId);
        return null;
      }
      
      const { fcmToken } = tokenDoc.data();
      
      // Bildirim içeriğini hazırla (küçük harfe çevrilmiş type kullan)
      let title, body, pattern;
      
      switch (typeLower) {
        case 'vibration':
          title = `💓 ${senderName || 'Partnerin'}`;
          body = 'Sana bir titreşim gönderdi!';
          pattern = vibPatternLower || 'gentle';
          break;
        
        case 'heartbeat':
          title = `💗 ${senderName || 'Partnerin'}`;
          body = 'Kalp atışı gönderdi!';
          pattern = 'heartbeat';
          break;
        
        case 'note':
          title = `💌 ${senderName || 'Partnerin'}`;
          body = (content && content.length > 100) ? content.substring(0, 97) + '...' : (content || 'Yeni mesaj!');
          pattern = 'gentle';
          break;
        
        default:
          title = `💕 ${senderName || 'Partnerin'}`;
          body = 'Yeni mesaj!';
          pattern = 'gentle';
      }
      
      // Titreşim pattern'i
      const vibrationTimings = getVibrationPattern(pattern);
      
      // FCM bildirimi oluştur - DATA-ONLY payload
      // NOT: notification bloğu KALDIRILDI. Böylece uygulama arka planda/kapalı
      // iken de onMessageReceived() çağrılır ve özel titreşim çalışır.
      const payload = {
        token: fcmToken,
        android: {
          priority: 'high', // Cihazı uyandırır (data-only mesajlar için kritik)
        },
        apns: {
          headers: {
            'apns-priority': '10', // iOS için yüksek öncelik
          },
          payload: {
            aps: {
              alert: {
                title: title,
                body: body,
              },
              sound: 'default',
              badge: 1,
              'content-available': 1,
            },
          },
        },
        data: {
          title: title,
          body: body,
          type: typeLower,
          vibrationPattern: pattern,
          senderId: senderId || '',
          senderName: senderName || 'Partnerin',
          messageId: context.params.messageId,
          coupleCode: coupleCode,
          click_action: 'OPEN_APP',
        },
      };
      
      // Bildirimi gönder
      const response = await admin.messaging().send(payload);
      console.log('Bildirim gönderildi:', {
        response,
        receiverId,
        type,
        pattern,
        messageId: context.params.messageId,
      });
      
      return { success: true, messageId: response };
      
    } catch (error) {
      // Detaylı hata loglama
      const errorInfo = {
        code: error.code || 'UNKNOWN',
        message: error.message,
        messageId: context.params.messageId,
        coupleCode,
        senderId,
        timestamp: new Date().toISOString(),
      };
      console.error('BILDIRIM_HATASI:', JSON.stringify(errorInfo));
      
      // Geçersiz token'ı temizle (token expired/unregistered)
      if (
        error.code === 'messaging/registration-token-not-registered' ||
        error.code === 'messaging/invalid-registration-token'
      ) {
        console.warn('Geçersiz token siliniyor, receiverId:', receiverId);
        try {
          await admin.firestore().collection('tokens').doc(receiverId).delete();
          console.log('Geçersiz token silindi:', receiverId);
        } catch (deleteError) {
          console.error('Token silme hatası:', deleteError.message);
        }
      }
      
      return { success: false, error: errorInfo };
    }
  });

/**
 * Titreşim pattern'ini döndür
 */
function getVibrationPattern(pattern) {
  // pattern zaten küçük harfe çevrilmiş olarak gelir
  switch (pattern) {
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

/**
 * Kullanıcı token'ını güncelle (isteğe bağlı)
 */
exports.updateToken = functions.https.onCall(async (data, context) => {
  const { userId, fcmToken } = data;
  
  if (!userId || !fcmToken) {
    throw new functions.https.HttpsError('invalid-argument', 'userId ve fcmToken gerekli');
  }
  
  try {
    await admin.firestore()
      .collection('tokens')
      .doc(userId)
      .set({
        fcmToken: fcmToken,
        lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
      });
    
    return { success: true };
  } catch (error) {
    throw new functions.https.HttpsError('internal', error.message);
  }
});
