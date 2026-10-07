package io.resizek;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * 共有メニューから呼ばれる画面。受け取った写真を「いつもの設定」で縮めて見せ、
 * 「コピー」でクリップボードに載せる。あとは LINE などで貼り付けるだけ。
 */
public final class ShareActivity extends Activity {
    private static final String TAG = "resizek";

    private ImageView image;
    private ProgressBar spinner;
    private TextView info, sub, hint;
    private Button copy;

    private Uri outUri;
    private boolean copied;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();

        Uri src = sourceOf(getIntent());
        if (src == null) {
            fail(getString(R.string.no_image));
            return;
        }
        final int edge = Prefs.edge(this);
        final int quality = Prefs.quality(this);
        Thread t = new Thread(() -> {
            long t0 = System.nanoTime();
            Shrinker.Result r = null;
            Bitmap shown = null;
            Throwable err = null;
            try {
                r = Shrinker.shrink(this, src, edge, quality);
                // 見せるのは書き出した JPEG そのもの。相手に届くのと同じ絵 (画質の粗さも含めて) を確かめられる。
                shown = BitmapFactory.decodeFile(r.file.getPath());
            } catch (Throwable e) {
                err = e;
            }
            Log.i(TAG, "shrink " + (System.nanoTime() - t0) / 1000000 + " ms"
                    + (r != null ? " " + r.srcWidth + "x" + r.srcHeight + " -> " + r.width + "x" + r.height
                    + " " + r.srcBytes + " -> " + r.file.length() + " bytes" : ""));
            final Shrinker.Result fr = r;
            final Bitmap fb = shown;
            final Throwable fe = err;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (fr != null) show(fr, fb);
                else {
                    Log.w(TAG, "shrink failed", fe);
                    fail(getString(R.string.failed));
                }
            });
        }, "shrink");
        t.start();
    }

    @SuppressWarnings("deprecation")
    private static Uri sourceOf(Intent in) {
        if (in == null || !Intent.ACTION_SEND.equals(in.getAction())) return null;
        Object o = in.getParcelableExtra(Intent.EXTRA_STREAM);
        if (o instanceof Uri) return (Uri) o;
        // EXTRA_STREAM を付けず ClipData にだけ載せてくるアプリもある
        ClipData clip = in.getClipData();
        if (clip != null && clip.getItemCount() > 0) return clip.getItemAt(0).getUri();
        return null;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        FrameLayout stage = new FrameLayout(this);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stage.addView(image, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(this);
        stage.addView(spinner, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        info = text(20, R.color.text);
        info.setTypeface(Typeface.DEFAULT_BOLD);
        info.setText(R.string.working);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 12);
        root.addView(info, lp);

        sub = text(13, R.color.text_sub);
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        copy = new Button(this);
        copy.setText(R.string.copy);
        copy.setAllCaps(false);
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        copy.setTypeface(Typeface.DEFAULT_BOLD);
        copy.setTextColor(getColor(R.color.on_accent));
        copy.setBackground(Ui.pill(this, getColor(R.color.accent)));
        copy.setStateListAnimator(null);
        copy.setEnabled(false);
        copy.setAlpha(0.4f);
        copy.setOnClickListener(v -> copyToClipboard());
        lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 64));
        lp.topMargin = Ui.dp(this, 16);
        root.addView(copy, lp);

        hint = text(13, R.color.text_sub);
        hint.setText(R.string.paste_hint);
        hint.setVisibility(View.INVISIBLE);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 8);
        root.addView(hint, lp);

        Ui.edgeToEdge(getWindow(), root, Ui.dp(this, 16));
        setContentView(root);
    }

    private TextView text(float sp, int colorRes) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(colorRes));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private void show(Shrinker.Result r, Bitmap shown) {
        spinner.setVisibility(View.GONE);
        image.setImageBitmap(shown);
        outUri = OutProvider.uriFor(r.file);

        info.setText(String.format(Locale.US, "%d × %d · %s", r.width, r.height, bytes(r.file.length())));
        StringBuilder s = new StringBuilder(getString(R.string.original)).append(' ')
                .append(r.srcWidth).append(" × ").append(r.srcHeight);
        if (r.srcBytes > 0) {
            s.append(" · ").append(bytes(r.srcBytes));
            long ratio = Math.round((double) r.srcBytes / Math.max(1, r.file.length()));
            if (ratio >= 2) s.append(getString(R.string.ratio, ratio));
        }
        if (!r.scaled) s.append('\n').append(getString(R.string.not_scaled));
        sub.setText(s);

        copy.setEnabled(true);
        copy.setAlpha(1f);
    }

    private void fail(String msg) {
        spinner.setVisibility(View.GONE);
        info.setText(msg);
        copy.setVisibility(View.GONE);
    }

    private void copyToClipboard() {
        if (outUri == null) return;
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        // 載せるのは content:// の URI。貼り付けた相手には、クリップボードが一時的な読み取り許可を付ける。
        cm.setPrimaryClip(ClipData.newUri(getContentResolver(), "resizek", outUri));
        copied = true;
        copy.setText(R.string.copied);
        hint.setVisibility(View.VISIBLE);
        Log.i(TAG, "copied " + outUri);
    }

    /**
     * コピーした後に LINE などへ移ったら、この画面は閉じる。
     * 共有元 (Google フォトなど) の上に重なって残ると、戻ったときに邪魔になるため。
     * クリップボードの中身は閉じても消えない。
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (copied && !isChangingConfigurations()) finish();
    }

    private static String bytes(long n) {
        if (n < 1024 * 1024) return Math.max(1, Math.round(n / 1024.0)) + " KB";
        return String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024.0));
    }
}
