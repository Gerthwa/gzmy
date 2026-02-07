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
    
    console.log('Yeni mesaj:', { coupleCode, senderId, type });
    
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
      
      // Bildirim içeriğini hazırla
      let title, body, pattern;
      
      switch (type) {
        case 'vibration':
          title = `💓 ${senderName || 'Partnerin'}`;
          body = 'Sana bir titreşim gönderdi!';
          pattern = vibrationPattern || 'gentle';
          break;
        
        case 'heartbeat':
          title = `💗 ${senderName || 'Partnerin'}`;
          body = 'Kalp atışı gönderdi!';
          pattern = 'heartbeat';
          break;
        
        case 'note':
          title = `💌 ${senderName || 'Partnerin'}`;
          body = content.length > 100 ? content.substring(0, 97) + '...' : content;
          pattern = 'gentle';
          break;
        
        default:
          title = 'gzmy';
          body = 'Yeni mesaj!';
          pattern = 'gentle';
      }
      
      // Titreşim pattern'i
      const vibrationTimings = getVibrationPattern(pattern);
      
      // FCM bildirimi oluştur
      const payload = {
        token: fcmToken,
        notification: {
          title: title,
          body: body,
          sound: 'default',
        },
        android: {
          notification: {
            channelId: 'gzmy_channel',
            priority: 'high',
            defaultVibrateTimings: false,
            vibrateTimingsMillis: vibrationTimings,
            visibility: 'public',
            sound: 'default',
          },
        },
        apns: {
          payload: {
            aps: {
              sound: 'default',
              badge: 1,
            },
          },
        },
        data: {
          type: type || 'note',
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
      console.log('Bildirim gönderildi:', response);
      
      return { success: true, messageId: response };
      
    } catch (error) {
      console.error('Bildirim gönderme hatası:', error);
      return { success: false, error: error.message };
    }
  });

/**
 * Titreşim pattern'ini döndür
 */
function getVibrationPattern(pattern) {
  switch (pattern) {
    case 'gentle':
      // Yumuşak - 200ms
      return [0, 200];
    
    case 'heartbeat':
      // Kalp atışı - tik-tik-tok
      return [0, 100, 100, 100, 300, 200];
    
    case 'intense':
      // Yoğun - 500ms
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
