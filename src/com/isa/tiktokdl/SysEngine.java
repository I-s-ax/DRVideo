package com.isa.tiktokdl;

import android.media.MediaPlayer;
import android.os.Build;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import java.io.File;

/** Reproductor del sistema: usa el decodificador por hardware del teléfono. */
final class SysEngine extends Engine {

    private MediaPlayer mp;
    private SurfaceView sv;
    private Host host;
    private boolean prepared, looping, muted, fill;
    private int vidW, vidH, boxW, boxH;
    private float applied = 1f;    // velocidad realmente aplicada

    @Override
    String name() {
        return "Sistema";
    }

    private void create() {
        mp = new MediaPlayer();
        mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(MediaPlayer m, int w, int h) {
                if (w == 0 || h == 0 || (w == vidW && h == vidH)) return;
                vidW = w;
                vidH = h;
                // El escalado lo hace el compositor del sistema, no la app
                sv.getHolder().setFixedSize(w, h);
                relayout();
            }
        });
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer m) {
                prepared = true;
                m.setLooping(looping);
                mute(muted);
                host.prepared();
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer m) {
                if (prepared) host.completed();
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer m, int what, int extra) {
                prepared = false;
                host.failed("El reproductor del sistema no pudo con este video (" + what + "/" + extra + ")");
                return true;
            }
        });
    }

    @Override
    void open(File f, SurfaceView sv, Host host) {
        this.sv = sv;
        this.host = host;
        prepared = false;
        applied = 1f;
        try {
            // Se reutiliza el mismo reproductor: cambiar de video es más rápido
            if (mp == null) create(); else mp.reset();
            mp.setDataSource(f.getAbsolutePath());
            mp.setDisplay(sv.getHolder());
            mp.prepareAsync();
        } catch (Exception e) {
            host.failed("No se pudo abrir el video");
        }
    }

    @Override
    void start() {
        if (!prepared) return;
        try {
            // Si terminó (modo "una vez"), vuelve al inicio
            if (mp.getDuration() - mp.getCurrentPosition() < 300) mp.seekTo(0);
            mp.start();
        } catch (Exception ignored) { }
    }

    @Override
    void pause() {
        try {
            if (playing()) mp.pause();
        } catch (Exception ignored) { }
    }

    @Override
    boolean playing() {
        try {
            return prepared && mp.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    int position() {
        try {
            return prepared ? mp.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    int duration() {
        try {
            return prepared ? mp.getDuration() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    void seek(int ms) {
        if (!prepared) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
            else mp.seekTo(ms);
        } catch (Exception ignored) { }
    }

    /**
     * Solo toca los parámetros si la velocidad cambia de verdad y está reproduciendo:
     * fijarlos a 1x mete el audio por el estirador de tiempo, y hacerlo en pausa reanuda el video.
     */
    @Override
    void speed(float s) {
        if (Build.VERSION.SDK_INT < 23 || !playing() || Math.abs(s - applied) < 0.01f) return;
        try {
            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(s));
            applied = s;
        } catch (Exception ignored) { }
    }

    @Override
    void mute(boolean m) {
        muted = m;
        if (!prepared) return;
        try {
            mp.setVolume(m ? 0f : 1f, m ? 0f : 1f);
        } catch (Exception ignored) { }
    }

    @Override
    void looping(boolean l) {
        looping = l;
        if (!prepared) return;
        try {
            mp.setLooping(l);
        } catch (Exception ignored) { }
    }

    @Override
    void layout(int boxW, int boxH, boolean fill) {
        this.boxW = boxW;
        this.boxH = boxH;
        this.fill = fill;
        relayout();
    }

    /** Ajusta o llena cambiando el tamaño de la superficie (se recorta sola contra la pantalla). */
    private void relayout() {
        if (sv == null || boxW == 0 || boxH == 0 || vidW == 0 || vidH == 0) return;
        float fit = Math.min((float) boxW / vidW, (float) boxH / vidH);
        float cover = Math.max((float) boxW / vidW, (float) boxH / vidH);
        float s = fill ? cover : fit;
        int w = Math.round(vidW * s), h = Math.round(vidH * s);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sv.getLayoutParams();
        if (lp.width == w && lp.height == h) return;
        lp.width = w;
        lp.height = h;
        sv.setLayoutParams(lp);
    }

    @Override
    void release() {
        prepared = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) { }
            mp = null;
        }
    }
}
