package com.snyal.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Окно приложения: показывает веб-дашборд Snyal, который крутится в фоновом сервисе. */
public class MainActivity extends Activity {
    private WebView web;
    private TextView status;
    private String shownUrl;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            String url = SnyalService.url;
            String err = SnyalService.error;
            if (url != null) {
                if (!url.equals(shownUrl)) {
                    shownUrl = url;
                    status.setVisibility(View.GONE);
                    web.loadUrl(url);
                }
                return;                       // дашборд поднят — опрос больше не нужен
            }
            shownUrl = null;
            if (err != null) {
                status.setText("Не удалось запустить Python:\n" + err);
                status.setVisibility(View.VISIBLE);
            }
            ui.postDelayed(this, 500);
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String h = u.getHost();
                if ("127.0.0.1".equals(h) || "localhost".equals(h)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));   // чужие ссылки — в браузер
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedError(final WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    ui.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            v.reload();
                        }
                    }, 1500);
                }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setTextColor(Color.rgb(255, 59, 48));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(48, 48, 48, 48);
        status.setText("SNYAL запускается…\nЕсли появится окно Magisk / KernelSU — выдай root.");
        root.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        // Android 15 (targetSdk 35) всегда рисует приложение под системными панелями —
        // сдвигаем содержимое на размер вырезов, статус-бара и клавиатуры.
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                    v.setPadding(i.left, i.top, i.right, i.bottom);
                    return WindowInsets.CONSUMED;
                }
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        startForegroundService(new Intent(this, SnyalService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(poll);
        ui.post(poll);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            moveTaskToBack(true);             // сервис продолжает работать
        }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(poll);
        web.destroy();
        super.onDestroy();
    }
}
