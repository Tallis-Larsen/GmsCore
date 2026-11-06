/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.smartdevice

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import org.microg.gms.common.Utils
import org.microg.gms.profile.Build
import org.microg.gms.profile.ProfileManager

private const val TAG = "AccountChallengeActivity"
private const val COOKIE_SESSION_CHECKPOINT = "GASC"

/**
 * Shows the fallback challenges the server requested for accounts to transfer and collects the resulting session
 * checkpoints.
 */
class AccountChallengeActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var resultReceiver: ResultReceiver? = null
    private var accounts: List<String> = emptyList()
    private var urls: List<String> = emptyList()
    private val checkpoints = arrayListOf<String>()
    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        accounts = intent.getStringArrayExtra(EXTRA_ACCOUNTS).orEmpty().toList()
        urls = intent.getStringArrayExtra(EXTRA_URLS).orEmpty().toList()
        if (resultReceiver == null || accounts.isEmpty() || accounts.size != urls.size) {
            answered = true
            finish()
            return
        }
        progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
            isIndeterminate = true
        }
        webView = createWebView()
        setContentView(FrameLayout(this).apply {
            addView(progressBar)
            addView(webView)
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else reply(false)
            }
        })
        loadNextChallenge()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() = WebView(this).apply {
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        visibility = View.INVISIBLE
        ProfileManager.ensureInitialized(context)
        settings.userAgentString = Build.generateWebViewUserAgentString(settings.userAgentString)
        settings.javaScriptEnabled = true
        settings.setSupportMultipleWindows(false)
        settings.saveFormData = false
        settings.allowFileAccess = false
        settings.databaseEnabled = false
        settings.setNeedInitialFocus(false)
        settings.useWideViewPort = false
        settings.setSupportZoom(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        webViewClient = object : WebViewClientCompat() {
            override fun onPageFinished(view: WebView, url: String) {
                val checkpoint = getSessionCheckpoint(url)
                if (checkpoint.isNullOrEmpty()) {
                    progressBar.visibility = View.INVISIBLE
                    webView.visibility = View.VISIBLE
                    return
                }
                Log.d(TAG, "Challenge completed for ${accounts[checkpoints.size]}")
                checkpoints.add(checkpoint)
                loadNextChallenge()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
                if (!request.isForMainFrame) return
                Log.w(TAG, "Failed to load ${request.url}")
                reply(false)
            }
        }
    }

    private fun loadNextChallenge() {
        if (checkpoints.size == accounts.size) return reply(true)
        webView.visibility = View.INVISIBLE
        progressBar.visibility = View.VISIBLE
        val uri = Uri.parse(urls[checkpoints.size])
        val url = uri.buildUpon().clearQuery().apply {
            for (name in uri.queryParameterNames) {
                if (name != "hl") appendQueryParameter(name, uri.getQueryParameter(name))
            }
            appendQueryParameter("hl", Utils.getLocale(this@AccountChallengeActivity).toString())
        }.build().toString()
        CookieManager.getInstance().setAcceptCookie(true)
        if (SDK_INT >= 21) {
            CookieManager.getInstance().removeAllCookies { webView.loadUrl(url) }
        } else {
            @Suppress("DEPRECATION")
            CookieManager.getInstance().removeAllCookie()
            webView.loadUrl(url)
        }
    }

    private fun getSessionCheckpoint(url: String): String? {
        val cookies = CookieManager.getInstance().getCookie(url) ?: return null
        val checkpoint = cookies.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("$COOKIE_SESSION_CHECKPOINT=") }
            ?.substringAfter("=") ?: return null
        CookieManager.getInstance().setCookie(Uri.parse(url).host, "$COOKIE_SESSION_CHECKPOINT=;expires=Thu, 01 Jan 1970 00:00:00 UTC")
        return checkpoint
    }

    private fun reply(completed: Boolean) {
        if (answered) return
        answered = true
        resultReceiver?.send(
            if (completed) RESULT_OK else RESULT_CANCELED,
            bundleOf(EXTRA_ACCOUNTS to accounts.toTypedArray(), EXTRA_SESSION_CHECKPOINTS to checkpoints.toTypedArray())
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) reply(false)
    }

    companion object {
        const val EXTRA_ACCOUNTS = "accounts"
        const val EXTRA_URLS = "urls"
        const val EXTRA_SESSION_CHECKPOINTS = "sessionCheckpoints"
        const val EXTRA_RESULT_RECEIVER = "resultReceiver"
    }
}
