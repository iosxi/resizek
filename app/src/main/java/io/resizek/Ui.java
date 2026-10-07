package io.resizek;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

/** 画面を Java で組むための小道具。レイアウト XML は使わない (読み込みと解析のぶん速い)。 */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * 画面いっぱいに描き、システムバーの分は自分で余白を取る。
     * targetSdk 35 以降は Android 15 で全画面描画が強制されるので、どの版でも同じ振る舞いに揃える。
     */
    @SuppressWarnings("deprecation")
    static void edgeToEdge(Window w, View root, int padPx) {
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = in.getSystemWindowInsetLeft();
                t = in.getSystemWindowInsetTop();
                r = in.getSystemWindowInsetRight();
                b = in.getSystemWindowInsetBottom();
            }
            v.setPadding(l + padPx, t + padPx, r + padPx, b + padPx);
            return in;
        });
    }

    /** 角の丸い塗りつぶしのボタン地。押すと波紋が出る。 */
    static RippleDrawable pill(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 16));
        return new RippleDrawable(ColorStateList.valueOf(0x40000000), g, null);
    }
}
