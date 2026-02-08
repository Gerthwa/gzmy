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
        
        case 'chat':
          title = `💬 ${senderName || 'Partnerin'}`;
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
      
      // FCM HYBRID payload (notification + data)
      // notification bloğu: Uygulama kapalı/arka plandayken sistem otomatik bildirim gösterir
      // data bloğu: Uygulama ön plandayken onMessageReceived() ile özel işlem yapılır
      //
      // Davranış:
      //   FOREGROUND  → onMessageReceived() çağrılır, biz bildirim göstermeyiz (broadcast)
      //   BACKGROUND  → Sistem notification bloğundan otomatik bildirim gösterir
      //   KILLED      → Sistem notification bloğundan otomatik bildirim gösterir
      const payload = {
        token: fcmToken,
        // Üst düzey notification — sistem bunu arka plan/kapalıda otomatik gösterir
        notification: {
          title: title,
          body: body,
        },
        android: {
          priority: 'high',
          ttl: 86400000,  // 24 saat (ms) — cihaz çevrimdışıysa mesaj bekler
          notification: {
            channelId: 'gzmy_channel',
            priority: 'MAX',
            defaultVibrateTimings: false,
            vibrateTimingsMillis: vibrationTimings.map(String),
            notificationCount: 1,
            tag: 'gzmy_' + typeLower, // Aynı türden bildirimleri gruplayarak üst üste biner
          },
        },
        apns: {
          headers: {
            'apns-priority': '10',
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
 * Çizim güncellendiğinde partner'a bildirim gönder.
 * couples/{coupleId} dokümanındaki latestDrawingUrl alanı değiştiğinde tetiklenir.
 */
exports.onDrawingUpdated = functions.firestore
  .document('couples/{coupleId}')
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    // Sadece latestDrawingUrl değiştiyse devam et
    if (!after.latestDrawingUrl || after.latestDrawingUrl === before.latestDrawingUrl) {
      return null;
    }

    const coupleId = context.params.coupleId;
    console.log('Drawing updated for couple:', coupleId);

    try {
      // Her iki partner'a da bildirim gönder (gönderen hariç tutmak için
      // senderId bilgisi yok, bu yüzden her ikisine de gönderilir —
      // FCMService foreground'da bunu filtreler)
      const partnerIds = [after.partner1Id, after.partner2Id].filter(Boolean);

      for (const partnerId of partnerIds) {
        const tokenDoc = await admin.firestore()
          .collection('tokens')
          .doc(partnerId)
          .get();

        if (!tokenDoc.exists) continue;

        const { fcmToken } = tokenDoc.data();
        if (!fcmToken) continue;

        const senderName = after.partner1Id === partnerId
          ? after.partner2Name || 'Partnerin'
          : after.partner1Name || 'Partnerin';

        const payload = {
          token: fcmToken,
          notification: {
            title: `🎨 ${senderName}`,
            body: 'Yeni bir cizim gonderdi!',
          },
          android: {
            priority: 'high',
            ttl: 86400000,
            notification: {
              channelId: 'gzmy_channel',
              priority: 'MAX',
              tag: 'gzmy_drawing',
            },
          },
          data: {
            type: 'drawing',
            title: `🎨 ${senderName}`,
            body: 'Yeni bir cizim gonderdi!',
            drawingUrl: after.latestDrawingUrl,
            coupleCode: coupleId,
            click_action: 'OPEN_APP',
          },
        };

        await admin.messaging().send(payload);
        console.log('Drawing notification sent to:', partnerId);
      }

      return { success: true };
    } catch (error) {
      console.error('Drawing notification error:', error.message);
      return { success: false };
    }
  });

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
