# پروژه رسمی اندروید فروشگاه قهوه رضا (RezaCoffee)
وب‌سایت هدف: https://rezacoffee.ir/
حداقل نسخه اندروید: Android 7.0 (API 24) به بالا
نسخه هدف ساخت: Android 14 (API 34)

## ۱. نحوه باز کردن در Android Studio:
1. فایل زیپ را از حالت فشرده خارج کنید.
2. نرم‌افزار Android Studio (نسخه Hedgehog، Iguana یا Jellyfish به بعد) را باز کنید.
3. گزینه "Open" را انتخاب کرده و پوشه خارج‌شده را انتخاب کنید.
4. اجازه دهید گریدل پکیج‌ها را دانلود کند (Sync Project with Gradle Files).
5. دکمه سبز رنگ "Run 'app'" را برای اجرای مستقیم روی گوشی یا شبیه‌ساز لمس کنید.

## ۲. راه‌اندازی GitHub Actions و انتشار خودکار APK (CI/CD):
فایل ورک‌فلو آماده در مسیر زیر در پروژه موجود است:
`.github/workflows/build-apk.yml`

### مراحل فعال‌سازی:
1. پروژه را به مخزن گیت‌هاب خود Push کنید:
   ```bash
   git init
   git add .
   git commit -m "feat: initial commit"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/rezacoffee-android.git
   git push -u origin main
   ```

2. در تنظیمات مخزن (Settings > Secrets and variables > Actions)، متغیرهای زیر را ثبت کنید:
   - KEYSTORE_BASE64: متن Base64 فایل release-keystore.jks
   - KEYSTORE_PASSWORD: رمز عبور کلید
   - KEY_ALIAS: نام مستعار کلید (مثلاً rezacoffee)
   - KEY_PASSWORD: رمز عبور کلید

3. برای انتشار خودکار هر نسخه، کافی است تگ بزنید:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   سرورهای ابری گیت‌هاب به صورت خودکار فایل‌های `RezaCoffee-release.apk` و `RezaCoffee-debug.apk` را ساخته و در تب Releases قرار می‌دهند.

up -1