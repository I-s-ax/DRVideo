package com.isa.tiktokdl;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";

    private EditText input;
    private Button btnDownload;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("Descargador de TikTok");
        title.setTextSize(22);
        root.addView(title);

        input = new EditText(this);
        input.setHint("Pega el enlace de TikTok");
        input.setSingleLine(true);
        root.addView(input);

        Button btnPaste = new Button(this);
        btnPaste.setText("Pegar del portapapeles");
        btnPaste.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { paste(); }
        });
        root.addView(btnPaste);

        btnDownload = new Button(this);
        btnDownload.setText("Descargar");
        btnDownload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { download(); }
        });
        root.addView(btnDownload);

        status = new TextView(this);
        status.setPadding(0, dp(16), 0, 0);
        status.setText("Tip: en TikTok toca Compartir → TikTok DL.");
        root.addView(status);

        setContentView(root);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            String url = extractUrl(text);
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
            String url = extractUrl(t == null ? null : t.toString());
            input.setText(url != null ? url : (t == null ? "" : t.toString()));
        }
    }

    private static String extractUrl(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("https?://\\S+").matcher(s);
        return m.find() ? m.group() : null;
    }

    private void download() {
        final String url = extractUrl(input.getText().toString());
        if (url == null || !url.contains("tiktok")) {
            status.setText("Enlace no válido. Debe ser de tiktok.com o vm.tiktok.com");
            return;
        }
        btnDownload.setEnabled(false);
        status.setText("Obteniendo video…");

        new Thread(new Runnable() {
            public void run() {
                try {
                    String api = "https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(url, "UTF-8");
                    JSONObject j = new JSONObject(get(api));
                    if (j.optInt("code", -1) != 0) {
                        throw new Exception(j.optString("msg", "La API no respondió bien"));
                    }
                    JSONObject d = j.getJSONObject("data");
                    String video = d.optString("hdplay", "");
                    if (video.isEmpty()) video = d.optString("play", "");
                    if (video.isEmpty()) throw new Exception("No hay video (¿es un carrusel de fotos?)");
                    if (video.startsWith("/")) video = "https://www.tikwm.com" + video;

                    String name = "tiktok_" + d.optString("id", String.valueOf(System.currentTimeMillis())) + ".mp4";
                    enqueue(video, name);
                    ui("Descargando " + name + "\nSe guarda en Descargas/TikTok. Mira las notificaciones.");
                } catch (Exception e) {
                    ui("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        c.disconnect();
        return sb.toString();
    }

    private void enqueue(String videoUrl, String name) {
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(videoUrl));
        req.addRequestHeader("User-Agent", UA);
        req.setTitle(name);
        req.setDescription("TikTok DL");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TikTok/" + name);
        dm.enqueue(req);
    }

    private void ui(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                status.setText(msg);
                btnDownload.setEnabled(true);
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
