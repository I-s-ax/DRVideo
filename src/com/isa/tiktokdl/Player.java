package com.isa.tiktokdl;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
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
    private SurfaceHolder holder;
    private MediaPlayer mp;
    private boolean prepared, wantPlay = true, dragging, holding, closed;
    private int vidW, vidH;
    private int resumeAt;          // posición a retomar si el sistema destruye la superficie
    private float applied = 1f;    // velocidad realmente aplicada al reproductor

    // Ajustes (se recuerdan)
    private float speed;
    private int loop;      // 0 repetir, 1 pasar al siguiente, 2 detener al final
    private boolean fill, mute;
    private int rot;       // 0 auto, 1 horizontal, 2 vertical (solo mientras está abierto)

    private View controls;
    private TextView title, counter, playBtn, cur, dur, flash;
    private TextView speedChip, loopChip, fillChip, muteChip, rotChip;
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

        // SurfaceView: el video va directo a pantalla, sin pasar por la GPU de la interfaz
        sv = new SurfaceView(act);
        sv.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder h) {
                holder = h;
                open(index);
            }

            public void surfaceChanged(SurfaceHolder h, int format, int w, int ht) { }

            public void surfaceDestroyed(SurfaceHolder h) {
                // Pasa al salir de la app: guarda dónde iba y suelta el decodificador
                try {
                    if (prepared) resumeAt = mp.getCurrentPosition();
                } catch (Exception ignored) { }
                wantPlay = false;
                holder = null;
                releasePlayer();
            }
        });
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
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
                if (Build.VERSION.SDK_INT < 23 || !prepared || !isPlaying()) return;
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
                if (prepared) mp.setLooping(loop == 0);
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
        if (Build.VERSION.SDK_INT >= 23) chips.addView(speedChip);
        chips.addView(loopChip);
        chips.addView(fillChip);
        chips.addView(muteChip);
        chips.addView(rotChip);
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
    }

    // ---------- Reproducción ----------

    private void createPlayer() {
        mp = new MediaPlayer();
        mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(MediaPlayer m, int w, int h) {
                if (w == 0 || h == 0 || (w == vidW && h == vidH)) return;
                vidW = w;
                vidH = h;
                // El escalado lo hace el compositor del sistema, no la app
                if (holder != null) holder.setFixedSize(w, h);
                updateTransform();
            }
        });
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer m) {
                prepared = true;
                m.setLooping(loop == 0);
                applyVolume();
                seek.setMax(Math.max(1, m.getDuration()));
                dur.setText(fmt(m.getDuration()));
                if (resumeAt > 0) {
                    seekTo(resumeAt);
                    resumeAt = 0;
                }
                if (wantPlay) play(); else updatePlayButton();
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer m) {
                if (!prepared) return;
                if (loop == 1 && files.size() > 1) {
                    open((index + 1) % files.size());
                } else {
                    wantPlay = false;
                    updatePlayButton();
                    showControls();
                }
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer m, int what, int extra) {
                prepared = false;
                Toast.makeText(act, "No se pudo reproducir (¿todavía se está descargando?)",
                    Toast.LENGTH_SHORT).show();
                showControls();
                return true;
            }
        });
    }

    private void open(int i) {
        if (closed || holder == null) return;
        index = i;
        File f = files.get(index);
        title.setText(f.getName());
        counter.setText(files.size() > 1 ? (index + 1) + " / " + files.size() : "");
        seek.setProgress(0);
        cur.setText("0:00");
        dur.setText("0:00");
        prepared = false;
        applied = 1f;
        try {
            // Se reutiliza el mismo reproductor: cambiar de video es más rápido
            if (mp == null) createPlayer(); else mp.reset();
            mp.setDataSource(f.getAbsolutePath());
            mp.setDisplay(holder);
            mp.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            Toast.makeText(act, "No se pudo abrir el video", Toast.LENGTH_SHORT).show();
        }
    }

    private void step(int delta) {
        if (files.size() < 2) return;
        wantPlay = true;
        open((index + delta + files.size()) % files.size());
        showControls();
    }

    private boolean isPlaying() {
        try {
            return prepared && mp != null && mp.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    private void play() {
        if (!prepared) return;
        wantPlay = true;
        try {
            // Si terminó (modo "una vez"), vuelve al inicio
            if (mp.getDuration() - mp.getCurrentPosition() < 300) mp.seekTo(0);
            mp.start();
        } catch (Exception ignored) { }
        applySpeed();
        updatePlayButton();
        showControls();
    }

    private void pause() {
        wantPlay = false;
        try {
            if (isPlaying()) mp.pause();
        } catch (Exception ignored) { }
        updatePlayButton();
        handler.removeCallbacks(hider);
    }

    private void toggle() {
        if (isPlaying()) pause(); else play();
        showControls();
    }

    /**
     * Solo toca los parámetros de reproducción si la velocidad cambia de verdad:
     * fijarlos a 1x sin necesidad mete el audio por el estirador de tiempo y provoca cortes.
     */
    private void applySpeed() {
        refreshChips();
        if (Build.VERSION.SDK_INT < 23 || !isPlaying()) return;
        float target = holding ? 2f : speed;
        if (Math.abs(target - applied) < 0.01f) return;
        try {
            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(target));
            applied = target;
        } catch (Exception ignored) { }
    }

    private void applyVolume() {
        refreshChips();
        if (!prepared) return;
        try {
            mp.setVolume(mute ? 0f : 1f, mute ? 0f : 1f);
        } catch (Exception ignored) { }
    }

    private void seekBy(int ms) {
        if (!prepared) return;
        try {
            int pos = Math.max(0, Math.min(mp.getDuration(), mp.getCurrentPosition() + ms));
            seekTo(pos);
            flash(ms > 0 ? "+10 s ▶▶" : "◀◀ −10 s", 700);
        } catch (Exception ignored) { }
    }

    private void seekTo(int pos) {
        if (!prepared) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) mp.seekTo(pos, MediaPlayer.SEEK_CLOSEST);
            else mp.seekTo(pos);
            seek.setProgress(pos);
            cur.setText(fmt(pos));
        } catch (Exception ignored) { }
    }

    /** Ajusta (video completo) o llena (recorta bordes) cambiando el tamaño de la superficie. */
    private void updateTransform() {
        refreshChips();
        int vw = root.getWidth(), vh = root.getHeight();
        if (vw == 0 || vh == 0 || vidW == 0 || vidH == 0) return;
        float fit = Math.min((float) vw / vidW, (float) vh / vidH);
        float cover = Math.max((float) vw / vidW, (float) vh / vidH);
        float s = fill ? cover : fit;
        int w = Math.round(vidW * s), h = Math.round(vidH * s);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sv.getLayoutParams();
        if (lp.width == w && lp.height == h) return;
        lp.width = w;
        lp.height = h;
        sv.setLayoutParams(lp);
    }

    private void releasePlayer() {
        prepared = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) { }
            mp = null;
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        act.setRequestedOrientation(origOrientation);
        if (onClose != null) onClose.run();
    }

    // ---------- Controles ----------

    private final Runnable hider = new Runnable() {
        public void run() { hideControls(); }
    };

    private final Runnable tick = new Runnable() {
        public void run() {
            if (closed) return;
            if (prepared && !dragging) {
                try {
                    int p = mp.getCurrentPosition();
                    seek.setProgress(p);
                    cur.setText(fmt(p));
                } catch (Exception ignored) { }
            }
            handler.postDelayed(this, 500);
        }
    };

    private void showControls() {
        controls.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hider);
        if (isPlaying() || (wantPlay && !prepared)) handler.postDelayed(hider, HIDE_MS);
    }

    private void hideControls() {
        if (dragging) return;
        controls.setVisibility(View.GONE);
    }

    private void updatePlayButton() {
        playBtn.setText(isPlaying() ? "❚❚" : "▶");
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
