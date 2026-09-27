package com.vmedios.timecastv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * TimeCastV — app WebView para celular y Android TV / Google TV.
 *
 * Botón "Atrás" (orden de prioridad):
 *   1. Si hay algo en pantalla completa → sale de pantalla completa.
 *   2. Si la página tiene historial → vuelve a la página anterior.
 *   3. Si no → pide presionar Atrás otra vez para cerrar la app.
 */
public class MainActivity extends Activity {

    private static final String HOME_URL = "https://timecastv.com/";

    private WebView webView;
    private ProgressBar progress;
    private FrameLayout fullscreenContainer;
    private View errorView;
    private Button retryButton;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private boolean isTv;
    private boolean pageFailed;
    private long lastBackPress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        isTv = detectTv();

        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        errorView = findViewById(R.id.error_view);
        retryButton = findViewById(R.id.retry_button);

        retryButton.setOnClickListener(v -> reload());

        setupWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
        webView.requestFocus();
        if (isTv) enterImmersive();
    }

    private boolean detectTv() {
        UiModeManager ui = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        boolean tvMode = ui != null
                && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        boolean leanback = getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        return tvMode || leanback;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // autoplay del canal
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString()
                + " TimeCastVApp/1.0" + (isTv ? " AndroidTV" : ""));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true); // embeds de YouTube

        webView.setBackgroundColor(0xFF000000);
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppChromeClient());
    }

    // ------------------------------------------------------------------
    // Navegación y errores
    // ------------------------------------------------------------------
    private class AppWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl().toString());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            pageFailed = false;
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            if (!pageFailed) {
                errorView.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
            if (isTv) {
                // Marca el documento como TV para los estilos del plugin
                view.evaluateJavascript(
                        "document.documentElement.classList.add('android-tv');"
                      + "document.body&&document.body.classList.add('android-tv');",
                        null);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame()) {
                showError();
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            if (Build.VERSION.SDK_INT < 23) showError();
        }
    }

    /** http/https se abren dentro de la app; otros esquemas (whatsapp, tel…) afuera. */
    private boolean handleUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            // No hay app que lo abra (común en TV): se ignora
        }
        return true;
    }

    private void showError() {
        pageFailed = true;
        webView.setVisibility(View.INVISIBLE);
        errorView.setVisibility(View.VISIBLE);
        retryButton.requestFocus();
    }

    private void reload() {
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        String current = webView.getUrl();
        if (current == null || current.startsWith("about:") || current.startsWith("data:")) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.reload();
        }
        webView.requestFocus();
    }

    // ------------------------------------------------------------------
    // Pantalla completa (videos y el reproductor de TimeCastV)
    // ------------------------------------------------------------------
    private class AppChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            fullscreenContainer.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
            enterImmersive();
            view.requestFocus();
        }

        @Override
        public void onHideCustomView() {
            exitCustomView();
        }

        /** Evita el ícono gris de "play" que muestra el WebView antes del video. */
        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    private void exitCustomView() {
        if (customView == null) return;
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        customView = null;
        if (customViewCallback != null) {
            WebChromeClient.CustomViewCallback cb = customViewCallback;
            customViewCallback = null;
            cb.onCustomViewHidden();
        }
        webView.setVisibility(View.VISIBLE);
        webView.requestFocus();
        if (!isTv) exitImmersive();
    }

    @SuppressWarnings("deprecation")
    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @SuppressWarnings("deprecation")
    private void exitImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    // ------------------------------------------------------------------
    // Botón "Atrás" del control remoto / del celular
    // ------------------------------------------------------------------
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // 1) Pantalla completa nativa del WebView
        if (customView != null) {
            exitCustomView();
            return;
        }
        // 2) Pantalla completa hecha solo con JS/CSS (respaldo), luego historial
        webView.evaluateJavascript(
                "(function(){if(document.fullscreenElement){document.exitFullscreen();return 'fs';}return 'no';})()",
                result -> {
                    if ("\"fs\"".equals(result)) return;
                    if (errorView.getVisibility() != View.VISIBLE && webView.canGoBack()) {
                        webView.goBack();
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastBackPress < 2000) {
                        finish();
                    } else {
                        lastBackPress = now;
                        Toast.makeText(this, R.string.press_again, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        if (isTv || customView != null) enterImmersive();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
