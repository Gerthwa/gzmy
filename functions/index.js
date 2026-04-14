const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

/**
 * Yeni mesaj oluşturulduğunda push bildirimi gönder.
 * Firestore 'messages' koleksiyonunda yeni doküman oluştuğunda tetiklenir.
 *
 * HYBRID payload kullanır (notification + data):
 *   FOREGROUND  → onMessageReceived() çağrılır (uygulama kendi yönetir)
 *   BACKGROUND  → Sistem notification bloğundan otomatik bildirim gösterir
 *   KILLED      → Sistem notification bloğundan otomatik bildirim gösterir
 */
exports.sendNotification = functions.firestore
  .document('messages/{messageId}')
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const { coupleCode, senderId, senderName, type, content, vibrationPattern } = message;

    if (!coupleCode || !senderId) {
      console.error('Eksik message alanı:', {
        messageId: context.params.messageId,
        coupleCode,
        senderId,
      });
      return null;
    }

    // Kotlin enum'lar Firestore'a BÜYÜK HARF olarak yazılır
    const typeLower = (type || 'note').toLowerCase();
    const vibPatternLower = (vibrationPattern || 'gentle').toLowerCase();

    console.log('Yeni mesaj:', {
      coupleCode,
      senderId,
      type: typeLower,
      vibrationPattern: vibPatternLower,
      messageId: context.params.messageId,
    });

    // receiverId dış scope'ta — catch bloğunda da erişilebilir
    let receiverId = null;

    try {
      // ── 1. Çift bilgilerini al ──
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
      receiverId = (couple.partner1Id === senderId)
        ? couple.partner2Id
        : couple.partner1Id;

      if (!receiverId) {
        console.log('Alıcı bulunamadı — partner2 henüz katılmamış olabilir');
        return null;
      }

      // ── 2. Alıcının FCM token'ını al ──
      const tokenDoc = await admin.firestore()
        .collection('tokens')
        .doc(receiverId)
        .get();

      if (!tokenDoc.exists) {
        console.log('Token dokümanı bulunamadı:', receiverId);
        return null;
      }

      const { fcmToken } = tokenDoc.data();

      // Token boş/null kontrolü
      if (!fcmToken || typeof fcmToken !== 'string' || fcmToken.length < 10) {
        console.log('Geçersiz/boş FCM token:', receiverId, fcmToken);
        return null;
      }

      // ── 3. Bildirim içeriğini hazırla ──
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

        case 'drawing':
          title = `🎨 ${senderName || 'Partnerin'}`;
          body = 'Sana özel bir çizim yaptı!';
          pattern = 'gentle';
          break;

        case 'voice':
          title = `🎤 ${senderName || 'Partnerin'}`;
          body = 'Sana bir ses kaydı gönderdi.';
          pattern = 'gentle';
          break;

        case 'photo':
          title = `📸 ${senderName || 'Partnerin'}`;
          body = 'Yeni bir fotoğraf gönderdi.';
          pattern = 'gentle';
          break;

        default:
          title = `💕 ${senderName || 'Partnerin'}`;
          body = 'Yeni mesaj!';
          pattern = 'gentle';
      }

      const vibrationTimings = getVibrationPattern(pattern);

      // ── 4. FCM HYBRID payload ──
      const payload = {
        token: fcmToken,

        // notification bloğu — sistem bunu arka plan/kapalıda otomatik gösterir
        notification: {
          title: title,
          body: body,
        },

        android: {
          priority: 'high',
          ttl: 86400000, // 24 saat (ms)
          notification: {
            channelId: 'gzmy_channel',
            priority: 'MAX',
            sound: 'default',
            defaultVibrateTimings: false,
            vibrateTimingsMillis: vibrationTimings.map(String),
            notificationCount: 1,
            tag: 'gzmy_' + typeLower,
            // Kilit ekranında da görünsün
            visibility: 'PUBLIC',
          },
        },

        apns: {
          headers: {
            'apns-priority': '10',
            'apns-push-type': 'alert',
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
              'mutable-content': 1,
            },
          },
        },

        // data bloğu — foreground'da onMessageReceived() kullanılır
        data: {
          title: title,
          body: body,
          type: typeLower,
          vibrationPattern: pattern,
          senderId: senderId || '',
          senderName: senderName || 'Partnerin',
          messageId: context.params.messageId,
          coupleCode: coupleCode || '',
          click_action: 'OPEN_APP',
          timestamp: String(Date.now()),
        },
      };

      // ── 5. Gönder ──
      const response = await admin.messaging().send(payload);
      console.log('Bildirim gönderildi:', {
        response,
        receiverId,
        type: typeLower,
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
        receiverId,
        timestamp: new Date().toISOString(),
      };
      console.error('BILDIRIM_HATASI:', JSON.stringify(errorInfo));

      // Geçersiz token'ı temizle (token expired/unregistered)
      const isInvalidTokenError =
        error.code === 'messaging/registration-token-not-registered' ||
        error.code === 'messaging/invalid-registration-token';

      if (receiverId && isInvalidTokenError) {
        console.warn('Geçersiz token siliniyor, receiverId:', receiverId);
        try {
          await admin.firestore().collection('tokens').doc(receiverId).delete();
          console.log('Geçersiz token silindi:', receiverId);
        } catch (deleteError) {
          console.error('Token silme hatası:', deleteError.message);
        }
        // Permanent failure: token artık geçersiz, retry gerekmez.
        return { success: false, error: errorInfo, permanent: true };
      }
      // Transient failure: throw to allow automatic retry.
      throw error;
    }
  });

