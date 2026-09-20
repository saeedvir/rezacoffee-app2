package ir.rezacoffee.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * اپلیکیشن اندروید فروشگاه رضا کافی (RezaCoffee)
 * پشتیبانی از اندروید 7.0 نوقا (API 24) به بالا
 * وب‌سایت: https://rezacoffee.ir/
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutError: View
    private lateinit var textErrorTitle: TextView
    private lateinit var textErrorDesc: TextView
    private lateinit var btnRetry: Button
    private lateinit var imgErrorIcon: ImageView
    private lateinit var offlineBanner: View

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var doubleBackToExitPressedOnce = false
    private var isNetworkAvailable = true
    private var lastLoadedUrl = TARGET_URL

    companion object {
        const val TARGET_URL = "https://rezacoffee.ir/"
        const val USER_AGENT_SUFFIX = " RezaCoffeeApp/1.0.0 (Android)"
    }

    // انتخـاب فایل برای آپلود در وب‌سایت (تصاویر، رسید و غیره)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val results = if (result.resultCode == RESULT_OK && result.data != null) {
                val dataString = result.data?.dataString
                val clipData = result.data?.clipData
                when {
                    clipData != null -> {
                        val count = clipData.itemCount
                        Array(count) { i -> clipData.getItemAt(i).uri }
                    }
                    dataString != null -> arrayOf(Uri.parse(dataString))
                    else -> null
                }
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupNetworkMonitoring()
        setupSwipeRefresh()
        setupWebView()
        setupBackNavigation()

        // بارگذاری اولیه سایت
        loadWebsite(TARGET_URL)
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        layoutError = findViewById(R.id.layoutError)
        textErrorTitle = findViewById(R.id.textErrorTitle)
        textErrorDesc = findViewById(R.id.textErrorDesc)
        btnRetry = findViewById(R.id.btnRetry)
        imgErrorIcon = findViewById(R.id.imgErrorIcon)
        offlineBanner = findViewById(R.id.offlineBanner)

        btnRetry.setOnClickListener {
            hideError()
            if (isNetworkAvailable) {
                webView.reload()
            } else {
                Toast.makeText(this, R.string.error_no_internet_short, Toast.LENGTH_SHORT).show()
                showNoInternetError()
            }
        }
    }

    private fun setupSwipeRefresh() {
        // رنگ‌های برند قهوه رضا برای انیمیشن لودینگ کشیدن به پایین
        swipeRefreshLayout.setColorSchemeResources(
            R.color.coffee_primary,
            R.color.coffee_accent,
            R.color.coffee_primary_variant
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.coffee_surface)

        swipeRefreshLayout.setOnRefreshListener {
            if (isNetworkAvailable) {
                hideError()
                webView.reload()
            } else {
                swipeRefreshLayout.isRefreshing = false
                showNoInternetError()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings

        // فعال‌سازی جاوااسکریپت و فضای ذخیره‌سازی محلی برای سبد خرید و حساب کاربری ووکامرس
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.allowFileAccess = true

        // تنظیمات کش برای عملکرد سریع‌تر در اینترنت ضعیف
        settings.cacheMode = if (isNetworkAvailable) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        // پشتیبانی از محتوای ترکیبی (Mixed Content) جهت سازگاری با برخی لودرهای درگاه پرداخت
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        // فعال‌سازی شتاب‌دهنده سخت‌افزاری
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // مدیریت کوکی‌ها (ضروری برای لاگین کاربران و درگاه پرداخت)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        cookieManager.flush()

        // سفارشی‌سازی User-Agent
        settings.userAgentString = settings.userAgentString + USER_AGENT_SUFFIX

        // مدیریت لودینگ و رویدادهای کروم
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.isVisible = true
                    progressBar.progress = newProgress
                } else {
                    progressBar.isVisible = false
                    swipeRefreshLayout.isRefreshing = false
                }
            }

            // پشتیبانی از آپلود فایل / تصویر در سایت
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
            }
        }

        // کلاینت وب‌ویو با مدیریت خطای حرفه‌ای
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lastLoadedUrl = url ?: TARGET_URL
                progressBar.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.isVisible = false
                swipeRefreshLayout.isRefreshing = false
            }

            // مدیریت لینک‌ها و درگاه‌های پرداخت شاپرک / زرین‌پال / تماس / واتساپ
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrlNavigation(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleUrlNavigation(url)
            }

            // مدیریت خطاهای شبکه و بارگذاری (اندروید 6 و بالاتر)
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // فقط برای صفحه اصلی خطا نمایش داده شود نه منابع جانبی مثل عکس‌های ثالث
                if (request?.isForMainFrame == true) {
                    val errorCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.errorCode ?: ERROR_UNKNOWN
                    } else {
                        ERROR_UNKNOWN
                    }
                    handleErrorCode(errorCode)
                }
            }

            // مدیریت خطاهای HTTP سرور (500, 502, 503)
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode ?: 0
                    if (statusCode in 500..599) {
                        showServerNotRespondingError(statusCode)
                    }
                }
            }

            // مدیریت خطای گواهینامه SSL
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // می‌توانید هشدار دهید یا در محیط پروداکشن کنسل کنید
                showSslError()
                handler?.cancel()
            }
        }
    }

    private fun handleUrlNavigation(url: String): Boolean {
        // ۱. هدایت پروتکل‌های خاص تماس، ایمیل و پیامک به اپ‌های بومی دستگاه
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            } catch (e: Exception) {
                return false
            }
        }

        // ۲. باز کردن لینک‌های دانلود مستقیم فایل (APK، PDF، ZIP) در مرورگر یا دانلود منیجر
        if (url.endsWith(".apk") || url.endsWith(".pdf") || url.endsWith(".zip") || url.endsWith(".rar")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            } catch (e: Exception) {
                return false
            }
        }

        // ۳. پشتیبانی از شمای intent:// برای درگاه‌های بانکی، اپ‌های شتاب و پرداخت
        if (url.startsWith("intent://")) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent != null) {
                    val packageManager = packageManager
                    val info = packageManager.resolveActivity(intent, 0)
                    if (info != null) {
                        startActivity(intent)
                        return true
                    } else {
                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                        if (!fallbackUrl.isNullOrEmpty()) {
                            webView.loadUrl(fallbackUrl)
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                // ادامه رندر معمولی در صورت بروز خطا
            }
        }

        // ۴. هدایت پروتکل‌های غیر http/https (مانند zarinpal://, shaparak://, tg://, whatsapp://)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            } catch (e: Exception) {
                return true // جلوگیری از نمایش خطای ERR_UNKNOWN_URL_SCHEME در وب‌ویو
            }
        }

        // صفحات عادی سایت و درگاه‌های تحت وب شاپرک درون وب‌ویو باز می‌شوند
        return false
    }

    private fun handleErrorCode(errorCode: Int) {
        progressBar.isVisible = false
        swipeRefreshLayout.isRefreshing = false

        when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT -> {
                showNoInternetError()
            }
            WebViewClient.ERROR_TIMEOUT -> {
                showTimeoutError()
            }
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> {
                showSslError()
            }
            else -> {
                showGeneralError()
            }
        }
    }

    private fun showNoInternetError() {
        layoutError.isVisible = true
        webView.isVisible = false
        imgErrorIcon.setImageResource(R.drawable.ic_error_wifi)
        textErrorTitle.text = getString(R.string.error_no_internet_title)
        textErrorDesc.text = getString(R.string.error_no_internet_desc)
    }

    private fun showServerNotRespondingError(statusCode: Int) {
        layoutError.isVisible = true
        webView.isVisible = false
        imgErrorIcon.setImageResource(R.drawable.ic_error_wifi)
        textErrorTitle.text = getString(R.string.error_server_title)
        textErrorDesc.text = getString(R.string.error_server_desc, statusCode)
    }

    private fun showTimeoutError() {
        layoutError.isVisible = true
        webView.isVisible = false
        imgErrorIcon.setImageResource(R.drawable.ic_error_wifi)
        textErrorTitle.text = getString(R.string.error_timeout_title)
        textErrorDesc.text = getString(R.string.error_timeout_desc)
    }

    private fun showSslError() {
        layoutError.isVisible = true
        webView.isVisible = false
        imgErrorIcon.setImageResource(R.drawable.ic_error_wifi)
        textErrorTitle.text = getString(R.string.error_ssl_title)
        textErrorDesc.text = getString(R.string.error_ssl_desc)
    }

    private fun showGeneralError() {
        layoutError.isVisible = true
        webView.isVisible = false
        imgErrorIcon.setImageResource(R.drawable.ic_error_wifi)
        textErrorTitle.text = getString(R.string.error_general_title)
        textErrorDesc.text = getString(R.string.error_general_desc)
    }

    private fun hideError() {
        layoutError.isVisible = false
        webView.isVisible = true
    }

    private fun loadWebsite(url: String) {
        if (!isNetworkConnected(this)) {
            showNoInternetError()
            return
        }
        hideError()
        webView.loadUrl(url)
    }

    // پایش زنده وضعیت اتصال به اینترنت (تغییر وای‌فای به دیتا و بالعکس)
    private fun setupNetworkMonitoring() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    isNetworkAvailable = true
                    offlineBanner.isVisible = false
                    // در صورت باز بودن صفحه خطا، هنگام وصل مجدد خودکار رفرش شود
                    if (layoutError.isVisible) {
                        hideError()
                        webView.reload()
                    }
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    isNetworkAvailable = false
                    offlineBanner.isVisible = true
                }
            }
        })
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // دکمه بازگشت: در صورت وجود تاریخچه در وب‌ویو به عقب برگردد، وگرنه با ۲ بار لمس خارج شود
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutError.isVisible) {
                    hideError()
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                    return
                }

                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    if (doubleBackToExitPressedOnce) {
                        finish()
                        return
                    }
                    doubleBackToExitPressedOnce = true
                    Toast.makeText(
                        this@MainActivity,
                        R.string.exit_double_press_notice,
                        Toast.LENGTH_SHORT
                    ).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        doubleBackToExitPressedOnce = false
                    }, 2000)
                }
            }
        })
    }
}
