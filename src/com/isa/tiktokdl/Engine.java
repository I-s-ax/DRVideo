package com.isa.tiktokdl;

import android.view.SurfaceView;

import java.io.File;

/** Motor de reproducción. Hay dos: el del sistema y VLC (decodifica por software cualquier formato). */
abstract class Engine {

    interface Host {
        /** El video ya está listo; position() y duration() son válidos. */
        void prepared();

        void completed();

        void failed(String msg);
    }

    abstract String name();

    /** Carga el archivo sobre esa superficie. Avisa con host.prepared() cuando está listo. */
    abstract void open(File f, SurfaceView sv, Host host);

    abstract void start();

    abstract void pause();

    abstract boolean playing();

    abstract int position();

    abstract int duration();

    abstract void seek(int ms);

    abstract void speed(float s);

    abstract void mute(boolean m);

    abstract void looping(boolean l);

    /** Coloca el video dentro de la caja: completo (fill=false) o recortado hasta llenarla. */
    abstract void layout(int boxW, int boxH, boolean fill);

    abstract void release();
}
