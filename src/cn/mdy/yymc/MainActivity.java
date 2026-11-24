package cn.mdy.yymc;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.mdy.yymc.relay.Relay;

// YYMC 安卓版：和桌面 exe 一个形态 —— 设备内起一个只绑 127.0.0.1 的中继，
// 由它提供静态资源、/rooms 的 HTTP 反代与 /rooms/p2p 的 WebSocket 隧道。
// 页面因此是同源 http 回环，登录和联机都不再经过云端中转。
public class MainActivity extends Activity {

    private WebView web;
    private Relay relay;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        String url;
        try {
            relay = new Relay(new AndroidAssets(getAssets()), 8720);
            int port = relay.start();
            url = "http://127.0.0.1:" + port + "/index.html";
        } catch (Exception e) {
            showError("中继启动失败：" + e);
            return;
        }

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        setContentView(web);
        web.loadUrl(url);
    }

    private void showError(String msg) {
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(Color.parseColor("#FF8080"));
        t.setBackgroundColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(32, 64, 32, 32);
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        setContentView(sv);
    }

    private void hideSystemUi() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        if (relay != null) {
            relay.stop();
            relay = null;
        }
        super.onDestroy();
    }
}