/**
 * Titreşim pattern'ini döndür
 */
function getVibrationPattern(pattern) {
  switch (pattern) {
    case 'gentle':
      return [0, 200];
    case 'heartbeat':
      return [0, 100, 100, 100, 300, 200];
    case 'intense':
      return [0, 500, 100, 500];
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

    const partnerIds = [after.partner1Id, after.partner2Id].filter(Boolean);
    const sendResults = await Promise.allSettled(partnerIds.map(async (partnerId) => {
      const tokenDoc = await admin.firestore()
        .collection('tokens')
        .doc(partnerId)
        .get();

      if (!tokenDoc.exists) {
        console.log('Token bulunamadı (drawing):', partnerId);
        return { partnerId, skipped: true, reason: 'no-token-doc' };
      }

      const { fcmToken } = tokenDoc.data();
      if (!fcmToken || fcmToken.length < 10) {
        console.log('Geçersiz token (drawing):', partnerId);
        return { partnerId, skipped: true, reason: 'invalid-token' };
      }

      const senderName = after.partner1Id === partnerId
        ? after.partner2Name || 'Partnerin'
        : after.partner1Name || 'Partnerin';

      const payload = {
        token: fcmToken,
        notification: {
          title: `🎨 ${senderName}`,
          body: 'Yeni bir çizim gönderdi!',
        },
        android: {
          priority: 'high',
          ttl: 86400000,
          notification: {
            channelId: 'gzmy_channel',
            priority: 'MAX',
            sound: 'default',
            tag: 'gzmy_drawing',
            visibility: 'PUBLIC',
          },
        },
        data: {
          type: 'drawing',
          title: `🎨 ${senderName}`,
          body: 'Yeni bir çizim gönderdi!',
          drawingUrl: after.latestDrawingUrl,
          coupleCode: coupleId,
          click_action: 'OPEN_APP',
        },
      };

      const msgId = await admin.messaging().send(payload);
      console.log('Drawing notification sent to:', partnerId, msgId);
      return { partnerId, skipped: false };
    }));

    const rejected = sendResults.filter((r) => r.status === 'rejected');
    if (rejected.length > 0) {
      rejected.forEach((r) => {
        const err = r.reason || {};
        const code = err.code || 'unknown';
        const message = err.message || String(err);
        console.error('Drawing notification error:', { code, message });
      });
      // Throw to allow platform retry for transient errors.
      throw new Error(`Drawing notification failed for ${rejected.length} recipients`);
    }

    return { success: true };
  });

/**
 * Kullanıcı token'ını güncelle (callable)
 */
exports.updateToken = functions.https.onCall(async (data, context) => {
  const { userId, fcmToken } = data;

  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Giriş yapmalısınız');
  }

  if (!userId || !fcmToken) {
    throw new functions.https.HttpsError('invalid-argument', 'userId ve fcmToken gerekli');
  }

  if (context.auth.uid !== userId) {
    throw new functions.https.HttpsError('permission-denied', 'Sadece kendi token kaydınızı güncelleyebilirsiniz');
  }

  try {
    await admin.firestore()
      .collection('tokens')
      .doc(userId)
      .set({
        fcmToken: fcmToken,
        lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
        platform: 'android',
      });

    return { success: true };
  } catch (error) {
    console.error('updateToken error:', error.message);
    throw new functions.https.HttpsError('internal', 'Token güncellenemedi');
  }
});
