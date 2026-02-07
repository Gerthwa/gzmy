# GitHub'a Yükleme ve Otomatik APK Derleme

## Hızlı Başlangıç

### 1. GitHub Repo Oluştur

1. https://github.com/new adresine git
2. Repo adı: `gzmy` (veya istediğin ad)
3. Public veya Private seç (ikisi de olur)
4. "Create repository" butonuna tıkla

### 2. Kodları Yükle

```bash
# Terminal'de gzmy-couple-app klasörüne git
cd ~/.openclaw/workspace/gzmy-couple-app

# Git init
git init

# Tüm dosyaları ekle
git add .

# İlk commit
git commit -m "Initial commit - gzmy couple app"

# GitHub repo bağlantısı (kendi kullanıcı adını yaz)
git remote add origin https://github.com/KULLANICI_ADIN/gzmy.git

# Push et
git branch -M main
git push -u origin main
```

### 3. Firebase Secret Ekle (ZORUNLU)

GitHub'da:
1. Repo sayfası → Settings → Secrets and variables → Actions
2. "New repository secret" butonuna tıkla
3. Name: `GOOGLE_SERVICES_JSON`
4. Value: `google-services.json` dosyasının **tüm içeriğini** kopyala yapıştır
5. "Add secret" butonuna tıkla

**google-services.json nasıl alınır:**
- Firebase Console → Project Settings → General
- Android app'ini seç
- `google-services.json` dosyasını indir
- Dosyayı bir text editöründe aç, tüm içeriği kopyala

### 4. Otomatik Derlemeyi Başlat

GitHub'da:
1. Repo sayfası → Actions sekmesi
2. "Build APK" workflow'unu seç
3. "Run workflow" butonuna tıkla
4. 5-10 dakika bekle

### 5. APK'yı İndir

1. Actions sekmesinde en üstteki çalışan workflow'u seç
2. Aşağı kaydır, "Artifacts" bölümünü bul
3. `gzmy-debug-apk` veya `gzmy-release-apk` yazan linke tıkla
4. ZIP dosyası indirilecek, içinden APK'yı çıkar

### 6. Telefona Kur

```bash
# USB ile bağla
adb install gzmy-1.0.0-debug.apk

# Veya email/WhatsApp ile gönderip kur
```

## Nasıl Çalışır?

Her `git push` yaptığında otomatik olarak:
1. ✅ Kodlar GitHub'a gider
2. ✅ GitHub Actions tetiklenir
3. ✅ APK derlenir (5-10 dk)
4. ✅ APK artifact olarak kaydedilir
5. ✅ Sen indirip kullanırsın

## Sorun Giderme

**"Build failed" hatası:**
- Secrets bölümünde `GOOGLE_SERVICES_JSON` ekli mi kontrol et
- Firebase'de app paket adı doğru mu: `com.gzmy.app`

**APK indirilemiyor:**
- Actions → En son workflow → Artifacts bölümüne bak
- ZIP dosyasını indir, içinden APK çıkar

**Kurulum hatası:**
- Telefonda "Bilinmeyen kaynaklardan yükleme" izni ver
- Eski sürüm varsa önce kaldır: `adb uninstall com.gzmy.app`

## Önemli Notlar

- GitHub'da **Private repo** kullanman önerilir (kodlar gizli kalsın)
- `google-services.json` asla GitHub'a commit etme! (Secret olarak ekle)
- Her güncellemede otomatik yeni APK oluşur

## Yardım

Sorun yaşarsan:
1. Actions sekmesindeki kırmızı ❌ işaretine tıkla
2. Hata mesajını oku
3. Bana gönder
