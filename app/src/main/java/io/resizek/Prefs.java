package io.resizek;

import android.content.Context;
import android.content.SharedPreferences;

/** いつもの設定 (長辺の大きさと JPEG の画質)。 */
final class Prefs {
    static final int[] EDGES = {480, 640, 800, 1024, 1280, 1600, 1920};
    static final int[] QUALITIES = {60, 70, 80, 90};
    static final int DEFAULT_EDGE = 800;
    static final int DEFAULT_QUALITY = 80;

    private static final String EDGE = "edge";
    private static final String QUALITY = "quality";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("s", Context.MODE_PRIVATE);
    }

    static int edge(Context c) {
        return sp(c).getInt(EDGE, DEFAULT_EDGE);
    }

    static int quality(Context c) {
        return sp(c).getInt(QUALITY, DEFAULT_QUALITY);
    }

    static void setEdge(Context c, int v) {
        sp(c).edit().putInt(EDGE, v).apply();
    }

    static void setQuality(Context c, int v) {
        sp(c).edit().putInt(QUALITY, v).apply();
    }
}
