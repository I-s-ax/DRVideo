package com.isa.tiktokdl;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.File;
import java.util.ArrayList;

/**
 * Motor VLC: trae sus propios decodificadores por software, así que no depende de lo que
 * soporte el chip del teléfono (H.265 de TikTok, AV1, VP9…).
 */
final class VlcEngine extends Engine implements IVLCVout.OnNewVideoLayoutListener {

    private static LibVLC lib;     // una sola instancia por proceso

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final MediaPlayer mp;
    private SurfaceView sv;
    private Host host;
    private File file;
    private boolean ready, ended, looping, fill, released;
    private long length;
    private int boxW, boxH;
    // Tamaño que reporta la salida de video (0 si VLC dibuja con OpenGL y se coloca solo)
    private int outW, outH, visW, visH, sarNum = 1, sarDen = 1;

    VlcEngine(Context ctx) {
        if (lib == null) {
            ArrayList<String> opts = new ArrayList<String>();
            opts.add("--audio-time-stretch");     // al cambiar la velocidad no cambia el tono
            opts.add("--no-sub-autodetect-file");
            opts.add("--no-stats");
            lib = new LibVLC(ctx.getApplicationContext(), opts);
        }
        mp = new MediaPlayer(lib);
        mp.setEventListener(new MediaPlayer.EventListener() {
            public void onEvent(final MediaPlayer.Event e) {
                final int type = e.type;
                final long len = type == MediaPlayer.Event.LengthChanged ? e.getLengthChanged() : 0;
                ui.post(new Runnable() {
                    public void run() { handle(type, len); }
                });
            }
        });
    }

    @Override
    String name() {
        return "VLC (software)";
    }

    private void handle(int type, long len) {
        if (released) return;
        if (type == MediaPlayer.Event.LengthChanged) {
            if (len > 0) length = len;
        } else if (type == MediaPlayer.Event.Playing) {
            ended = false;
            if (length <= 0) length = mp.getLength();
            if (!ready) {
                ready = true;
                relayout();
                host.prepared();
            }
        } else if (type == MediaPlayer.Event.Vout) {
            relayout();
        } else if (type == MediaPlayer.Event.EndReached) {
            if (looping) {
                restart();
            } else {
                ended = true;
                host.completed();
            }
        } else if (type == MediaPlayer.Event.EncounteredError) {
            ready = false;
            host.failed("VLC no pudo reproducir este video");
        }
    }

    /** Tras llegar al final VLC queda detenido: hay que cargar el archivo otra vez. */
    private void restart() {
        ended = false;
        try {
            mp.stop();
            load();
            mp.play();
        } catch (Throwable t) {
            host.failed("VLC no pudo reiniciar el video");
        }
    }

    private void load() {
        Media m = new Media(lib, Uri.fromFile(file));
        // Decodificación por software: es justo lo que evita los tirones del decodificador del chip
        m.setHWDecoderEnabled(false, false);
        mp.setMedia(m);
        m.release();
    }

    @Override
    void open(File f, SurfaceView sv, Host host) {
        this.host = host;
        this.file = f;
        ready = false;
        ended = false;
        length = 0;
        outW = outH = 0;
        try {
            IVLCVout vout = mp.getVLCVout();
            if (this.sv != sv || !vout.areViewsAttached()) {
                if (vout.areViewsAttached()) vout.detachViews();
                this.sv = sv;
                vout.setVideoView(sv);
                if (sv.getWidth() > 0) vout.setWindowSize(sv.getWidth(), sv.getHeight());
                vout.attachViews(this);
            } else {
                mp.stop();
            }
            mp.setRate(1f);
            load();
            mp.play();
        } catch (Throwable t) {
            host.failed("VLC no pudo abrir el video: " + t);
        }
    }

    @Override
    public void onNewVideoLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight,
                                 int sarNum, int sarDen) {
        outW = width;
        outH = height;
        visW = visibleWidth;
        visH = visibleHeight;
        this.sarNum = sarNum > 0 ? sarNum : 1;
        this.sarDen = sarDen > 0 ? sarDen : 1;
        ui.post(new Runnable() {
            public void run() { relayout(); }
        });
    }

    @Override
    void start() {
        try {
            if (ended) restart(); else mp.play();
        } catch (Throwable ignored) { }
    }

    @Override
    void pause() {
        try {
            if (mp.isPlaying()) mp.pause();
        } catch (Throwable ignored) { }
    }

    @Override
    boolean playing() {
        try {
            return !released && !ended && mp.isPlaying();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    int position() {
        if (ended) return (int) length;
        try {
            return (int) Math.max(0, mp.getTime());
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    int duration() {
        return (int) Math.max(0, length);
    }

    @Override
    void seek(int ms) {
        try {
            if (ended) {
                restart();
            }
            mp.setTime(ms);
        } catch (Throwable ignored) { }
    }

    @Override
    void speed(float s) {
        try {
            mp.setRate(s);
        } catch (Throwable ignored) { }
    }

    @Override
    void mute(boolean m) {
        try {
            mp.setVolume(m ? 0 : 100);
        } catch (Throwable ignored) { }
    }

    @Override
    void looping(boolean l) {
        looping = l;
    }

    @Override
    void layout(int boxW, int boxH, boolean fill) {
        this.boxW = boxW;
        this.boxH = boxH;
        this.fill = fill;
        relayout();
    }

    private void relayout() {
        if (released || sv == null || boxW == 0 || boxH == 0) return;
        try {
            mp.getVLCVout().setWindowSize(boxW, boxH);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sv.getLayoutParams();

            if (outW * outH == 0) {
                // VLC dibuja con OpenGL sobre toda la superficie y coloca el video él mismo
                if (lp.width != -1 || lp.height != -1) {
                    lp.width = -1;
                    lp.height = -1;
                    sv.setLayoutParams(lp);
                }
                mp.setAspectRatio(null);
                float scale = 0;    // 0 = ajustar completo
                if (fill) {
                    IMedia.VideoTrack t = mp.getCurrentVideoTrack();
                    if (t != null && t.width > 0 && t.height > 0) {
                        // 4 a 7 = orientaciones giradas 90°
                        boolean swapped = t.orientation >= 4;
                        float vw = swapped ? t.height : t.width, vh = swapped ? t.width : t.height;
                        if (t.sarNum > 0 && t.sarDen > 0) vw = vw * t.sarNum / t.sarDen;
                        scale = Math.max(boxW / vw, boxH / vh);
                    }
                }
                mp.setScale(scale);
                return;
            }

            // La salida de video tiene tamaño propio: se coloca cambiando el tamaño de la vista
            mp.setAspectRatio(null);
            mp.setScale(0);
            sv.getHolder().setFixedSize(outW, outH);
            float vw = (float) visW * sarNum / sarDen, vh = visH;
            if (vw <= 0 || vh <= 0) return;
            float s = fill ? Math.max(boxW / vw, boxH / vh) : Math.min(boxW / vw, boxH / vh);
            int w = (int) Math.ceil(vw * s * outW / visW), h = (int) Math.ceil(vh * s * outH / visH);
            if (lp.width != w || lp.height != h) {
                lp.width = w;
                lp.height = h;
                sv.setLayoutParams(lp);
            }
        } catch (Throwable ignored) { }
    }

    @Override
    void release() {
        if (released) return;
        released = true;
        ui.removeCallbacksAndMessages(null);
        try {
            mp.setEventListener(null);
            mp.stop();
            mp.getVLCVout().detachViews();
            mp.release();
        } catch (Throwable ignored) { }
    }
}
