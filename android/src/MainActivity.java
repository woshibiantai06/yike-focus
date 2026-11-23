package app.yike.focus;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net/yike/";
    private WebView webView;

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
            setContentView(webView);
            webView.loadDataWithBaseURL(APP_ORIGIN, page, "text/html", "UTF-8", null);
        } catch (IOException exception) {
            TextView message = new TextView(this);
            message.setText(R.string.offline_page_error);
            message.setGravity(Gravity.CENTER);
            setContentView(message);
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
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
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
