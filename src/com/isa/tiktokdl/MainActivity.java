package com.isa.tiktokdl;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";
    private static final int PICK_BG = 42;
    private static final int PINK = 0xFFFE2C55;
    private static final int CYAN = 0xFF25F4EE;

    // id, nombre, color inicio, color fin
    private static final String[][] BGS = {
        {"noche", "Noche", "#0B0B10", "#1B1B2A"},
        {"neon", "Neón", "#3A0A1E", "#062A33"},
        {"aurora", "Aurora", "#0B3D2E", "#1B1B4B"},
        {"oceano", "Océano", "#0F2027", "#2C5364"},
        {"atardecer", "Atardecer", "#FE2C55", "#FF9A44"},
        {"lavanda", "Lavanda", "#E0C3FC", "#8EC5FC"},
        {"nieve", "Nieve", "#F5F5F7", "#E2E2EA"},
    };
    // alias, drawable, nombre
    private static final String[][] ICONS = {
        {"IconDefault", "ic_default", "Glitch"},
        {"IconPink", "ic_pink", "Rosa"},
        {"IconCyan", "ic_cyan", "Cian"},
        {"IconMono", "ic_mono", "Mono"},
    };

    private SharedPreferences prefs;
    private boolean dark;
    private int cText, cSub, cCard, cField;

    private EditText input;
    private TextView btnDownload, status;
    private ProgressBar progress;
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        buildUi();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    // ---------- Interfaz ----------

    private void buildUi() {
        String keepText = input != null ? input.getText().toString() : "";
        CharSequence keepStatus = status != null ? status.getText() : null;

        dark = !"light".equals(prefs.getString("theme", "dark"));
        cText = dark ? 0xFFFFFFFF : 0xFF141418;
        cSub = dark ? 0xB3FFFFFF : 0x99141418;
        cCard = dark ? 0xCC15151C : 0xE6FFFFFF;
        cField = dark ? 0xFF262630 : 0xFFF0F0F4;

        FrameLayout root = new FrameLayout(this);
        int barColor = applyBackground(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(28), dp(20), dp(24));
        scroll.addView(col);
        root.addView(scroll);

        // Encabezado
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(iconRes(currentIcon()[1]));
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text("TikTok DL", 24, cText, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1);
        tlp.leftMargin = dp(12);
        header.addView(title, tlp);

        TextView gear = text("⚙", 20, cText, false);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(ripple(rounded(cCard, 22), 0x33FFFFFF));
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        header.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(header);

        TextView sub = text("TikTok, Instagram, X y Facebook", 14, cSub, false);
        sub.setPadding(0, dp(6), 0, 0);
        col.addView(sub);

        // Tarjeta principal
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(cCard, 24));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(28);
        col.addView(card, clp);

        card.addView(text("Enlace del video", 13, cSub, true));

        input = new EditText(this);
        input.setHint("Pega el enlace del video");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextColor(cText);
        input.setHintTextColor(cSub);
        input.setTextSize(15);
        input.setBackground(rounded(cField, 14));
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        input.setText(keepText);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = dp(8);
        card.addView(input, ilp);

        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(14);
        card.addView(row, rlp);

        TextView btnPaste = button("Pegar", cField, cText);
        btnPaste.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { paste(); }
        });
        row.addView(btnPaste, new LinearLayout.LayoutParams(0, -2, 1));

        btnDownload = button("Descargar", PINK, Color.WHITE);
        btnDownload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { download(); }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -2, 1.5f);
        dlp.leftMargin = dp(10);
        row.addView(btnDownload, dlp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(CYAN));
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(6));
        plp.topMargin = dp(14);
        card.addView(progress, plp);

        status = text(keepStatus != null ? keepStatus.toString()
            : "Tip: en la app del video toca Compartir → TikTok DL", 14, cSub, false);
        status.setPadding(0, dp(10), 0, 0);
        card.addView(status);
        setBusy(busy);

        TextView foot = text("Se guardan en Descargas, en la carpeta de cada red", 12, cSub, false);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(20);
        col.addView(foot, flp);

        setContentView(root);

        getWindow().setStatusBarColor(barColor);
        getWindow().setNavigationBarColor(barColor);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /** Pone el fondo elegido y devuelve el color para las barras del sistema. */
    private int applyBackground(FrameLayout root) {
        String bg = prefs.getString("bg", dark ? "noche" : "nieve");
        if ("custom".equals(bg)) {
            Bitmap bmp = loadBitmap(prefs.getString("bgUri", null));
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bmp);
                root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                View scrim = new View(this);
                scrim.setBackgroundColor(dark ? 0x8C000000 : 0x59FFFFFF);
                root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
                return dark ? Color.BLACK : Color.WHITE;
            }
            bg = dark ? "noche" : "nieve";
        }
        String[] p = findBg(bg);
        int c1 = Color.parseColor(p[2]), c2 = Color.parseColor(p[3]);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2}));
        return c1;
    }

    // ---------- Personalización ----------

    private AlertDialog settingsDialog;

    private void openSettings() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(8), dp(20), dp(8));

        // Tema
        v.addView(section("Tema"));
        LinearLayout themes = new LinearLayout(this);
        themes.addView(chip("Oscuro", dark, new Runnable() {
            public void run() { setPref("theme", "dark"); }
        }));
        themes.addView(chip("Claro", !dark, new Runnable() {
            public void run() { setPref("theme", "light"); }
        }));
        v.addView(themes);

        // Fondo
        v.addView(section("Fondo"));
        LinearLayout bgs = new LinearLayout(this);
        String curBg = prefs.getString("bg", dark ? "noche" : "nieve");
        for (final String[] p : BGS) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(p[2]), Color.parseColor(p[3])});
            bgs.addView(swatch(g, null, p[1], p[0].equals(curBg), new Runnable() {
                public void run() { setPref("bg", p[0]); }
            }));
        }
        bgs.addView(swatch(rounded(cField, 16), "+", "Galería", "custom".equals(curBg), new Runnable() {
            public void run() { pickBackground(); }
        }));
        v.addView(hscroll(bgs));

        // Icono
        v.addView(section("Icono de la app"));
        LinearLayout icons = new LinearLayout(this);
        String curIcon = currentIcon()[0];
        for (final String[] ic : ICONS) {
            Drawable d = getResources().getDrawable(iconRes(ic[1]), null);
            icons.addView(swatch(d, null, ic[2], ic[0].equals(curIcon), new Runnable() {
                public void run() { setIcon(ic[0]); }
            }));
        }
        v.addView(hscroll(icons));

        // Cuenta de Instagram
        v.addView(section("Cuenta de Instagram"));
        final boolean logged = prefs.getString("igCookies", "").contains("sessionid=");
        LinearLayout acc = new LinearLayout(this);
        acc.addView(chip(logged ? "Cerrar sesión" : "Iniciar sesión", !logged, new Runnable() {
            public void run() {
                if (logged) igLogout(); else igLogin();
            }
        }));
        v.addView(acc);
        TextView note = text(logged
            ? "Sesión activa: permite reels, historias y cuentas privadas que sigues."
            : "Necesaria cuando Instagram pide login. La contraseña se escribe en la página oficial de Instagram; "
              + "aquí solo se guarda la cookie de sesión en tu teléfono. Recomendado: usar una cuenta secundaria.",
            12, cSub, false);
        note.setPadding(0, dp(8), 0, 0);
        v.addView(note);

        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        settingsDialog = new AlertDialog.Builder(this, dark
                ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Personalizar")
            .setView(sv)
            .setPositiveButton("Listo", null)
            .show();
    }

    private void refreshSettings() {
        if (settingsDialog != null) settingsDialog.dismiss();
        buildUi();
        openSettings();
    }

    private void setPref(String key, String value) {
        prefs.edit().putString(key, value).apply();
        refreshSettings();
    }

    private void pickBackground() {
        if (settingsDialog != null) settingsDialog.dismiss();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, PICK_BG);
        } catch (Exception e) {
            toast("No se encontró una galería");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_BG || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        prefs.edit().putString("bg", "custom").putString("bgUri", uri.toString()).apply();
        buildUi();
    }

    private void setIcon(String alias) {
        PackageManager pm = getPackageManager();
        // Primero activa el nuevo para no quedar sin icono
        for (int pass = 0; pass < 2; pass++) {
            for (String[] ic : ICONS) {
                boolean on = ic[0].equals(alias);
                if ((pass == 0) != on) continue;
                pm.setComponentEnabledSetting(
                    new ComponentName(getPackageName(), getPackageName() + "." + ic[0]),
                    on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                       : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            }
        }
        prefs.edit().putString("icon", alias).apply();
        toast("El icono puede tardar unos segundos en cambiar");
        refreshSettings();
    }

    private String[] currentIcon() {
        String a = prefs.getString("icon", "IconDefault");
        for (String[] ic : ICONS) if (ic[0].equals(a)) return ic;
        return ICONS[0];
    }

    private String[] findBg(String id) {
        for (String[] p : BGS) if (p[0].equals(id)) return p;
        return BGS[0];
    }

    // ---------- Sesión de Instagram ----------

    private void igLogin() {
        if (settingsDialog != null) settingsDialog.dismiss();
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        final WebView w = new WebView(this);
        WebSettings ws = w.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        final String ua = ws.getUserAgentString().replace("; wv", "");
        ws.setUserAgentString(ua);
        final CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
        w.setWebViewClient(new WebViewClient() {
            boolean saved;

            void check() {
                String c = cm.getCookie("https://www.instagram.com");
                if (saved || c == null || !c.contains("sessionid=")) return;
                saved = true;
                prefs.edit().putString("igCookies", c).putString("igUa", ua).apply();
                cm.flush();
                d.dismiss();
                toast("Sesión de Instagram guardada");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                check();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                check();
            }
        });
        d.setContentView(w, new ViewGroup.LayoutParams(-1, -1));
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface di) {
                w.stopLoading();
                w.destroy();
            }
        });
        d.show();
        w.loadUrl("https://www.instagram.com/accounts/login/");
    }

    private void igLogout() {
        prefs.edit().remove("igCookies").remove("igUa").apply();
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
        toast("Sesión cerrada");
        refreshSettings();
    }

    // ---------- Descarga ----------

    private void handleIntent(Intent i) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String url = extractUrl(i.getStringExtra(Intent.EXTRA_TEXT));
            if (url != null) {
                input.setText(url);
                download();
            }
        }
    }

    private void paste() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence t = clip.getItemAt(0).coerceToText(this);
            String s = t == null ? "" : t.toString();
            String url = extractUrl(s);
            input.setText(url != null ? url : s);
        }
    }

    private static String extractUrl(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("https?://\\S+").matcher(s);
        return m.find() ? m.group() : null;
    }

    private void download() {
        if (busy) return;
        final String url = extractUrl(input.getText().toString());
        final int kind = url == null ? -1
            : url.contains("tiktok.com") ? 0
            : (url.contains("instagram.com") || url.contains("instagr.am")) ? 1
            : (X_ID.matcher(url).find() || url.contains("//t.co/")) ? 2
            : (url.contains("facebook.com") || url.contains("fb.watch") || url.contains("//fb.com/")) ? 3 : -1;
        if (kind < 0) {
            status.setText("Enlace no válido. Usa un enlace de TikTok, Instagram, X o Facebook.");
            return;
        }
        final String[] names = {"TikTok", "Instagram", "X", "Facebook"};
        final String igCookies = prefs.getString("igCookies", null);
        final String igUa = prefs.getString("igUa", IG_UA);
        setBusy(true);
        status.setText("Obteniendo video de " + names[kind] + "…");

        new Thread(new Runnable() {
            public void run() {
                try {
                    List<String[]> items = kind == 0 ? fetchTikTok(url)
                        : kind == 1 ? fetchInstagram(url, igCookies, igUa)
                        : kind == 2 ? fetchX(url) : fetchFacebook(url);
                    for (String[] it : items) enqueue(it[0], names[kind], it[1]);
                    done(items.size() == 1
                        ? "✓ Descargando " + items.get(0)[1] + "\nMira las notificaciones."
                        : "✓ Descargando " + items.size() + " videos\nMira las notificaciones.");
                } catch (Exception e) {
                    done("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    // ---------- X / Twitter ----------

    private static final Pattern X_ID = Pattern.compile(
        "(?:twitter\\.com|[/.]x\\.com)/(?:[^/?#]+/){1,2}status(?:es)?/(\\d+)");

    private static List<String[]> fetchX(String url) throws Exception {
        if (url.contains("//t.co/")) {
            url = request("GET", url, null, new String[][]{{"User-Agent", "curl/8.0"}}).finalUrl;
        }
        Matcher m = X_ID.matcher(url);
        if (!m.find()) throw new Exception("No reconocí el enlace. Usa el de un post de X (…/status/123…).");
        String id = m.group(1);

        String[] names = {"x", "fx", "vx"};
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            try {
                List<String> urls = i == 0 ? xSyndication(id) : i == 1 ? xFx(id) : xVx(id);
                if (!urls.isEmpty()) {
                    List<String[]> out = new ArrayList<String[]>();
                    for (int k = 0; k < urls.size(); k++) {
                        out.add(new String[]{urls.get(k), "x_" + id + (urls.size() > 1 ? "_" + (k + 1) : "") + ".mp4"});
                    }
                    return out;
                }
                log.append(names[i]).append("=sin video; ");
            } catch (Exception e) {
                log.append(names[i]).append("=").append(e.getMessage()).append("; ");
            }
        }
        throw new Exception("No encontré video en ese post. Puede ser solo texto o fotos, de una cuenta privada "
            + "o con restricción de edad.\n[" + log.toString().trim() + "]");
    }

    /** API pública de X para posts incrustados. */
    private static List<String> xSyndication(String id) throws Exception {
        String abc = "123456789abcdefghijklmnopqrstuvwxyz";
        StringBuilder tok = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 10; i++) tok.append(abc.charAt(rnd.nextInt(abc.length())));
        Resp r = request("GET", "https://cdn.syndication.twimg.com/tweet-result?id=" + id + "&token=" + tok,
            null, new String[][]{{"User-Agent", IG_UA}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        JSONObject j = new JSONObject(r.body);
        if ("TweetTombstone".equals(j.optString("__typename", ""))) throw new Exception("restringido");
        List<String> out = new ArrayList<String>();
        org.json.JSONArray md = j.optJSONArray("mediaDetails");
        for (int i = 0; md != null && i < md.length(); i++) {
            JSONObject vi = md.getJSONObject(i).optJSONObject("video_info");
            org.json.JSONArray vars = vi == null ? null : vi.optJSONArray("variants");
            String best = null;
            int bestRate = -1;
            for (int k = 0; vars != null && k < vars.length(); k++) {
                JSONObject v = vars.getJSONObject(k);
                if (!"video/mp4".equals(v.optString("content_type", ""))) continue;
                int rate = v.optInt("bitrate", 0);
                if (rate > bestRate) {
                    bestRate = rate;
                    best = v.optString("url", "");
                }
            }
            if (best != null && !best.isEmpty()) out.add(best);
        }
        return out;
    }

    private static List<String> xFx(String id) throws Exception {
        Resp r = request("GET", "https://api.fxtwitter.com/status/" + id, null,
            new String[][]{{"User-Agent", "TikTokDL/2.3"}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        List<String> out = new ArrayList<String>();
        JSONObject tweet = new JSONObject(r.body).optJSONObject("tweet");
        JSONObject media = tweet == null ? null : tweet.optJSONObject("media");
        org.json.JSONArray vids = media == null ? null : media.optJSONArray("videos");
        for (int i = 0; vids != null && i < vids.length(); i++) {
            String v = vids.getJSONObject(i).optString("url", "");
            if (v.startsWith("http")) out.add(v);
        }
        return out;
    }

    private static List<String> xVx(String id) throws Exception {
        Resp r = request("GET", "https://api.vxtwitter.com/Twitter/status/" + id, null,
            new String[][]{{"User-Agent", "TikTokDL/2.3"}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        List<String> out = new ArrayList<String>();
        org.json.JSONArray me = new JSONObject(r.body).optJSONArray("media_extended");
        for (int i = 0; me != null && i < me.length(); i++) {
            JSONObject o = me.getJSONObject(i);
            String type = o.optString("type", "");
            String v = o.optString("url", "");
            if (("video".equals(type) || "gif".equals(type)) && v.startsWith("http")) out.add(v);
        }
        return out;
    }

    // ---------- TikTok ----------

    private static List<String[]> fetchTikTok(String url) throws Exception {
        String api = "https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(url, "UTF-8");
        JSONObject j = new JSONObject(http("GET", api, null, null));
        if (j.optInt("code", -1) != 0) {
            throw new Exception(j.optString("msg", "La API no respondió bien"));
        }
        JSONObject d = j.getJSONObject("data");
        String video = d.optString("hdplay", "");
        if (video.isEmpty()) video = d.optString("play", "");
        if (video.isEmpty()) throw new Exception("No hay video (¿es un carrusel de fotos?)");
        if (video.startsWith("/")) video = "https://www.tikwm.com" + video;

        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{video, "tiktok_" + d.optString("id", String.valueOf(System.currentTimeMillis())) + ".mp4"});
        return out;
    }

    // ---------- Instagram ----------

    private static final String IG_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final String IG_APP_ID = "936619743392459";
    private static final String IG_DOC_ID = "8845758582119845";
    private static final String IG_LSD = "AVqbxe3J_YA";
    private static final Pattern IG_CODE = Pattern.compile(
        "instagr(?:am\\.com|\\.am)/(?:[^/?#]+/)?(?:p|reels?|tv)/([A-Za-z0-9_-]+)");

    /** La publicación existe pero no trae video: no tiene caso seguir intentando. */
    private static final class NoVideo extends Exception {
        NoVideo(String m) { super(m); }
    }

    private static String igShortcode(String url) {
        Matcher m = IG_CODE.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern IG_STORY = Pattern.compile("instagram\\.com/stories/[^/?#]+/(\\d+)");

    private static List<String[]> fetchInstagram(String url, String cookies, String ua) throws Exception {
        boolean logged = cookies != null && cookies.contains("sessionid=");

        Matcher st = IG_STORY.matcher(url);
        if (st.find()) {
            if (!logged) throw new Exception("Las historias requieren sesión. Ve a ⚙ → Iniciar sesión en Instagram.");
            try {
                return igAuth(st.group(1), "story_" + st.group(1), cookies, ua);
            } catch (NoVideo e) {
                throw new Exception("Esa historia es una foto, no un video.");
            }
        }
        if (url.contains("/stories/")) throw new Exception("Abre la historia y copia el enlace de esa historia en concreto.");

        // Los enlaces instagram.com/share/... son cortos: hay que seguir la redirección
        if (url.contains("/share/")) {
            Resp r = request("GET", url, null, new String[][]{
                {"User-Agent", logged ? ua : IG_UA}, {"Cookie", logged ? cookies : ""}});
            if (r.finalUrl.contains("/share/") || igShortcode(r.finalUrl) == null) {
                throw new Exception("No pude abrir ese enlace corto. En Instagram usa ⋯ → Copiar enlace y pégalo aquí.");
            }
            url = r.finalUrl;
        }

        String code = igShortcode(url);
        if (code == null) throw new Exception("No reconocí el enlace. Usa uno de un reel o publicación.");

        StringBuilder log = new StringBuilder();
        if (logged) {
            try {
                List<String[]> out = igAuth(igPk(code), code, cookies, ua);
                if (!out.isEmpty()) return out;
                log.append("sesion=sin video; ");
            } catch (NoVideo e) {
                throw e;
            } catch (Exception e) {
                log.append("sesion=").append(e.getMessage()).append("; ");
            }
        }

        String[] names = {"api", "api2", "embed", "web"};
        for (int i = 0; i < names.length; i++) {
            try {
                List<String[]> out;
                if (i == 0) out = igApi(code);
                else if (i == 1) out = igApiLsd(code);
                else if (i == 2) out = igEmbed(code);
                else out = igWeb(code);
                if (!out.isEmpty()) return out;
                log.append(names[i]).append("=sin video; ");
            } catch (NoVideo e) {
                throw e;
            } catch (Exception e) {
                log.append(names[i]).append("=").append(e.getMessage()).append("; ");
            }
        }
        throw new Exception((logged
            ? "Instagram no entregó el video ni con tu sesión. Si dice que caducó, cierra sesión y vuelve a entrar en ⚙."
            : "Instagram pide iniciar sesión para este video. Ve a ⚙ → Iniciar sesión en Instagram.")
            + "\n[" + log.toString().trim() + "]");
    }

    /** Con sesión iniciada: API de medios, igual que la web de Instagram. */
    private static List<String[]> igAuth(String pk, String label, String cookies, String ua) throws Exception {
        String csrf = cookieValue(cookies, "csrftoken");
        String[][] h = {
            {"User-Agent", ua},
            {"Accept", "*/*"},
            {"X-IG-App-ID", IG_APP_ID},
            {"X-ASBD-ID", "198387"},
            {"X-IG-WWW-Claim", "0"},
            {"X-Requested-With", "XMLHttpRequest"},
            {"X-CSRFToken", csrf == null ? "" : csrf},
            {"Origin", "https://www.instagram.com"},
            {"Referer", "https://www.instagram.com/"},
            {"Cookie", cookies},
        };
        Resp r = request("GET", "https://i.instagram.com/api/v1/media/" + pk + "/info/", null, h);
        if (r.code >= 400) {
            Resp r2 = request("GET", "https://www.instagram.com/api/v1/media/" + pk + "/info/", null, h);
            if (r2.code < 400) r = r2;
        }
        if (r.code == 401 || r.code == 403 || r.body.contains("login_required")) {
            throw new Exception("sesión caducada (HTTP " + r.code + ")");
        }
        if (r.code >= 400) throw new Exception("HTTP " + r.code);

        org.json.JSONArray items;
        try {
            items = new JSONObject(r.body).optJSONArray("items");
        } catch (Exception e) {
            throw new Exception("respuesta no JSON");
        }
        if (items == null || items.length() == 0) throw new Exception("sin datos");
        JSONObject it = items.getJSONObject(0);

        List<String[]> out = new ArrayList<String[]>();
        org.json.JSONArray car = it.optJSONArray("carousel_media");
        if (car != null) {
            int n = 0;
            for (int i = 0; i < car.length(); i++) {
                String v = igBestVersion(car.getJSONObject(i));
                if (v != null) {
                    n++;
                    out.add(new String[]{v, "ig_" + label + "_" + n + ".mp4"});
                }
            }
        } else {
            String v = igBestVersion(it);
            if (v != null) out.add(new String[]{v, "ig_" + label + ".mp4"});
        }
        if (out.isEmpty()) throw new NoVideo("Esa publicación no tiene video (solo fotos).");
        return out;
    }

    private static String igBestVersion(JSONObject media) throws Exception {
        org.json.JSONArray vv = media.optJSONArray("video_versions");
        String best = null;
        int bestW = -1;
        for (int i = 0; vv != null && i < vv.length(); i++) {
            JSONObject v = vv.getJSONObject(i);
            String u = v.optString("url", "");
            int w = v.optInt("width", 0);
            if (!u.isEmpty() && w > bestW) {
                bestW = w;
                best = u;
            }
        }
        return best;
    }

    private static String[][] igHeaders(String code, String csrf, String cookies) {
        return new String[][]{
            {"User-Agent", IG_UA},
            {"Accept", "*/*"},
            {"X-IG-App-ID", IG_APP_ID},
            {"X-ASBD-ID", "198387"},
            {"X-IG-WWW-Claim", "0"},
            {"X-Requested-With", "XMLHttpRequest"},
            {"X-CSRFToken", csrf == null ? "" : csrf},
            {"Origin", "https://www.instagram.com"},
            {"Referer", "https://www.instagram.com/p/" + code + "/"},
            {"Cookie", cookies == null ? "" : cookies},
        };
    }

    /** Método 1: pide un csrftoken y consulta GraphQL por GET. */
    private static List<String[]> igApi(String code) throws Exception {
        Resp pre = request("GET", "https://i.instagram.com/api/v1/web/get_ruling_for_content/"
            + "?content_type=MEDIA&target_id=" + igPk(code), null, igHeaders(code, null, null));
        String csrf = cookieValue(pre.cookies, "csrftoken");

        String vars = "{\"shortcode\":\"" + code + "\",\"child_comment_count\":3,\"fetch_comment_count\":40,"
            + "\"parent_comment_count\":24,\"has_threaded_comments\":true}";
        Resp r = request("GET", "https://www.instagram.com/graphql/query/?doc_id=" + IG_DOC_ID
            + "&variables=" + URLEncoder.encode(vars, "UTF-8"), null, igHeaders(code, csrf, pre.cookies));
        return igParse(r, code);
    }

    /** Método 2: GraphQL por POST con token lsd. */
    private static List<String[]> igApiLsd(String code) throws Exception {
        String vars = "{\"shortcode\":\"" + code + "\",\"fetch_tagged_user_count\":null,"
            + "\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}";
        String body = "variables=" + URLEncoder.encode(vars, "UTF-8")
            + "&doc_id=" + IG_DOC_ID + "&lsd=" + IG_LSD;
        String[][] h = {
            {"User-Agent", IG_UA},
            {"Content-Type", "application/x-www-form-urlencoded"},
            {"X-IG-App-ID", IG_APP_ID},
            {"X-FB-LSD", IG_LSD},
            {"X-ASBD-ID", "198387"},
            {"Sec-Fetch-Site", "same-origin"},
            {"Origin", "https://www.instagram.com"},
            {"Referer", "https://www.instagram.com/p/" + code + "/"},
        };
        return igParse(request("POST", "https://www.instagram.com/graphql/query", body, h), code);
    }

    private static List<String[]> igParse(Resp r, String code) throws Exception {
        if (r.code >= 400) throw new Exception("HTTP " + r.code + (r.body.contains("login") ? " (pide login)" : ""));
        JSONObject root;
        try {
            root = new JSONObject(r.body);
        } catch (Exception e) {
            throw new Exception("respuesta no JSON");
        }
        JSONObject data = root.optJSONObject("data");
        JSONObject media = data == null ? null : data.optJSONObject("xdt_shortcode_media");
        if (media == null) {
            String msg = root.optString("message", "");
            throw new Exception(msg.isEmpty() ? "sin datos" : msg);
        }

        List<String[]> out = new ArrayList<String[]>();
        boolean flagged = false;
        JSONObject side = media.optJSONObject("edge_sidecar_to_children");
        if (side != null) {
            org.json.JSONArray edges = side.optJSONArray("edges");
            int n = 0;
            for (int i = 0; edges != null && i < edges.length(); i++) {
                JSONObject node = edges.getJSONObject(i).getJSONObject("node");
                if (!node.optBoolean("is_video")) continue;
                flagged = true;
                String v = node.optString("video_url", "");
                if (!v.isEmpty()) {
                    n++;
                    out.add(new String[]{v, "ig_" + code + "_" + n + ".mp4"});
                }
            }
        } else if (media.optBoolean("is_video")) {
            flagged = true;
            String v = media.optString("video_url", "");
            if (!v.isEmpty()) out.add(new String[]{v, "ig_" + code + ".mp4"});
        }
        if (!flagged) throw new NoVideo("Esa publicación no tiene video (solo fotos).");
        return out;
    }

    /** Método 3: página embed. */
    private static List<String[]> igEmbed(String code) throws Exception {
        Resp r = request("GET", "https://www.instagram.com/p/" + code + "/embed/captioned/", null,
            new String[][]{{"User-Agent", IG_UA}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        return igScrape(r.body, code);
    }

    /** Método 4: página normal de la publicación. */
    private static List<String[]> igWeb(String code) throws Exception {
        Resp r = request("GET", "https://www.instagram.com/reel/" + code + "/", null,
            new String[][]{{"User-Agent", IG_UA}, {"Accept", "text/html"}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        if (r.finalUrl.contains("/accounts/login")) throw new Exception("pide login");
        return igScrape(r.body, code);
    }

    private static final Pattern[] IG_HTML = {
        Pattern.compile("video_url\\\\*\"\\s*:\\s*\\\\*\"(.+?)\\\\*\""),
        Pattern.compile("video_versions\\\\*\"\\s*:\\s*\\[\\s*\\{.{0,400}?\\\\*\"url\\\\*\"\\s*:\\s*\\\\*\"(.+?)\\\\*\""),
        Pattern.compile("property=\"og:video(?::secure_url)?\"\\s+content=\"(.+?)\""),
    };

    private static List<String[]> igScrape(String html, String code) {
        List<String[]> out = new ArrayList<String[]>();
        List<String> seen = new ArrayList<String>();
        for (Pattern p : IG_HTML) {
            Matcher m = p.matcher(html);
            while (m.find()) {
                String v = unescape(m.group(1));
                if (!v.startsWith("http")) continue;
                String key = v.contains("?") ? v.substring(0, v.indexOf('?')) : v;
                if (seen.contains(key)) continue;
                seen.add(key);
                out.add(new String[]{v, "ig_" + code + (seen.size() > 1 ? "_" + seen.size() : "") + ".mp4"});
            }
            if (!out.isEmpty()) break;
        }
        return out;
    }

    /** Quita los escapes JSON (\/ y \u0026), aunque vengan escapados varias veces. */
    private static String unescape(String s) {
        String r = s;
        for (int i = 0; i < 3 && r.contains("\\"); i++) {
            try {
                r = new JSONObject("{\"u\":\"" + r.replace("\"", "") + "\"}").getString("u");
            } catch (Exception e) {
                break;
            }
        }
        return r.replace("&amp;", "&");
    }

    /** Convierte el shortcode (base64 de Instagram) al id numérico. */
    private static String igPk(String code) {
        String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        if (code.length() > 28) code = code.substring(0, code.length() - 28);
        java.math.BigInteger n = java.math.BigInteger.ZERO;
        java.math.BigInteger b = java.math.BigInteger.valueOf(64);
        for (int i = 0; i < code.length(); i++) {
            n = n.multiply(b).add(java.math.BigInteger.valueOf(Math.max(0, abc.indexOf(code.charAt(i)))));
        }
        return n.toString();
    }

    private static String cookieValue(String cookies, String name) {
        if (cookies == null) return null;
        for (String part : cookies.split(";\\s*")) {
            if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        }
        return null;
    }

    // ---------- Facebook ----------

    private static final String[][] FB_HEADERS = {
        {"User-Agent", IG_UA},
        {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"},
        {"Accept-Language", "en-US,en;q=0.9"},
        {"Sec-Fetch-Dest", "document"},
        {"Sec-Fetch-Mode", "navigate"},
        {"Sec-Fetch-Site", "none"},
        {"Sec-Fetch-User", "?1"},
        {"Upgrade-Insecure-Requests", "1"},
    };
    // De mejor a peor calidad
    private static final Pattern[] FB_HTML = {
        Pattern.compile("\"progressive_url\":\"([^\"]+)\",\"failure_reason\":[^,]*,\"metadata\":\\{\"quality\":\"HD\"\\}"),
        Pattern.compile("\"browser_native_hd_url\":\"([^\"]+)\""),
        Pattern.compile("\"playable_url_quality_hd\":\"([^\"]+)\""),
        Pattern.compile("\"?hd_src\"?:\"([^\"]+)\""),
        Pattern.compile("\"progressive_url\":\"([^\"]+)\""),
        Pattern.compile("\"browser_native_sd_url\":\"([^\"]+)\""),
        Pattern.compile("\"playable_url\":\"([^\"]+)\""),
        Pattern.compile("\"?sd_src(?:_no_ratelimit)?\"?:\"([^\"]+)\""),
        Pattern.compile("property=\"og:video(?::secure_url|:url)?\"\\s+content=\"([^\"]+)\""),
    };
    private static final Pattern FB_VID = Pattern.compile("(?:/videos/(?:[^/?#]+/)?|/reel/|[?&]v=|story_fbid=|/watch/\\?v=)(\\d+)");

    private static String fbScrape(String html) {
        for (Pattern p : FB_HTML) {
            Matcher m = p.matcher(html);
            while (m.find()) {
                String v = unescape(m.group(1));
                if (v.startsWith("http")) return v;
            }
        }
        return null;
    }

    private static List<String[]> fetchFacebook(String url) throws Exception {
        url = url.replaceFirst("//(?:m|mbasic|web|touch)\\.facebook\\.com", "//www.facebook.com");
        StringBuilder log = new StringBuilder();
        String video = null;
        String page = url;

        // 1) Página del video (también resuelve fb.watch y /share/)
        try {
            Resp r = request("GET", url, null, FB_HEADERS);
            page = r.finalUrl;
            if (r.code >= 400) log.append("web=HTTP ").append(r.code).append("; ");
            else {
                video = fbScrape(r.body);
                if (video == null) {
                    log.append(r.finalUrl.contains("/login") || r.body.contains("login_form")
                        ? "web=pide login; " : "web=sin video; ");
                }
            }
        } catch (Exception e) {
            log.append("web=").append(e.getMessage()).append("; ");
        }

        // 2) Reproductor incrustable
        if (video == null) {
            try {
                String target = page.contains("/login") ? url : page;
                Resp r = request("GET", "https://www.facebook.com/plugins/video.php?show_text=0&href="
                    + URLEncoder.encode(target, "UTF-8"), null, FB_HEADERS);
                if (r.code >= 400) log.append("embed=HTTP ").append(r.code).append("; ");
                else {
                    video = fbScrape(r.body);
                    if (video == null) log.append("embed=sin video; ");
                }
            } catch (Exception e) {
                log.append("embed=").append(e.getMessage()).append("; ");
            }
        }

        if (video == null) {
            throw new Exception("No encontré el video. Puede ser privado, de un grupo cerrado, "
                + "o Facebook pidió iniciar sesión.\n[" + log.toString().trim() + "]");
        }
        Matcher m = FB_VID.matcher(page);
        String id = m.find() ? m.group(1) : String.valueOf(System.currentTimeMillis());
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{video, "fb_" + id + ".mp4"});
        return out;
    }

    // ---------- Red ----------

    private static final class Resp {
        int code;
        String body = "";
        String cookies = "";
        String finalUrl = "";
    }

    /** Petición HTTP que no lanza error por códigos 4xx/5xx; devuelve cuerpo, cookies y URL final. */
    private static Resp request(String method, String u, String body, String[][] headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", UA);
        if (headers != null) {
            for (String[] h : headers) if (!h[1].isEmpty()) c.setRequestProperty(h[0], h[1]);
        }
        if (body != null) {
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        r.finalUrl = c.getURL().toString();

        StringBuilder ck = new StringBuilder();
        for (java.util.Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
            if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Set-Cookie")) continue;
            for (String v : e.getValue()) {
                String pair = v.contains(";") ? v.substring(0, v.indexOf(';')) : v;
                if (ck.length() > 0) ck.append("; ");
                ck.append(pair);
            }
        }
        r.cookies = ck.toString();

        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            r.body = sb.toString();
        }
        c.disconnect();
        return r;
    }

    private static String http(String method, String u, String body, String[][] headers) throws Exception {
        Resp r = request(method, u, body, headers);
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        return r.body;
    }

    private void enqueue(String videoUrl, String folder, String name) {
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(videoUrl));
        req.addRequestHeader("User-Agent", UA);
        req.setTitle(name);
        req.setDescription("TikTok DL");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, folder + "/" + name);
        dm.enqueue(req);
    }

    private void done(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                setBusy(false);
                status.setText(msg);
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        if (progress != null) progress.setVisibility(b ? View.VISIBLE : View.GONE);
        if (btnDownload != null) {
            btnDownload.setEnabled(!b);
            btnDownload.setAlpha(b ? 0.5f : 1f);
        }
    }

    // ---------- Helpers de UI ----------

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String s, int bg, int fg) {
        TextView t = text(s, 15, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(14), dp(12), dp(14));
        t.setBackground(ripple(rounded(bg, 14), 0x33FFFFFF));
        t.setClickable(true);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 13, cSub, true);
        t.setPadding(0, dp(16), 0, dp(8));
        return t;
    }

    private TextView chip(String label, boolean selected, final Runnable action) {
        TextView t = text(label, 14, selected ? Color.WHITE : cText, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setBackground(ripple(rounded(selected ? PINK : cField, 20), 0x33FFFFFF));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private View swatch(Drawable fill, String center, String label, boolean selected, final Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout frame = new FrameLayout(this);
        int pad = dp(3);
        frame.setPadding(pad, pad, pad, pad);
        GradientDrawable ring = rounded(Color.TRANSPARENT, 19);
        if (selected) ring.setStroke(dp(2), PINK);
        frame.setBackground(ring);

        if (fill instanceof GradientDrawable) ((GradientDrawable) fill).setCornerRadius(dp(16));
        View inner;
        if (center != null) {
            TextView t = text(center, 22, cText, true);
            t.setGravity(Gravity.CENTER);
            inner = t;
            inner.setBackground(fill);
        } else if (fill instanceof GradientDrawable) {
            inner = new View(this);
            inner.setBackground(fill);
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(fill);
            inner = iv;
        }
        frame.addView(inner, new FrameLayout.LayoutParams(dp(54), dp(54)));
        box.addView(frame);

        TextView l = text(label, 12, selected ? cText : cSub, selected);
        l.setPadding(0, dp(4), 0, 0);
        box.addView(l);

        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    private HorizontalScrollView hscroll(View child) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(child);
        return h;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Drawable ripple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    private int iconRes(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private Bitmap loadBitmap(String uriStr) {
        if (uriStr == null) return null;
        try {
            Uri uri = Uri.parse(uriStr);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            in = getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
