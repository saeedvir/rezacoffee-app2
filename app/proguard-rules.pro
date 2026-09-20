# قوانین حفاظت از اینترفیس‌های وب‌ویو و مدل‌های جاوااسکریپت
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# حفاظت از کلاس‌های وب‌ویو، وب‌کیت و اندروید ایکس
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**
-keep class android.webkit.** { *; }

# حفظ سلامت و کوکی‌های سشن ورود و سبد خرید مشتری
-keepclassmembers class * extends android.webkit.CookieManager { *; }

# حفظ متادیتاهای ضروری جاوااسکریپت و تعاملات وب
-keepattributes *Annotation*,EnclosingMethod,InnerClasses,Signature
-keepattributes SourceFile,LineNumberTable
