package com.isa.tiktokdl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.MediaStore;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Reproductor a pantalla completa con ajustes, gestos y navegación dentro de la carpeta. */
final class Player {

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private static final String[] LOOPS = {"↻ Repetir", "⇥ Siguiente", "■ Una vez"};
    private static final String[] ENGINES = {"⚙ Motor: auto", "⚙ Motor: sistema", "⚙ Motor: VLC"};
    private static final String[] ROTS = {"⟳ Giro auto", "⟳ Horizontal", "⟳ Vertical"};
    private static final int PINK = 0xFFFE2C55;
    private static final int HIDE_MS = 3500;

    private final Activity act;
    private final SharedPreferences prefs;
    private final List<File> files;
    private final Dialog dialog;
    private final Handler handler = new Handler();
    private final int origOrientation;

    private int index;
    private final Runnable onClose;
    private FrameLayout root;
    private SurfaceView sv;
    private Engine engine;
    private int engineKind;        // 0 sistema, 1 VLC (el que está en uso)
    private int enginePref;        // 0 auto, 1 sistema, 2 VLC
    private static boolean vlcBroken;
    private boolean surfaceReady;
    private boolean prepared, wantPlay = true, dragging, holding, closed;
    private int resumeAt;          // posición a retomar al reabrir el video

    // Ajustes (se recuerdan)
    private float speed;
    private int loop;      // 0 repetir, 1 pasar al siguiente, 2 detener al final
    private boolean fill, mute;
    private int rot;       // 0 auto, 1 horizontal, 2 vertical (solo mientras está abierto)

    private View controls;
    private TextView title, counter, playBtn, cur, dur, flash;
    private TextView speedChip, loopChip, fillChip, muteChip, rotChip, engineChip;
    private SeekBar seek;

