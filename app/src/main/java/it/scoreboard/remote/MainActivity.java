package it.scoreboard.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    static final int BLUE = 0xFF1F6BFF;
    static final int RED = 0xFFE5202E;
    static final int GRAY = 0xFF3A4150;
    static final int LINE = 0xFF222A38;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    SharedPreferences prefs;
    final Handler ui = new Handler(Looper.getMainLooper());
    FrameLayout root;
    ScrollView homeScroll;
    WebView web;
    TextView pill, msg;
    GradientDrawable pillBg;
    EditText ipField, pinField;
    Button openBtn;
    int seq = 0;

    final Runnable tick = new Runnable() {
        @Override public void run() {
            check();
            ui.postDelayed(this, 3000);
        }
    };

    final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) { schedule(600); }
    };

    // ---------- ciclo di vita ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("remote", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        buildHome();
        buildWeb();
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (homeScroll.getVisibility() == View.VISIBLE) schedule(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        if (web.getVisibility() == View.VISIBLE) closeRemote();
        else super.onBackPressed();
    }

    // ---------- interfaccia: schermata principale ----------
    private void buildHome() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(20), dp(28), dp(20), dp(24));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("SCOREBOARD", 18, Color.WHITE);
        title.setLetterSpacing(0.15f);
        head.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));

        pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(30));
        pillBg.setColor(GRAY);
        pill = text("CONTROLLO", 12, Color.WHITE);
        pill.setBackground(pillBg);
        pill.setPadding(dp(14), dp(7), dp(14), dp(7));
        head.addView(pill, new LinearLayout.LayoutParams(WRAP, WRAP));
        h.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        h.addView(label("INDIRIZZO DEL PC"), lp(MATCH, WRAP, 32));
        ipField = field("192.168.x.x", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ipField.setText(prefs.getString("ip", ""));
        h.addView(ipField, lp(MATCH, WRAP, 8));

        h.addView(label("CODICE"), lp(MATCH, WRAP, 22));
        pinField = field("Facoltativo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pinField.setText(prefs.getString("pin", ""));
        h.addView(pinField, lp(MATCH, WRAP, 8));

        msg = text("", 14, RED);
        h.addView(msg, lp(MATCH, WRAP, 18));

        openBtn = new Button(this);
        openBtn.setText("APRI TELECOMANDO");
        openBtn.setAllCaps(true);
        openBtn.setTextColor(Color.WHITE);
        openBtn.setTextSize(15);
        openBtn.setTypeface(Typeface.DEFAULT_BOLD);
        openBtn.setLetterSpacing(0.08f);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BLUE);
        g.setCornerRadius(dp(12));
        openBtn.setBackground(g);
        openBtn.setEnabled(false);
        openBtn.setAlpha(0.35f);
        openBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openRemote(); }
        });
        h.addView(openBtn, lp(MATCH, dp(56), 24));

        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.addView(h, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(homeScroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        pinField.setEnabled(false);
        pinField.setAlpha(0.35f);
    }

    // ---------- interfaccia: telecomando (WebView) ----------
    private void buildWeb() {
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) closeRemote();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView v, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setNegativeButton("Annulla", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String url, String message, String def, final JsPromptResult result) {
                String saved = prefs.getString("pin", "");
                if (!saved.isEmpty()) {      // il telecomando chiede il PIN: rispondo con quello salvato
                    result.confirm(saved);
                    return true;
                }
                final EditText in = new EditText(MainActivity.this);
                in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(message)
                    .setView(in)
                    .setPositiveButton("OK", (d, w) -> result.confirm(in.getText().toString()))
                    .setNegativeButton("Annulla", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
                return true;
            }
        });

        web.setVisibility(View.GONE);
        root.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private void openRemote() {
        String ip = ipField.getText().toString().trim();
        prefs.edit().putString("ip", ip).putString("pin", pinField.getText().toString().trim()).apply();
        ui.removeCallbacks(tick);
        homeScroll.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        web.loadUrl(base(ip) + "/remote.html");
    }

    private void closeRemote() {
        web.loadUrl("about:blank");
        web.setVisibility(View.GONE);
        homeScroll.setVisibility(View.VISIBLE);
        schedule(0);
    }

    // ---------- controllo del server ----------
    private void schedule(long delay) {
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, delay);
    }

    private String base(String ip) {
        ip = ip.replace("http://", "").replace("/", "").trim();
        if (!ip.contains(":")) ip += ":3000";
        return "http://" + ip;
    }

    private HttpURLConnection open(String ip, String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base(ip) + path).openConnection();
        c.setConnectTimeout(2500);
        c.setReadTimeout(2500);
        return c;
    }

    // 0 = non connesso, 1 = connesso e codice ok, 2 = connesso ma codice errato
    private void check() {
        final String ip = ipField.getText().toString().trim();
        final String pin = pinField.getText().toString().trim();
        final int my = ++seq;
        if (ip.isEmpty()) { apply(0); return; }

        new Thread(new Runnable() {
            @Override public void run() {
                int result = 0;
                try {
                    HttpURLConnection c = open(ip, "/api/state");
                    int code = c.getResponseCode();
                    c.disconnect();
                    if (code == 200) {
                        // prova innocua: il server risponde 401 se il codice è sbagliato, 400 se è giusto
                        HttpURLConnection p = open(ip, "/api/action");
                        p.setRequestMethod("POST");
                        p.setDoOutput(true);
                        p.setRequestProperty("Content-Type", "application/json");
                        p.setRequestProperty("x-pin", pin);
                        OutputStream os = p.getOutputStream();
                        os.write("{\"action\":\"ping\"}".getBytes("UTF-8"));
                        os.close();
                        int pc = p.getResponseCode();
                        p.disconnect();
                        result = (pc == 401) ? 2 : 1;
                    }
                } catch (Exception e) {
                    result = 0;
                }
                final int r = result;
                ui.post(new Runnable() {
                    @Override public void run() { if (my == seq) apply(r); }
                });
            }
        }).start();
    }

    private void apply(int r) {
        boolean on = r != 0;
        pillBg.setColor(on ? BLUE : RED);
        pill.setText(on ? "CONNESSO" : "NON CONNESSO");
        pinField.setEnabled(on);
        pinField.setAlpha(on ? 1f : 0.35f);
        openBtn.setEnabled(r == 1);
        openBtn.setAlpha(r == 1 ? 1f : 0.35f);
        msg.setText(r == 0 ? "Server non raggiungibile" : (r == 2 ? "Codice errato o mancante" : ""));
    }

    // ---------- piccoli aiuti grafici ----------
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 12, 0xFF8A93A5);
        t.setLetterSpacing(0.15f);
        return t;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF5A6272);
        e.setTextColor(Color.WHITE);
        e.setTextSize(17);
        e.setInputType(type);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.BLACK);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(2), LINE);
        e.setBackground(g);
        e.setPadding(dp(14), dp(14), dp(14), dp(14));
        e.addTextChangedListener(watcher);
        return e;
    }
}
