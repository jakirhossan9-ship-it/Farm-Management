package com.mashallahagro.app

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequest = 1001
    private val cameraRequest = 1002
    private val notificationRequest = 1003
    private val notificationChannelId = "masaallah_agro_tasks"
    private val homeUrl = "https://masaallahagro.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()

        webView = findViewById(R.id.webView)
        configureWebView()
        webView.loadUrl(homeUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.userAgentString = settings.userAgentString + " MasaallahAgroAndroid/1.2"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
        }

        // Bridge between the WordPress page and native Android notifications.
        webView.addJavascriptInterface(AndroidNotificationBridge(), "MasaallahAndroid")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectNotificationBridge()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "http" || uri.scheme == "https") {
                    false
                } else {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePath: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePath
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try {
                    startActivityForResult(intent, fileChooserRequest)
                    true
                } catch (_: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), cameraRequest)
        }
    }

    /**
     * The web page's Notification API is not reliable inside Android WebView.
     * Capture the notification button click and route it to native Android.
     */
    private fun injectNotificationBridge() {
        val script = """
            (function(){
                if(window.__ma2NativeNotificationBridgeInstalled)return;
                window.__ma2NativeNotificationBridgeInstalled=true;

                document.addEventListener('click', function(e){
                    var el=e.target;
                    while(el && el!==document.body && el.tagName!=='BUTTON') el=el.parentElement;
                    if(!el || el.tagName!=='BUTTON') return;

                    var txt=(el.innerText || el.textContent || '').trim();
                    if(txt.indexOf('নোটিফিকেশন চালু করুন')!==-1 || txt.indexOf('Notification')!==-1){
                        if(window.MasaallahAndroid && window.MasaallahAndroid.enableNotifications){
                            e.preventDefault();
                            e.stopPropagation();
                            if(e.stopImmediatePropagation)e.stopImmediatePropagation();
                            window.MasaallahAndroid.enableNotifications();
                        }
                    }
                }, true);

                window.MasaallahNativeNotification = {
                    enabled: function(){
                        try{
                            localStorage.setItem('ma2_notify_enabled','1');
                            var b=document.getElementById('ma2NotifyBtn');
                            if(b){b.textContent='🔔 চালু আছে'; b.disabled=false;}
                            var m=document.getElementById('ma2NotifyMsg');
                            if(m){m.textContent='✅ নোটিফিকেশন চালু হয়েছে।'; m.className='ma2-notify-msg';}
                        }catch(x){}
                    },
                    denied: function(){
                        try{
                            var m=document.getElementById('ma2NotifyMsg');
                            if(m){m.textContent='নোটিফিকেশন অনুমতি দেওয়া হয়নি। Android Settings থেকে Notifications Allow করুন।'; m.className='ma2-notify-msg err';}
                        }catch(x){}
                    }
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun requestNativeNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                notificationRequest
            )
        } else {
            notificationsGranted()
        }
    }

    private fun notificationsGranted() {
        createNotificationChannel()
        try {
            val notification = NotificationCompat.Builder(this, notificationChannelId)
                .setSmallIcon(R.drawable.app_logo)
                .setContentTitle("🔔 Masaallah Agro")
                .setContentText("নোটিফিকেশন সফলভাবে চালু হয়েছে।")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify(10031, notification)
        } catch (_: Exception) {
        }

        webView.post {
            webView.evaluateJavascript(
                "window.MasaallahNativeNotification && window.MasaallahNativeNotification.enabled();",
                null
            )
        }
    }

    private fun notificationsDenied() {
        webView.post {
            webView.evaluateJavascript(
                "window.MasaallahNativeNotification && window.MasaallahNativeNotification.denied();",
                null
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Masaallah Agro কাজের নোটিফিকেশন",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "খামারের আগামীকালের কাজের নোটিফিকেশন"
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    inner class AndroidNotificationBridge {
        @JavascriptInterface
        fun enableNotifications() {
            runOnUiThread { requestNativeNotifications() }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == notificationRequest) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notificationsGranted()
            } else {
                notificationsDenied()
            }
        }
    }

    @Deprecated("Deprecated in Android SDK, retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequest) {
            val result = if (resultCode == Activity.RESULT_OK && data != null) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else null
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        webView.removeJavascriptInterface("MasaallahAndroid")
        webView.destroy()
        super.onDestroy()
    }
}