    Player(Activity act, SharedPreferences prefs, List<File> files, int start, Runnable onClose) {
        this.onClose = onClose;
        this.act = act;
        this.prefs = prefs;
        this.files = files;
        this.index = start;
        this.origOrientation = act.getRequestedOrientation();
        speed = prefs.getFloat("plSpeed", 1f);
        loop = prefs.getInt("plLoop", 0);
        fill = prefs.getBoolean("plFill", false);
        mute = prefs.getBoolean("plMute", false);
        enginePref = prefs.getInt("plEngine", 0);

        dialog = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(buildUi());
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface d) { close(); }
        });
        dialog.show();
        dialog.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        handler.post(tick);
        open(index);
        showControls();
        if (!prefs.getBoolean("plHint", false)) {
            prefs.edit().putBoolean("plHint", true).apply();
            flash("Doble toque: ±10 s  ·  Mantén: 2x  ·  Desliza ↑↓: otro video", 4500);
        }
    }

    /** La app pasó a segundo plano. */
    void hostPaused() {
        pause();
    }

    // ---------- Interfaz ----------

    private View buildUi() {
        root = new FrameLayout(act);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);

        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) updateTransform();
            }
        });

        // Gestos sobre el video
        final GestureDetector gd = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (controls.getVisibility() == View.VISIBLE) hideControls(); else showControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float x = e.getX() / Math.max(1, root.getWidth());
                if (x < 0.35f) seekBy(-10000);
                else if (x > 0.65f) seekBy(10000);
                else toggle();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if ((engineKind == 0 && Build.VERSION.SDK_INT < 23) || !isPlaying()) return;
                holding = true;
                applySpeed();
                flash("2x ▶▶", 60000);
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null || files.size() < 2) return false;
                float dy = b.getY() - a.getY(), dx = b.getX() - a.getX();
                if (Math.abs(dy) < dp(90) || Math.abs(dy) < Math.abs(dx) * 1.5f) return false;
                step(dy < 0 ? 1 : -1);
                return true;
            }
        });
        root.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                gd.onTouchEvent(e);
                int a = e.getActionMasked();
                if (holding && (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)) {
                    holding = false;
                    applySpeed();
                    flash.setVisibility(View.GONE);
                }
                return true;
            }
        });

        // Aviso flotante (+10 s, 2x…)
        flash = label("", 14, true);
        flash.setBackground(round(0xB3000000, 18));
        flash.setPadding(dp(14), dp(8), dp(14), dp(8));
        flash.setVisibility(View.GONE);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        flp.topMargin = dp(84);
        root.addView(flash, flp);

        FrameLayout over = new FrameLayout(act);
        controls = over;
        root.addView(over, new FrameLayout.LayoutParams(-1, -1));

        // Barra superior
        LinearLayout top = new LinearLayout(act);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14), dp(16), dp(28));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xB3000000, 0x00000000}));
        TextView close = circle("✕", 18, 44);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        title = label("", 14, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1);
        tlp.leftMargin = dp(10);
        top.addView(title, tlp);
        counter = label("", 13, false);
        counter.setTextColor(0xCCFFFFFF);
        counter.setPadding(dp(10), 0, 0, 0);
        top.addView(counter);
        over.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Botones centrales
        LinearLayout mid = new LinearLayout(act);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = circle("◀◀", 13, 50);
        prev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { step(-1); }
        });
        playBtn = circle("❚❚", 20, 68);
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggle(); }
        });
        TextView next = circle("▶▶", 13, 50);
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { step(1); }
        });
        if (files.size() < 2) {
            prev.setVisibility(View.INVISIBLE);
            next.setVisibility(View.INVISIBLE);
        }
        mid.addView(prev, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(68), dp(68));
        plp.leftMargin = dp(28);
        plp.rightMargin = dp(28);
        mid.addView(playBtn, plp);
        mid.addView(next, new LinearLayout.LayoutParams(dp(50), dp(50)));
        over.addView(mid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // Barra inferior: progreso + ajustes
        LinearLayout bottom = new LinearLayout(act);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(0, dp(28), 0, dp(14));
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x00000000, 0xCC000000}));

        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(16), 0);
        cur = label("0:00", 12, false);
        dur = label("0:00", 12, false);
        seek = new SeekBar(act);
        seek.setProgressTintList(ColorStateList.valueOf(PINK));
        seek.setThumbTintList(ColorStateList.valueOf(PINK));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x80FFFFFF));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) cur.setText(fmt(p));
            }

            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
                handler.removeCallbacks(hider);
            }

            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                seekTo(s.getProgress());
                showControls();
            }
        });
        bar.addView(cur);
        bar.addView(seek, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(dur);
        bottom.addView(bar);

        LinearLayout chips = new LinearLayout(act);
        chips.setPadding(dp(16), dp(10), dp(8), 0);
        speedChip = chip(new Runnable() {
            public void run() {
                int i = 0;
                for (int k = 0; k < SPEEDS.length; k++) if (Math.abs(SPEEDS[k] - speed) < 0.01f) i = k;
                speed = SPEEDS[(i + 1) % SPEEDS.length];
                prefs.edit().putFloat("plSpeed", speed).apply();
                applySpeed();
            }
        });
        loopChip = chip(new Runnable() {
            public void run() {
                loop = (loop + 1) % LOOPS.length;
                prefs.edit().putInt("plLoop", loop).apply();
                if (engine != null) engine.looping(loop == 0);
            }
        });
        fillChip = chip(new Runnable() {
            public void run() {
                fill = !fill;
                prefs.edit().putBoolean("plFill", fill).apply();
                updateTransform();
            }
        });
        muteChip = chip(new Runnable() {
            public void run() {
                mute = !mute;
                prefs.edit().putBoolean("plMute", mute).apply();
                applyVolume();
            }
        });
        rotChip = chip(new Runnable() {
            public void run() {
                rot = (rot + 1) % ROTS.length;
                act.setRequestedOrientation(rot == 0 ? origOrientation
                    : rot == 1 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        });
        chips.addView(speedChip);
        chips.addView(loopChip);
        chips.addView(fillChip);
        chips.addView(muteChip);
        chips.addView(rotChip);
        engineChip = chip(new Runnable() {
            public void run() {
                enginePref = (enginePref + 1) % ENGINES.length;
                prefs.edit().putInt("plEngine", enginePref).apply();
                reopen();
            }
        });
        chips.addView(engineChip);
        TextView infoChip = chip(new Runnable() {
            public void run() { showInfo(); }
        });
        setChip(infoChip, "ⓘ Info", false);
        chips.addView(infoChip);
        TextView withChip = chip(new Runnable() {
            public void run() { openWith(); }
        });
        setChip(withChip, "↗ Abrir con", false);
        chips.addView(withChip);
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        bottom.addView(hs);
        over.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        refreshChips();
        return root;
    }

    private void refreshChips() {
        String s = speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed);
        setChip(speedChip, "⏱ " + s + "x", Math.abs(speed - 1f) > 0.01f);
        setChip(loopChip, LOOPS[loop], loop == 1);
        setChip(fillChip, fill ? "⤢ Llenar" : "⤢ Ajustar", fill);
        setChip(muteChip, mute ? "🔇 Silencio" : "🔊 Sonido", mute);
        setChip(rotChip, ROTS[rot], rot != 0);
        setChip(engineChip, ENGINES[enginePref] + (enginePref == 0 && engine != null
            ? (engineKind == 1 ? " (VLC)" : " (sistema)") : ""), enginePref != 0);
    }

    // ---------- Reproducción ----------

    /** Crea una superficie nueva. Cada motor necesita la suya: no se puede reutilizar entre motores. */
    private void makeSurface() {
        if (sv != null) root.removeView(sv);
        surfaceReady = false;
        final SurfaceView view = new SurfaceView(act);
        sv = view;
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder h) {
                if (view != sv) return;
                surfaceReady = true;
                load();
            }

            public void surfaceChanged(SurfaceHolder h, int format, int w, int ht) { }

            public void surfaceDestroyed(SurfaceHolder h) {
                if (view != sv) return;
                // Pasa al salir de la app: guarda dónde iba y suelta el decodificador
                surfaceReady = false;
                if (prepared) {
                    resumeAt = engine.position();
                    wantPlay = false;
                }
                releaseEngine();
            }
        });
        root.addView(view, 0, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    }

    /** Auto: el sistema para H.264 (lo acelera cualquier chip) y VLC para todo lo demás. */
    private int pickEngine(File f) {
        if (vlcBroken || enginePref == 1) return 0;
        if (enginePref == 2) return 1;
        String mime = videoMime(f);
        return mime == null || "video/avc".equals(mime) ? 0 : 1;
    }

    private static String videoMime(File f) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) return mime;
            }
        } catch (Exception ignored) {
        } finally {
            ex.release();
        }
        return null;
    }

    private void open(int i) {
        if (closed) return;
        index = i;
        File f = files.get(index);
        title.setText(f.getName());
        counter.setText(files.size() > 1 ? (index + 1) + " / " + files.size() : "");
        seek.setProgress(0);
        cur.setText("0:00");
        dur.setText("0:00");
        prepared = false;

        int kind = pickEngine(f);
        if (engine == null || kind != engineKind || !surfaceReady) {
            // Otro motor (o primera vez): superficie nueva; load() corre cuando esté lista
            releaseEngine();
            engineKind = kind;
            makeSurface();
        } else {
            load();
        }
    }

    private void load() {
        if (closed || !surfaceReady) return;
        final File f = files.get(index);
        final int forIndex = index;
        prepared = false;
        try {
            if (engine == null) engine = engineKind == 1 ? new VlcEngine(act) : new SysEngine();
        } catch (Throwable t) {
            // Falta la librería (p. ej. teléfono de 32 bits): se queda con el reproductor del sistema
            vlcBroken = true;
            engineKind = 0;
            engine = new SysEngine();
            Toast.makeText(act, "VLC no está disponible aquí: " + t.getClass().getSimpleName(),
                Toast.LENGTH_LONG).show();
        }
        refreshChips();
        engine.looping(loop == 0);
        engine.mute(mute);
        engine.layout(root.getWidth(), root.getHeight(), fill);
        engine.open(f, sv, new Engine.Host() {
            public void prepared() {
                if (closed || index != forIndex) return;
                prepared = true;
                int d = engine.duration();
                seek.setMax(Math.max(1, d));
                dur.setText(fmt(d));
                engine.layout(root.getWidth(), root.getHeight(), fill);
                if (resumeAt > 0) {
                    seekTo(resumeAt);
                    resumeAt = 0;
                }
                if (wantPlay) {
                    play();
                } else {
                    engine.pause();
                    updatePlayButton();
                }
            }

            public void completed() {
                if (closed || index != forIndex) return;
                if (loop == 1 && files.size() > 1) {
                    open((index + 1) % files.size());
                } else {
                    wantPlay = false;
                    updatePlayButton();
                    showControls();
                }
            }

            public void failed(String msg) {
                if (closed) return;
                prepared = false;
                Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                showControls();
            }
        });
        if (engineKind == 0) warnIfSlow(index);
    }

    private void step(int delta) {
        if (files.size() < 2) return;
        wantPlay = true;
        resumeAt = 0;
        open((index + delta + files.size()) % files.size());
        showControls();
    }

    /** Vuelve a abrir el video actual donde iba (al cambiar de motor). */
    private void reopen() {
        if (prepared) resumeAt = engine.position();
        open(index);
    }

    private boolean isPlaying() {
        return prepared && engine != null && engine.playing();
    }

    private void play() {
        if (!prepared) return;
        wantPlay = true;
        engine.start();
        applySpeed();
        updatePlayButton();
        showControls();
    }

    private void pause() {
        wantPlay = false;
        if (prepared) engine.pause();
        updatePlayButton();
        handler.removeCallbacks(hider);
    }

    private void toggle() {
        if (wantPlay && prepared) pause(); else play();
        showControls();
    }

    private void applySpeed() {
        refreshChips();
        if (prepared) engine.speed(holding ? 2f : speed);
    }

    private void applyVolume() {
        refreshChips();
        if (engine != null) engine.mute(mute);
    }

    private void seekBy(int ms) {
        if (!prepared) return;
        int pos = Math.max(0, Math.min(engine.duration(), engine.position() + ms));
        seekTo(pos);
        flash(ms > 0 ? "+10 s ▶▶" : "◀◀ −10 s", 700);
    }

    private void seekTo(int pos) {
        if (!prepared) return;
        engine.seek(pos);
        seek.setProgress(pos);
        cur.setText(fmt(pos));
    }

    private void updateTransform() {
        refreshChips();
        if (engine != null) engine.layout(root.getWidth(), root.getHeight(), fill);
    }

    private void releaseEngine() {
        prepared = false;
        if (engine != null) {
            engine.release();
            engine = null;
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacksAndMessages(null);
        releaseEngine();
        act.setRequestedOrientation(origOrientation);
        if (onClose != null) onClose.run();
    }

    // ---------- Información del archivo ----------

    private static String codecName(String mime) {
        if ("video/avc".equals(mime)) return "H.264";
        if ("video/hevc".equals(mime)) return "H.265 (HEVC)";
        if ("video/av01".equals(mime)) return "AV1";
        if ("video/x-vnd.on2.vp9".equals(mime)) return "VP9";
        if ("audio/mp4a-latm".equals(mime)) return "AAC";
        if ("audio/opus".equals(mime)) return "Opus";
        return mime.substring(mime.indexOf('/') + 1);
    }

    private static int num(MediaFormat f, String key) {
        if (!f.containsKey(key)) return 0;
        try {
            return f.getInteger(key);
        } catch (Exception e) {
            try {
                return Math.round(f.getFloat(key));
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    private static String decoderFor(MediaFormat f, String mime, int w, int h) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String name = null;
        try {
            if (Build.VERSION.SDK_INT == 21) f.setString(MediaFormat.KEY_FRAME_RATE, null);
            name = list.findDecoderForFormat(f);
        } catch (Exception ignored) { }
        if (name == null && w > 0 && h > 0) {
            try {
                name = list.findDecoderForFormat(MediaFormat.createVideoFormat(mime, w, h));
            } catch (Exception ignored) { }
        }
        return name;
    }

    private static boolean isSoftware(String decoder) {
        String n = decoder.toLowerCase();
        return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw.");
    }

    /** Describe códecs y decodificador. slow[0] queda en true si el teléfono no lo acelera por hardware. */
    private static String describe(File f, boolean[] slow) {
        StringBuilder b = new StringBuilder();
        MediaExtractor ex = new MediaExtractor();
        long durUs = 0;
        try {
            ex.setDataSource(f.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat mf = ex.getTrackFormat(i);
                String mime = mf.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mf.containsKey(MediaFormat.KEY_DURATION)) durUs = Math.max(durUs, mf.getLong(MediaFormat.KEY_DURATION));
                if (mime.startsWith("video/")) {
                    int w = num(mf, MediaFormat.KEY_WIDTH), h = num(mf, MediaFormat.KEY_HEIGHT);
                    int fps = num(mf, MediaFormat.KEY_FRAME_RATE);
                    b.append("Video: ").append(codecName(mime)).append(" · ").append(w).append("×").append(h);
                    if (fps > 0) b.append(" · ").append(fps).append(" fps");
                    if ("video/hevc".equals(mime) && Build.VERSION.SDK_INT >= 24) {
                        int p = num(mf, MediaFormat.KEY_PROFILE);
                        if (p == 0x02 || p == 0x1000 || p == 0x2000) b.append(" · 10 bits (HDR)");
                    }
                    String dec = decoderFor(mf, mime, w, h);
                    boolean soft = dec == null || isSoftware(dec);
                    if (soft) slow[0] = true;
                    b.append("\nDecodificador: ").append(dec == null ? "ninguno compatible"
                        : dec + (soft ? " (por software)" : " (por hardware)")).append('\n');
                } else if (mime.startsWith("audio/")) {
                    b.append("Audio: ").append(codecName(mime));
                    int sr = num(mf, MediaFormat.KEY_SAMPLE_RATE), ch = num(mf, MediaFormat.KEY_CHANNEL_COUNT);
                    if (sr > 0) b.append(" · ").append(sr / 1000).append(" kHz");
                    if (ch > 0) b.append(ch == 1 ? " · mono" : ch == 2 ? " · estéreo" : " · " + ch + " canales");
                    b.append('\n');
                }
            }
        } catch (Exception e) {
            b.append("No se pudo leer el archivo.\n");
        } finally {
            ex.release();
        }
        long size = f.length();
        b.append("Tamaño: ").append(String.format(java.util.Locale.US, "%.1f MB", size / 1048576f));
        if (durUs > 0) {
            b.append(String.format(java.util.Locale.US, " · %.1f Mbps", size * 8f / (durUs / 1e6f) / 1e6f));
        }
        if (slow[0]) {
            b.append("\n\nTu teléfono no acelera este formato por hardware, por eso se traba. "
                + "Prueba «Abrir con» otro reproductor (por ejemplo VLC) o vuelve a descargarlo en calidad Compatible.");
        }
        return b.toString();
    }

    private void showInfo() {
        final File f = files.get(index);
        new Thread(new Runnable() {
            public void run() {
                final String text = f.getName() + "\n\n" + describe(f, new boolean[1])
                    + "\n\nMotor en uso: " + (engine != null ? engine.name() : "—");
                handler.post(new Runnable() {
                    public void run() {
                        if (closed) return;
                        new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Información del video")
                            .setMessage(text)
                            .setPositiveButton("OK", null)
                            .show();
                    }
                });
            }
        }).start();
    }

    /** Avisa si el video que se acaba de abrir no tiene decodificador por hardware. */
    private void warnIfSlow(final int forIndex) {
        final File f = files.get(forIndex);
        new Thread(new Runnable() {
            public void run() {
                final boolean[] slow = new boolean[1];
                describe(f, slow);
                if (!slow[0]) return;
                handler.post(new Runnable() {
                    public void run() {
                        if (!closed && index == forIndex) {
                            flash("Formato sin aceleración en tu teléfono: puede trabarse · mira ⓘ Info", 5000);
                        }
                    }
                });
            }
        }).start();
    }

    /** Abre el video en otra app (VLC, galería…) usando su entrada en la galería del sistema. */
    private void openWith() {
        String path = files.get(index).getAbsolutePath();
        Uri uri = null;
        Uri[] tables = {MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Files.getContentUri("external")};
        for (int i = 0; i < tables.length && uri == null; i++) {
            try {
                Cursor c = act.getContentResolver().query(tables[i], new String[]{"_id"},
                    "_data=?", new String[]{path}, null);
                if (c != null) {
                    if (c.moveToFirst()) uri = ContentUris.withAppendedId(tables[i], c.getLong(0));
                    c.close();
                }
            } catch (Exception ignored) { }
        }
        if (uri == null) {
            Toast.makeText(act, "El sistema aún no ha indexado este video", Toast.LENGTH_SHORT).show();
            return;
        }
        pause();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "video/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivity(Intent.createChooser(i, "Abrir con"));
        } catch (Exception e) {
            Toast.makeText(act, "No hay otra app para abrir videos", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Controles ----------

    private final Runnable hider = new Runnable() {
        public void run() { hideControls(); }
    };

    private final Runnable tick = new Runnable() {
        public void run() {
            if (closed) return;
            if (prepared && !dragging) {
                int p = engine.position();
                seek.setProgress(p);
                cur.setText(fmt(p));
                if (seek.getMax() <= 1 && engine.duration() > 0) {
                    seek.setMax(engine.duration());
                    dur.setText(fmt(engine.duration()));
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    private void showControls() {
        controls.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hider);
        if (wantPlay) handler.postDelayed(hider, HIDE_MS);
    }

    private void hideControls() {
        if (dragging) return;
        controls.setVisibility(View.GONE);
    }

    private void updatePlayButton() {
        playBtn.setText(wantPlay ? "❚❚" : "▶");
    }

    private void flash(String s, int ms) {
        flash.setText(s);
        flash.setVisibility(View.VISIBLE);
        handler.removeCallbacks(flashOff);
        handler.postDelayed(flashOff, ms);
    }

    private final Runnable flashOff = new Runnable() {
        public void run() { flash.setVisibility(View.GONE); }
    };

    // ---------- Helpers ----------

    private TextView label(String s, int sp, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView circle(String s, int sp, int sizeDp) {
        TextView t = label(s, sp, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(0x80000000, sizeDp / 2));
        return t;
    }

    private TextView chip(final Runnable action) {
        TextView t = label("", 13, true);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                action.run();
                refreshChips();
                showControls();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void setChip(TextView chip, String text, boolean active) {
        if (chip == null) return;
        chip.setText(text);
        chip.setBackground(round(active ? PINK : 0x40FFFFFF, 18));
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private static String fmt(int ms) {
        int s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }
}
