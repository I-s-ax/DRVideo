package com.isa.tiktokdl;

import android.Manifest;
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
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int PICK_BG = 42;
    private static final int REQ_READ = 43;
    private static final int PINK = 0xFFFE2C55;
    private static final int CYAN = 0xFF25F4EE;

    // Carpeta en Descargas e id de sesión de cada red (mismo orden)
    private static final String[] NETS = {"TikTok", "Instagram", "X", "Facebook"};
    private static final String[] NET_IDS = {"tt", "ig", "x", "fb"};
    // id, nombre, página de login, dominio de las cookies, cookie que indica sesión iniciada
    private static final String[][] ACCOUNTS = {
        {"ig", "Instagram", "https://www.instagram.com/accounts/login/", "https://www.instagram.com", "sessionid="},
        {"fb", "Facebook", "https://m.facebook.com/login/", "https://www.facebook.com", "c_user="},
        {"x", "X", "https://x.com/i/flow/login", "https://x.com", "auth_token="},
        {"tt", "TikTok", "https://www.tiktok.com/login/phone-or-email/email", "https://www.tiktok.com", "sessionid="},
    };

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

    private int tab;            // 0 = descargar, 1 = biblioteca
    private String libKey;      // carpeta abierta en la biblioteca ("Red/usuario"), o null
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

    @Override
    public void onBackPressed() {
        if (tab == 1 && libKey != null) {
            libKey = null;
            showPage();
        } else if (tab == 1) {
            tab = 0;
            showPage();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQ_READ) showPage();
    }

    // ---------- Interfaz ----------

    private FrameLayout pageHost;
    private LinearLayout navHost;

    /** Arma el marco (fondo, encabezado, barra). Solo hace falta al iniciar o al cambiar el tema. */
    private void buildUi() {
        dark = !"light".equals(prefs.getString("theme", "dark"));
        cText = dark ? 0xFFFFFFFF : 0xFF141418;
        cSub = dark ? 0xB3FFFFFF : 0x99141418;
        cCard = dark ? 0xCC15151C : 0xE6FFFFFF;
        cField = dark ? 0xFF262630 : 0xFFF0F0F4;

        FrameLayout root = new FrameLayout(this);
        int barColor = applyBackground(root);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        // Encabezado
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(24), dp(20), dp(4));
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
        shell.addView(header);

        // Contenido de la pestaña
        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));

        // Barra inferior
        navHost = new LinearLayout(this);
        navHost.setBackground(rounded(cCard, 26));
        navHost.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.setMargins(dp(20), dp(8), dp(20), dp(16));
        shell.addView(navHost, nlp);

        showPage();
        setContentView(root);

        getWindow().setStatusBarColor(barColor);
        getWindow().setNavigationBarColor(barColor);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /** Cambia solo el contenido de la pestaña, sin reconstruir toda la pantalla. */
    private void showPage() {
        pageHost.removeAllViews();
        pageHost.addView(tab == 0 ? downloadPage() : libraryPage(), new FrameLayout.LayoutParams(-1, -1));
        navHost.removeAllViews();
        navHost.addView(navButton("↓  Descargar", 0), new LinearLayout.LayoutParams(0, -2, 1));
        navHost.addView(navButton("▦  Biblioteca", 1), new LinearLayout.LayoutParams(0, -2, 1));
    }

    private TextView navButton(String label, final int target) {
        boolean on = tab == target;
        TextView t = text(label, 14, on ? Color.WHITE : cSub, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(11), dp(10), dp(11));
        t.setBackground(ripple(rounded(on ? PINK : Color.TRANSPARENT, 20), 0x33FFFFFF));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (tab == target && libKey == null) return;
                tab = target;
                libKey = null;
                showPage();
            }
        });
        return t;
    }

    private View downloadPage() {
        String keepText = input != null ? input.getText().toString() : "";
        String keepStatus = status != null ? status.getText().toString() : null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), 0, dp(20), dp(12));
        scroll.addView(col);

        TextView sub = text("TikTok, Instagram, X y Facebook", 14, cSub, false);
        sub.setPadding(0, dp(2), 0, 0);
        col.addView(sub);

        // Tarjeta principal
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(cCard, 24));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(24);
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
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(6));
        plp.topMargin = dp(14);
        card.addView(progress, plp);

        status = text(keepStatus != null ? keepStatus
            : "Tip: en la app del video toca Compartir → TikTok DL", 14, cSub, false);
        status.setPadding(0, dp(10), 0, 0);
        card.addView(status);
        setBusy(busy);

        TextView foot = text("Se guardan en Descargas / red / perfil", 12, cSub, false);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(20);
        col.addView(foot, flp);
        return scroll;
    }

    private Bitmap bgBitmap;
    private String bgBitmapUri;

    /** Pone el fondo elegido y devuelve el color para las barras del sistema. */
    private int applyBackground(FrameLayout root) {
        String bg = prefs.getString("bg", dark ? "noche" : "nieve");
        if ("custom".equals(bg)) {
            String bgUri = prefs.getString("bgUri", null);
            if (bgBitmap == null || bgUri == null || !bgUri.equals(bgBitmapUri)) {
                bgBitmap = loadBitmap(bgUri);
                bgBitmapUri = bgUri;
            }
            Bitmap bmp = bgBitmap;
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

        // Cuentas
        v.addView(section("Cuentas (para videos privados)"));
        for (final String[] acc : ACCOUNTS) {
            final boolean logged = cookies(acc[0]) != null;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(acc[1], 15, cText, true));
            info.addView(text(logged ? "Sesión activa" : "Sin sesión", 12, logged ? CYAN : cSub, false));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(chip(logged ? "Cerrar sesión" : "Iniciar sesión", !logged, new Runnable() {
                public void run() {
                    if (logged) logout(acc); else login(acc);
                }
            }));
            v.addView(row);
        }
        TextView note = text("Solo hace falta para cuentas privadas que sigues, historias, o cuando la red pide "
            + "login. La contraseña se escribe en la página oficial; aquí solo se guarda la cookie de sesión en tu "
            + "teléfono. Entra con usuario y contraseña (el botón de Google no funciona aquí). "
            + "Recomendado: cuentas secundarias.", 12, cSub, false);
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

    // ---------- Sesiones ----------

    /** Cookies guardadas de una red, o null si no hay sesión. */
    private String cookies(String id) {
        String c = prefs.getString(id + "Cookies", "");
        for (String[] acc : ACCOUNTS) {
            if (acc[0].equals(id)) return c.contains(acc[4]) ? c : null;
        }
        return null;
    }

    private void login(final String[] acc) {
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
        w.setWebViewClient(new WebViewClient());

        // Revisa cada segundo si ya apareció la cookie de sesión
        final Handler handler = new Handler();
        final Runnable check = new Runnable() {
            public void run() {
                if (!d.isShowing()) return;
                String c = cm.getCookie(acc[3]);
                if (c != null && c.contains(acc[4])) {
                    prefs.edit().putString(acc[0] + "Cookies", c).putString(acc[0] + "Ua", ua).apply();
                    cm.flush();
                    d.dismiss();
                    toast("Sesión de " + acc[1] + " guardada");
                    return;
                }
                handler.postDelayed(this, 1000);
            }
        };
        d.setContentView(w, new ViewGroup.LayoutParams(-1, -1));
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface di) {
                handler.removeCallbacks(check);
                w.stopLoading();
                w.destroy();
            }
        });
        d.show();
        w.loadUrl(acc[2]);
        handler.postDelayed(check, 1500);
        toast("Inicia sesión; la ventana se cierra sola al terminar");
    }

    private void logout(String[] acc) {
        prefs.edit().remove(acc[0] + "Cookies").remove(acc[0] + "Ua").apply();
        // Caduca las cookies de ese dominio en el navegador interno
        CookieManager cm = CookieManager.getInstance();
        String all = cm.getCookie(acc[3]);
        String host = Uri.parse(acc[3]).getHost();
        if (host.startsWith("www.")) host = host.substring(4);
        if (all != null) {
            for (String part : all.split(";\\s*")) {
                String name = part.split("=", 2)[0];
                cm.setCookie(acc[3], name + "=; Max-Age=0; Path=/; Domain=." + host);
                cm.setCookie(acc[3], name + "=; Max-Age=0; Path=/");
            }
        }
        cm.flush();
        toast("Sesión de " + acc[1] + " cerrada");
        refreshSettings();
    }

    // ---------- Descarga ----------

    private void handleIntent(Intent i) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String url = extractUrl(i.getStringExtra(Intent.EXTRA_TEXT));
            if (url != null) {
                if (tab != 0) {
                    tab = 0;
                    libKey = null;
                    showPage();
                }
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
            : (Net.X_ID.matcher(url).find() || url.contains("//t.co/")) ? 2
            : (url.contains("facebook.com") || url.contains("fb.watch") || url.contains("//fb.com/")) ? 3 : -1;
        if (kind < 0) {
            status.setText("Enlace no válido. Usa un enlace de TikTok, Instagram, X o Facebook.");
            return;
        }
        final String ck = cookies(NET_IDS[kind]);
        final String ua = prefs.getString(NET_IDS[kind] + "Ua", Net.IG_UA);
        setBusy(true);
        status.setText("Obteniendo video de " + NETS[kind] + "…");

        new Thread(new Runnable() {
            public void run() {
                try {
                    List<String[]> items = kind == 0 ? Net.fetchTikTok(url, ck)
                        : kind == 1 ? Net.fetchInstagram(url, ck, ua)
                        : kind == 2 ? Net.fetchX(url, ck) : Net.fetchFacebook(url, ck);
                    String folder = NETS[kind];
                    for (String[] it : items) {
                        String user = it.length > 2 ? Net.cleanName(it[2]) : "";
                        String file = user.isEmpty() ? it[1]
                            : user + "_" + it[1].replaceFirst("^(tiktok|ig|x|fb)_", "");
                        folder = NETS[kind] + (user.isEmpty() ? "" : "/" + user);
                        enqueue(it[0], folder, file, it.length > 4 ? it[4] : "", it.length > 5 ? it[5] : "");

                        // Foto de perfil para la portada de la carpeta
                        if (!user.isEmpty() && it.length > 3 && it[3].startsWith("http")) {
                            File av = avatarFile(NETS[kind], user);
                            if (!av.exists() || System.currentTimeMillis() - av.lastModified() > 7L * 86400000L) {
                                Net.downloadTo(it[3], av);
                            }
                        }
                    }
                    done("✓ Descargando " + (items.size() == 1 ? "video" : items.size() + " videos")
                        + "\nCarpeta: Descargas/" + folder);
                } catch (Exception e) {
                    done("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private File avatarFile(String net, String user) {
        return new File(getFilesDir(), "avatars/" + net + "_" + user + ".jpg");
    }

    private void enqueue(String videoUrl, String folder, String name, String cookie, String referer) {
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(videoUrl));
        if (cookie != null && !cookie.isEmpty()) {
            req.addRequestHeader("User-Agent", Net.IG_UA);
            req.addRequestHeader("Cookie", cookie);
        } else {
            req.addRequestHeader("User-Agent", Net.UA);
        }
        if (referer != null && !referer.isEmpty()) req.addRequestHeader("Referer", referer);
        if (Build.VERSION.SDK_INT < 29) req.allowScanningByMediaScanner();
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

    // ---------- Biblioteca ----------

    private static final class Folder {
        String net, user;
        List<File> videos = new ArrayList<File>();

        String key() { return net + "/" + user; }
        String title() { return user.isEmpty() ? net : user; }
    }

    /** Contenedor con proporción fija (alto = ancho × ratio). */
    private static final class RatioFrame extends FrameLayout {
        final float ratio;

        RatioFrame(Context c, float ratio) {
            super(c);
            this.ratio = ratio;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((int) (w * ratio), MeasureSpec.EXACTLY));
        }
    }

    private static final ViewOutlineProvider OVAL = new ViewOutlineProvider() {
        @Override
        public void getOutline(View v, Outline o) {
            o.setOval(0, 0, v.getWidth(), v.getHeight());
        }
    };

    // Fecha de cada archivo, leída una sola vez (consultarla es lento en el almacenamiento compartido)
    private final java.util.concurrent.ConcurrentHashMap<String, Long> fileTimes =
        new java.util.concurrent.ConcurrentHashMap<String, Long>();

    private long timeOf(File f) {
        Long t = fileTimes.get(f.getPath());
        if (t == null) {
            t = f.lastModified();
            fileTimes.put(f.getPath(), t);
        }
        return t;
    }

    private final Comparator<File> NEWEST = new Comparator<File>() {
        public int compare(File a, File b) {
            return Long.compare(timeOf(b), timeOf(a));
        }
    };

    private List<Folder> libCache;     // último escaneo; se muestra al instante
    private boolean libScanning;
    private long libScannedAt;

    private static String signature(List<Folder> l) {
        if (l == null) return "";
        StringBuilder b = new StringBuilder();
        for (Folder f : l) b.append(f.key()).append(':').append(f.videos.size()).append(':')
            .append(f.videos.get(0).getName()).append('|');
        return b.toString();
    }

    /** Vuelve a leer las carpetas en segundo plano y refresca la vista solo si algo cambió. */
    private void refreshLibrary() {
        if (libScanning || System.currentTimeMillis() - libScannedAt < 3000) return;
        libScanning = true;
        new Thread(new Runnable() {
            public void run() {
                final List<Folder> fresh = scanLibrary();
                runOnUiThread(new Runnable() {
                    public void run() {
                        libScanning = false;
                        libScannedAt = System.currentTimeMillis();
                        boolean changed = libCache == null || !signature(fresh).equals(signature(libCache));
                        libCache = fresh;
                        if (changed && tab == 1 && pageHost != null) showPage();
                    }
                });
            }
        }).start();
    }

    private boolean canRead() {
        return Build.VERSION.SDK_INT < 23
            || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isVideo(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".webm");
    }

    /** Lee Descargas/<red>/<perfil>/ y arma la lista de carpetas, la más reciente primero. */
    private List<Folder> scanLibrary() {
        List<Folder> out = new ArrayList<Folder>();
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        for (String net : NETS) {
            File[] children = new File(base, net).listFiles();
            if (children == null) continue;
            Folder loose = new Folder();
            loose.net = net;
            loose.user = "";
            for (File c : children) {
                if (isVideo(c.getName())) {
                    loose.videos.add(c);
                } else if (c.isDirectory()) {
                    Folder f = new Folder();
                    f.net = net;
                    f.user = c.getName();
                    File[] vids = c.listFiles();
                    for (int i = 0; vids != null && i < vids.length; i++) {
                        if (isVideo(vids[i].getName())) f.videos.add(vids[i]);
                    }
                    if (!f.videos.isEmpty()) out.add(f);
                }
            }
            if (!loose.videos.isEmpty()) out.add(loose);
        }
        for (Folder f : out) {
            for (File v : f.videos) fileTimes.put(v.getPath(), v.lastModified());
            Collections.sort(f.videos, NEWEST);
        }
        Collections.sort(out, new Comparator<Folder>() {
            public int compare(Folder a, Folder b) {
                return Long.compare(timeOf(b.videos.get(0)), timeOf(a.videos.get(0)));
            }
        });
        return out;
    }

    private View libraryPage() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(4), dp(20), 0);

        if (!canRead()) {
            col.setGravity(Gravity.CENTER);
            TextView msg = text("Para mostrar tus videos descargados, permite el acceso a los archivos.", 15, cText, false);
            msg.setGravity(Gravity.CENTER);
            col.addView(msg);
            TextView allow = button("Permitir acceso", PINK, Color.WHITE);
            allow.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(16);
            col.addView(allow, lp);
            return col;
        }

        refreshLibrary();
        if (libCache == null) {
            col.setGravity(Gravity.CENTER);
            col.addView(text("Cargando biblioteca…", 14, cSub, false));
            return col;
        }
        final List<Folder> folders = libCache;
        Folder open = null;
        for (Folder f : folders) if (f.key().equals(libKey)) open = f;
        if (open == null) libKey = null;

        GridView grid = new GridView(this);
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(new GradientDrawable());
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(14), 0, dp(12));
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));

        if (open == null) {
            int total = 0;
            for (Folder f : folders) total += f.videos.size();
            col.addView(text("Biblioteca", 18, cText, true));
            col.addView(text(folders.size() + (folders.size() == 1 ? " carpeta · " : " carpetas · ")
                + total + (total == 1 ? " video" : " videos"), 13, cSub, false));
            if (folders.isEmpty()) {
                TextView empty = text("Aún no hay descargas.\nLos videos que bajes aparecerán aquí, "
                    + "agrupados por perfil.", 14, cSub, false);
                empty.setGravity(Gravity.CENTER);
                col.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
                return col;
            }
            grid.setNumColumns(2);
            grid.setAdapter(new FolderAdapter(folders));
            grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                    libKey = folders.get(pos).key();
                    showPage();
                }
            });
        } else {
            final Folder f = open;
            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            TextView back = text("‹", 26, cText, true);
            back.setGravity(Gravity.CENTER);
            back.setPadding(0, 0, 0, dp(4));
            back.setBackground(ripple(rounded(cCard, 20), 0x33FFFFFF));
            back.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { onBackPressed(); }
            });
            bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

            File av = avatarFile(f.net, f.user);
            if (av.exists()) {
                ImageView pic = circleImage();
                loadImage(pic, av, false);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(40), dp(40));
                plp.leftMargin = dp(10);
                bar.addView(pic, plp);
            }

            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(f.title(), 17, cText, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            tx.addView(name);
            tx.addView(text(f.net + " · " + f.videos.size() + (f.videos.size() == 1 ? " video" : " videos"),
                12, cSub, false));
            LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(0, -2, 1);
            xlp.leftMargin = dp(10);
            bar.addView(tx, xlp);
            col.addView(bar);

            grid.setNumColumns(3);
            grid.setAdapter(new VideoAdapter(f.videos));
            grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                    play(f.videos, pos);
                }
            });
        }
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        return col;
    }

    private ImageView circleImage() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(rounded(cField, 40));
        iv.setOutlineProvider(OVAL);
        iv.setClipToOutline(true);
        return iv;
    }

    /** Tarjeta de carpeta: fondo con el último video y foto de perfil junto al nombre. */
    private final class FolderAdapter extends BaseAdapter {
        final List<Folder> data;

        FolderAdapter(List<Folder> data) { this.data = data; }

        public int getCount() { return data.size(); }
        public Object getItem(int i) { return data.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Object[] h;
            if (cv == null) {
                RatioFrame card = new RatioFrame(MainActivity.this, 1.2f);
                card.setBackground(rounded(cField, 18));
                card.setClipToOutline(true);

                ImageView cover = new ImageView(MainActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(cover, new FrameLayout.LayoutParams(-1, -1));

                View scrim = new View(MainActivity.this);
                scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0xE6000000}));
                card.addView(scrim, new FrameLayout.LayoutParams(-1, dp(90), Gravity.BOTTOM));

                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                ImageView avatar = circleImage();
                row.addView(avatar, new LinearLayout.LayoutParams(dp(34), dp(34)));

                LinearLayout tx = new LinearLayout(MainActivity.this);
                tx.setOrientation(LinearLayout.VERTICAL);
                TextView name = text("", 13, Color.WHITE, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                TextView sub = text("", 11, 0xCCFFFFFF, false);
                sub.setSingleLine(true);
                tx.addView(name);
                tx.addView(sub);
                LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(0, -2, 1);
                xlp.leftMargin = dp(8);
                row.addView(tx, xlp);
                card.addView(row, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

                h = new Object[]{cover, avatar, name, sub, tx};
                card.setTag(h);
                cv = card;
            } else {
                h = (Object[]) cv.getTag();
            }
            Folder f = data.get(pos);
            loadImage((ImageView) h[0], f.videos.get(0), true);
            ImageView avatar = (ImageView) h[1];
            File av = avatarFile(f.net, f.user);
            if (av.exists()) {
                avatar.setVisibility(View.VISIBLE);
                loadImage(avatar, av, false);
            } else {
                avatar.setVisibility(View.GONE);
            }
            ((LinearLayout.LayoutParams) ((View) h[4]).getLayoutParams()).leftMargin = av.exists() ? dp(8) : 0;
            ((TextView) h[2]).setText(f.title());
            ((TextView) h[3]).setText(f.net + " · " + f.videos.size());
            return cv;
        }
    }

    private final class VideoAdapter extends BaseAdapter {
        final List<File> data;

        VideoAdapter(List<File> data) { this.data = data; }

        public int getCount() { return data.size(); }
        public Object getItem(int i) { return data.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            ImageView thumb;
            if (cv == null) {
                RatioFrame card = new RatioFrame(MainActivity.this, 1.5f);
                card.setBackground(rounded(cField, 12));
                card.setClipToOutline(true);
                thumb = new ImageView(MainActivity.this);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(thumb, new FrameLayout.LayoutParams(-1, -1));
                TextView playIcon = text("▶", 13, Color.WHITE, false);
                playIcon.setShadowLayer(6, 0, 0, Color.BLACK);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                lp.setMargins(dp(8), 0, 0, dp(6));
                card.addView(playIcon, lp);
                card.setTag(thumb);
                cv = card;
            } else {
                thumb = (ImageView) cv.getTag();
            }
            loadImage(thumb, data.get(pos), true);
            return cv;
        }
    }

    // ---------- Miniaturas ----------

    private final ExecutorService imgPool = Executors.newFixedThreadPool(2);
    private volatile boolean thumbsPaused;
    private final LruCache<String, Bitmap> imgCache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    /** Carga una imagen o la miniatura de un video sin bloquear la interfaz. */
    private void loadImage(final ImageView iv, final File f, final boolean video) {
        final String key = (video ? "v:" : "i:") + f.getPath() + ":" + (video ? timeOf(f) : f.lastModified());
        iv.setTag(key);
        Bitmap cached = imgCache.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageDrawable(null);
        imgPool.execute(new Runnable() {
            public void run() {
                // Mientras se reproduce un video no se generan miniaturas: compiten por el decodificador
                while (thumbsPaused) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                // Si la celda ya muestra otro archivo (se hizo scroll), no vale la pena el trabajo
                if (!key.equals(iv.getTag())) return;
                final Bitmap b = video ? videoThumb(f, key) : decodeSmall(f, 256);
                if (b == null) return;
                imgCache.put(key, b);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (key.equals(iv.getTag())) iv.setImageBitmap(b);
                    }
                });
            }
        });
    }

    private Bitmap videoThumb(File f, String key) {
        File cache = new File(getCacheDir(), "thumbs/" + Integer.toHexString(key.hashCode()) + ".jpg");
        if (cache.exists()) {
            Bitmap b = BitmapFactory.decodeFile(cache.getPath());
            if (b != null) return b;
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getPath());
            // En Android 8.1+ el sistema entrega el cuadro ya reducido: más rápido y con menos memoria
            Bitmap full = Build.VERSION.SDK_INT >= 27
                ? r.getScaledFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
                : r.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (full == null) return null;
            int w = full.getWidth(), h = full.getHeight();
            float s = Math.min(1f, 360f / Math.min(w, h));
            Bitmap small = s < 1f
                ? Bitmap.createScaledBitmap(full, Math.round(w * s), Math.round(h * s), true) : full;
            cache.getParentFile().mkdirs();
            FileOutputStream os = new FileOutputStream(cache);
            small.compress(Bitmap.CompressFormat.JPEG, 82, os);
            os.close();
            return small;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) { }
        }
    }

    private static Bitmap decodeSmall(File f, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getPath(), o2);
    }

    // ---------- Reproductor ----------

    private Player player;

    private void play(List<File> videos, int index) {
        thumbsPaused = true;
        player = new Player(this, prefs, videos, index, new Runnable() {
            public void run() {
                player = null;
                thumbsPaused = false;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.hostPaused();
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
