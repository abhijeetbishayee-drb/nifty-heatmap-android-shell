package io.github.abhijeetbishayee.heatmap.shell;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * The whole app: one WebView showing the live web boards.
 *
 * The boards themselves (Nifty 50, F&O Sectors, RRG, and the sibling Rollover /
 * PCR / 44 EMA boards) live on GitHub Pages and are NOT reimplemented here. That
 * is deliberate - the earlier Kivy app re-coded the heatmap natively and drifted
 * from the web version (wrong mover count, missing range bars). Showing the web
 * pages means a change to the website reaches the app with no app release.
 *
 * Both consumer apps (the sideload APK repo and the Play AAB repo) subclass this
 * activity; anything that must differ between them is a protected hook.
 */
public class ShellActivity extends Activity {

    protected static final String HOST = "abhijeetbishayee-drb.github.io";

    /** Path prefixes on HOST that stay inside the app. Every other link - an NSE
     *  quote page from a tile tap, say - opens in the user's browser, which is
     *  also the only place NSE's site works (it blocks embedded WebViews). */
    private static final String[] IN_APP_PATHS = {
            "/nifty-heatmap-web/",
            "/fno-rollover/",
            "/nifty-pcr-tracker/",
            "/nifty-ema-board/",
    };

    private WebView web;
    private View errorView;
    private ProgressBar progress;
    private boolean mainFrameFailed;

    /** First page shown on a cold start. */
    protected String startUrl() {
        return "https://" + HOST + "/nifty-heatmap-web/index.html";
    }

    /** Tag appended to the WebView user agent, so the pages can tell they are
     *  inside the app if they ever need to. */
    protected String userAgentTag() {
        return "NiftyHeatmapApp";
    }

    /** Scheme of the links {@link #extraNavEntries()} adds. Never leaves the app. */
    protected static final String APP_SCHEME = "heatmapapp";

    /**
     * Native entries this app adds to the end of each board's nav bar, as
     * {id, label} pairs - e.g. the Play build's "Pro" screen. Tapping one calls
     * {@link #onExtraNavEntry(String)} with its id. The shell itself adds none,
     * so the sideload APK shows the boards' nav exactly as the website does.
     */
    protected String[][] extraNavEntries() {
        return new String[0][];
    }

    /** Called when the user taps an entry from {@link #extraNavEntries()}. */
    protected void onExtraNavEntry(String id) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shell_activity);

        View root = findViewById(R.id.shell_root);
        web = findViewById(R.id.shell_web);
        errorView = findViewById(R.id.shell_error);
        progress = findViewById(R.id.shell_progress);

        applySystemBarInsets(root);
        configureWebView();
        registerBackHandling();

        findViewById(R.id.shell_retry).setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            String url = web.getUrl();
            if (url == null || url.startsWith("about:")) web.loadUrl(startUrl());
            else web.reload();
        });

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(startUrl());
        }
    }

    /** Targeting API 35+ makes the app draw edge-to-edge on Android 15+, under
     *  the status and navigation bars. Pad the content clear of them there;
     *  older versions still lay the window out below the bars themselves.
     *  Public so an app's own screens (e.g. Play's Pro screen) match. */
    public static void applySystemBarInsets(View root) {
        if (Build.VERSION.SDK_INT < 35) return;
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    private void configureWebView() {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // boards remember view/period/tail choices
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);   // target=_blank links come through shouldOverrideUrlLoading
        s.setBuiltInZoomControls(false);      // RRG handles its own pinch/drag
        s.setUserAgentString(s.getUserAgentString() + " " + userAgentTag());
        web.setBackgroundColor(getColor(R.color.shell_bg));   // no white flash before first paint

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (APP_SCHEME.equals(uri.getScheme())) {
                    onExtraNavEntry(uri.getHost());
                    return true;
                }
                if (isInApp(uri)) return false;
                openExternally(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameFailed = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!mainFrameFailed) errorView.setVisibility(View.GONE);
                if (url != null && isInApp(Uri.parse(url))) injectExtraNavEntries(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // A failed data.json poll or font is the page's own business; only
                // a page that could not load at all gets the native retry screen.
                if (!request.isForMainFrame()) return;
                mainFrameFailed = true;
                errorView.setVisibility(View.VISIBLE);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    /** Appends {@link #extraNavEntries()} to the page's nav bar. The heatmap
     *  pages use .nav and the sibling boards .xnav; a page with neither is left
     *  alone. Idempotent, since onPageFinished can fire more than once. */
    private void injectExtraNavEntries(WebView view) {
        String[][] entries = extraNavEntries();
        if (entries.length == 0) return;
        org.json.JSONArray json = new org.json.JSONArray();
        for (String[] e : entries) {
            json.put(new org.json.JSONArray().put(e[0]).put(e[1]));
        }
        view.evaluateJavascript("(function(entries){"
                + "var nav=document.querySelector('.nav,.xnav');"
                + "if(!nav||nav.querySelector('[data-app-entry]'))return;"
                + "entries.forEach(function(e){"
                + "var a=document.createElement('a');"
                + "a.href='" + APP_SCHEME + "://'+e[0];"
                + "a.textContent=e[1];"
                + "a.setAttribute('data-app-entry',e[0]);"
                + "nav.appendChild(a);});"
                + "})(" + json + ");", null);
    }

    static boolean isInApp(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) return false;
        String path = uri.getPath() == null ? "" : uri.getPath();
        for (String prefix : IN_APP_PATHS) {
            if (path.startsWith(prefix) || (path + "/").equals(prefix)) return true;
        }
        return false;
    }

    private void openExternally(Uri uri) {
        String scheme = uri.getScheme();
        // Only hand ordinary links to other apps; never intent:, file:, javascript: etc.
        if (!"https".equals(scheme) && !"http".equals(scheme) && !"mailto".equals(scheme)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.shell_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    /** Back walks the board history (e.g. RRG -> Sectors -> Nifty 50) before
     *  leaving the app. From API 33 the system delivers back through
     *  OnBackInvokedDispatcher, and apps targeting 36 never get onBackPressed. */
    private void registerBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {   // API 26-32 only
        handleBack();
    }

    private void handleBack() {
        if (errorView.getVisibility() != View.VISIBLE && web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    // The boards poll for fresh data on timers; pause them while the app is in
    // the background so it does not keep fetching (and draining battery) unseen.
    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
    }

    @Override
    protected void onPause() {
        web.onPause();
        web.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
