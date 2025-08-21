package com.yubytech.tracked.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.ui.platform.LocalContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SystemWebView(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }

    AndroidView(
        factory = { ctx ->
            val swipeRefreshLayout = SwipeRefreshLayout(ctx)
            val web = WebView(ctx).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isRefreshing = false
                    }
                }
                settings.javaScriptEnabled = true
                loadUrl(url)
            }
            webView = web
            swipeRefreshLayout.addView(web)
            swipeRefreshLayout.setOnRefreshListener {
                isRefreshing = true
                web.reload()
            }
            swipeRefreshLayout
        },
        update = { swipeRefreshLayout ->
            swipeRefreshLayout.isRefreshing = isRefreshing
        },
        modifier = modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    )
} 