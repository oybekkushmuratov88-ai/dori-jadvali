package io.github.oybekkushmuratov88.dori;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * The Android app is a window onto the web app: it opens the published site,
 * which keeps working offline through its service worker, and adds what a plain
 * browser tab lacks here: the camera for medicine photos and a share sheet for
 * backup and history files.
 */
public class MainActivity extends Activity {
    static final String START_URL = "https://oybekkushmuratov88-ai.github.io/dori-jadvali/";
    static final String APP_HOST = "oybekkushmuratov88-ai.github.io";
    static final String APP_PATH = "/dori-jadvali/";
    static final int REQ_FILE = 41;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " DoriJadvaliAndroid/1");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openOutside(request.getUrl());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOffline();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return chooseFile(callback, params);
            }
        });
        web.addJavascriptInterface(new Bridge(), "DoriAndroid");
        web.loadUrl(START_URL);
    }

    /** Pages of the app stay inside; any other link opens in the phone's browser or app. */
    boolean openOutside(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("https".equals(scheme) && APP_HOST.equals(uri.getHost()) && path.startsWith(APP_PATH)) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            // Nothing on the phone can open it; stay on the current page.
        }
        return true;
    }

    void showOffline() {
        String html = "<!doctype html><html lang=\"uz\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "font:16px/1.5 system-ui,sans-serif;background:#EDF1F0;color:#0F2320;padding:24px;box-sizing:border-box;text-align:center}"
                + "@media(prefers-color-scheme:dark){body{background:#0D1513;color:#E4EEEB}}"
                + "a{display:inline-block;margin-top:16px;padding:12px 22px;border-radius:12px;background:#2446C8;color:#fff;"
                + "text-decoration:none;font-weight:600}</style></head><body><div>"
                + "<h2>Internet yo‘q</h2>"
                + "<p>Ilovani birinchi marta ochish uchun internet kerak. Keyin u internetsiz ham ishlaydi.</p>"
                + "<a href=\"" + START_URL + "\">Qayta urinish</a></div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    /** Photo inputs offer the camera and the gallery; other inputs open the file picker. */
    boolean chooseFile(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = callback;
        cameraUri = null;

        boolean image = false;
        String[] types = params.getAcceptTypes();
        if (types != null) for (String t : types) if (t != null && t.startsWith("image")) image = true;

        Intent camera = null;
        if (image) {
            cameraUri = SharedFiles.newPhotoUri(this);
            camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.setClipData(ClipData.newRawUri("", cameraUri));
            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        Intent intent;
        if (camera != null && params.isCaptureEnabled()) {
            intent = camera;
        } else {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType(image ? "image/*" : "*/*");
            intent = Intent.createChooser(pick, image ? "Rasm tanlang" : "Faylni tanlang");
            if (camera != null) intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {camera});
        }
        try {
            startActivityForResult(intent, REQ_FILE);
        } catch (ActivityNotFoundException e) {
            fileCallback = null;
            callback.onReceiveValue(null);
            Toast.makeText(this, "Bu telefonda mos ilova topilmadi", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FILE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        ValueCallback<Uri[]> callback = fileCallback;
        fileCallback = null;
        if (callback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            Uri picked = data != null ? data.getData() : null;
            if (picked != null) result = new Uri[] {picked};
            else if (cameraUri != null && SharedFiles.hasContent(this, cameraUri)) result = new Uri[] {cameraUri};
        }
        callback.onReceiveValue(result);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** Called from the page: window.DoriAndroid.shareFile(name, mime, base64). */
    class Bridge {
        @JavascriptInterface
        public void shareFile(final String name, final String mime, final String base64) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                        Uri uri = SharedFiles.save(MainActivity.this, name, bytes);
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType(mime == null || mime.isEmpty() ? SharedFiles.mimeFor(name) : mime);
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.setClipData(ClipData.newRawUri(name, uri));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Faylni saqlash yoki yuborish"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Faylni tayyorlab bo‘lmadi", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public int version() {
            return 1;
        }
    }
}
