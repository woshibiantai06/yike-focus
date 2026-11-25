package app.yike.focus;

import android.app.Activity;
import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebResourceRequest;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net/yike/";
    private WebView webView;
    private static final int NOTIFICATION_PERMISSION = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String page = readPage();
            webView = new WebView(this);
            webView.setBackgroundColor(Color.rgb(163, 57, 72));
            WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setBlockNetworkLoads(true);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setUseWideViewPort(true);
            webView.setWebViewClient(new OfflineClient());
            webView.addJavascriptInterface(new TimerBridge(), "YikeAndroid");
            setContentView(webView);
            webView.loadDataWithBaseURL(APP_ORIGIN, page, "text/html", "UTF-8", null);
        } catch (IOException exception) {
            TextView message = new TextView(this);
            message.setText(R.string.offline_page_error);
            message.setGravity(Gravity.CENTER);
            setContentView(message);
        }
    }

    private final class TimerBridge {
        @JavascriptInterface
        public void updateTimer(String json) {
            runOnUiThread(() -> {
                try {
                    TimerAlerts.update(MainActivity.this, new JSONObject(json));
                } catch (JSONException ignored) {
                    // Leave the existing timer intact if a message is incomplete.
                }
            });
        }

        @JavascriptInterface
        public void requestNotifications() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
                if (isFinishing() || TimerAlerts.preferences(MainActivity.this).getBoolean("permission-requested", false)) return;
                TimerAlerts.preferences(MainActivity.this).edit().putBoolean("permission-requested", true).apply();
                requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, NOTIFICATION_PERMISSION);
            });
        }

        @JavascriptInterface
        public void ringBreak(String id, long until, boolean sound, boolean english) {
            runOnUiThread(() -> TimerAlerts.ring(MainActivity.this, id, until, sound, english, false));
        }

        @JavascriptInterface
        public void ringFocus(String id, long until, boolean sound, boolean english) {
            runOnUiThread(() -> TimerAlerts.ring(MainActivity.this, id, until, sound, english, true));
        }

        @JavascriptInterface
        public void chimeFocus(String id, long dueAt) {
            runOnUiThread(() -> TimerAlerts.chime(MainActivity.this, id, dueAt));
        }

        @JavascriptInterface
        public String timerControls(long after) {
            android.content.SharedPreferences saved = TimerAlerts.preferences(MainActivity.this);
            return saved.getLong("control-seq", 0) > after ? saved.getString("controls", "") : "";
        }

        @JavascriptInterface
        public String dismissedAlarm() {
            return TimerAlerts.preferences(MainActivity.this).getString("dismissed", "");
        }

        @JavascriptInterface
        public void dismissAlarm(String id) {
            runOnUiThread(() -> TimerAlerts.dismiss(MainActivity.this, id));
        }

        @JavascriptInterface
        public boolean floatingEnabled() {
            return TimerAlerts.preferences(MainActivity.this).getBoolean("floating", false)
                && Settings.canDrawOverlays(MainActivity.this);
        }

        @JavascriptInterface
        public void setFloating(boolean enabled) {
            runOnUiThread(() -> {
                TimerAlerts.preferences(MainActivity.this).edit().putBoolean("floating", enabled).apply();
                if (enabled && !Settings.canDrawOverlays(MainActivity.this)) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException ignored) {
                        // The timer notification remains available.
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION && webView != null) {
            webView.evaluateJavascript("typeof syncNativeTimer === 'function' && syncNativeTimer()", null);
        }
    }

    private static final class OfflineClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true;
        }
    }

    private String readPage() throws IOException {
        try (InputStream page = getAssets().open("index.html");
             ByteArrayOutputStream contents = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = page.read(buffer)) != -1) {
                contents.write(buffer, 0, count);
            }
            return new String(contents.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        TimerAlerts.setAppVisible(this, false);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TimerAlerts.setAppVisible(this, true);
        if (webView != null) {
            webView.onResume();
            webView.evaluateJavascript("typeof refresh === 'function' && refresh(); typeof updateFloatingSetting === 'function' && updateFloatingSetting()", null);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript("typeof handleAppBack === 'function' && handleAppBack()", handled -> {
            if (!"true".equals(handled)) super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
