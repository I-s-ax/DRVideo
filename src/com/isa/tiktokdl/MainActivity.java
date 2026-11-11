package com.isa.tiktokdl;

import android.app.Activity;
import android.app.AlertDialog;
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

        TextView sub = text("TikTok e Instagram, sin marca de agua", 14, cSub, false);
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
        input.setHint("Enlace de TikTok o Instagram");
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
            : "Tip: en TikTok o Instagram toca Compartir → TikTok DL", 14, cSub, false);
        status.setPadding(0, dp(10), 0, 0);
        card.addView(status);
        setBusy(busy);

        TextView foot = text("Se guardan en Descargas/TikTok y Descargas/Instagram", 12, cSub, false);
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
        final boolean isTikTok = url != null && url.contains("tiktok.com");
        final boolean isInsta = url != null && (url.contains("instagram.com") || url.contains("instagr.am"));
        if (!isTikTok && !isInsta) {
            status.setText("Enlace no válido. Usa un enlace de TikTok o Instagram.");
            return;
        }
        setBusy(true);
        status.setText(isTikTok ? "Obteniendo video de TikTok…" : "Obteniendo video de Instagram…");

        new Thread(new Runnable() {
            public void run() {
                try {
                    List<String[]> items = isTikTok ? fetchTikTok(url) : fetchInstagram(url);
                    String folder = isTikTok ? "TikTok" : "Instagram";
                    for (String[] it : items) enqueue(it[0], folder, it[1]);
                    done(items.size() == 1
                        ? "✓ Descargando " + items.get(0)[1] + "\nMira las notificaciones."
                        : "✓ Descargando " + items.size() + " videos\nMira las notificaciones.");
                } catch (Exception e) {
                    done("Error: " + e.getMessage());
                }
            }
        }).start();
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

    private static String igShortcode(String url) {
        Matcher m = Pattern.compile("instagr(?:am\\.com|\\.am)/(?:[^/?#]+/)?(?:p|reels?|tv)/([A-Za-z0-9_-]+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static List<String[]> fetchInstagram(String url) throws Exception {
        if (url.contains("/stories/")) throw new Exception("Las historias requieren iniciar sesión; no son compatibles.");
        String code = igShortcode(url);
        if (code == null) throw new Exception("No reconocí el enlace. Usa uno de un reel o publicación.");

        List<String[]> out = new ArrayList<String[]>();
        // 1) API GraphQL pública (la que usa la web de Instagram)
        try {
            out = igGraphql(code);
        } catch (Exception ignored) { }
        // 2) Respaldo: página embed
        if (out.isEmpty()) {
            try {
                out = igEmbed(code);
            } catch (Exception ignored) { }
        }
        if (out.isEmpty()) {
            throw new Exception("No encontré video. Puede ser una cuenta privada, una publicación solo de fotos, o Instagram limitó las solicitudes (intenta en unos minutos).");
        }
        return out;
    }

    private static List<String[]> igGraphql(String code) throws Exception {
        String vars = "{\"shortcode\":\"" + code + "\",\"fetch_tagged_user_count\":null,"
            + "\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}";
        String body = "variables=" + URLEncoder.encode(vars, "UTF-8")
            + "&doc_id=" + IG_DOC_ID + "&lsd=" + IG_LSD;
        String[][] h = {
            {"User-Agent", IG_UA},
            {"Content-Type", "application/x-www-form-urlencoded"},
            {"X-IG-App-ID", IG_APP_ID},
            {"X-FB-LSD", IG_LSD},
            {"X-ASBD-ID", "129477"},
            {"Sec-Fetch-Site", "same-origin"},
            {"Origin", "https://www.instagram.com"},
            {"Referer", "https://www.instagram.com/p/" + code + "/"},
        };
        JSONObject root = new JSONObject(http("POST", "https://www.instagram.com/graphql/query", body, h));
        JSONObject media = root.getJSONObject("data").optJSONObject("xdt_shortcode_media");
        List<String[]> out = new ArrayList<String[]>();
        if (media == null) return out;

        JSONObject side = media.optJSONObject("edge_sidecar_to_children");
        if (side != null) {
            org.json.JSONArray edges = side.optJSONArray("edges");
            int n = 0;
            for (int i = 0; edges != null && i < edges.length(); i++) {
                JSONObject node = edges.getJSONObject(i).getJSONObject("node");
                String v = node.optString("video_url", "");
                if (node.optBoolean("is_video") && !v.isEmpty()) {
                    n++;
                    out.add(new String[]{v, "ig_" + code + "_" + n + ".mp4"});
                }
            }
        } else if (media.optBoolean("is_video")) {
            String v = media.optString("video_url", "");
            if (!v.isEmpty()) out.add(new String[]{v, "ig_" + code + ".mp4"});
        }
        return out;
    }

    private static List<String[]> igEmbed(String code) throws Exception {
        String html = http("GET", "https://www.instagram.com/p/" + code + "/embed/captioned/", null,
            new String[][]{{"User-Agent", IG_UA}});
        List<String[]> out = new ArrayList<String[]>();
        Matcher m = Pattern.compile("video_url\\\\?\"\\s*:\\s*\\\\?\"(.+?)\\\\?\"").matcher(html);
        int n = 0;
        List<String> seen = new ArrayList<String>();
        while (m.find()) {
            String v = unescape(m.group(1));
            if (!v.startsWith("http") || seen.contains(v)) continue;
            seen.add(v);
            n++;
            out.add(new String[]{v, "ig_" + code + (n > 1 ? "_" + n : "") + ".mp4"});
        }
        return out;
    }

    /** Quita los escapes JSON (\\/ , \\u0026, y doble escape dentro de HTML). */
    private static String unescape(String s) {
        String r = s;
        for (int i = 0; i < 2 && r.contains("\\"); i++) {
            try {
                r = new JSONObject("{\"u\":\"" + r.replace("\"", "\\\"") + "\"}").getString("u");
            } catch (Exception e) {
                break;
            }
        }
        return r.replace("&amp;", "&");
    }

    // ---------- Red ----------

    private static String http(String method, String u, String body, String[][] headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", UA);
        if (headers != null) for (String[] h : headers) c.setRequestProperty(h[0], h[1]);
        if (body != null) {
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) throw new Exception("HTTP " + code);
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code);
        return sb.toString();
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
