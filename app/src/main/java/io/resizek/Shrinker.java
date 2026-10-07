package io.resizek;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Size;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/** 共有された写真を、長辺が edge 以下の JPEG に縮める。 */
final class Shrinker {
    /** 残しておく出来上がりの数。クリップボードの履歴から古いものを貼っても切れないように少し残す。 */
    private static final int KEEP = 10;

    static final class Result {
        File file;
        int width, height;        // 出来上がり
        int srcWidth, srcHeight;  // 元 (EXIF の向きを直したあと)
        long srcBytes = -1;       // 元のファイルの大きさ。分からなければ -1
        boolean scaled;           // 縮めたか (元から小さければ false)
    }

    private Shrinker() {}

    static Result shrink(Context ctx, Uri src, int edge, int quality) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        Result r = new Result();
        r.srcBytes = sizeOf(cr, src);

        final int[] hdr = new int[2];
        // ImageDecoder (API 28〜) は EXIF の向きを自分で直し、HEIF も読める。
        // setTargetSize は復号の段階で間引くので、1 億画素の写真でも全画素を展開しない。
        Bitmap bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, src), (dec, info, s) -> {
            Size sz = info.getSize();
            int w = sz.getWidth(), h = sz.getHeight();
            hdr[0] = w;
            hdr[1] = h;
            int longer = Math.max(w, h);
            if (longer > edge) {
                // 長辺はちょうど edge に。短辺は比を保って丸める (4032x3024 → 800x600)。
                if (w >= h) dec.setTargetSize(edge, Math.max(1, Math.round(h * (float) edge / w)));
                else dec.setTargetSize(Math.max(1, Math.round(w * (float) edge / h)), edge);
            }
            // JPEG に書くので、ハードウェア側の置き場ではなくふつうのメモリに。
            dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            // 広色域 (Display P3) の写真も sRGB に揃える。受け手が色の情報を読まなくても色が転ばない。
            dec.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });

        r.width = bmp.getWidth();
        r.height = bmp.getHeight();
        r.scaled = Math.max(hdr[0], hdr[1]) > edge;
        // 元の大きさは向きを直した後の並びで出す (縦の写真が「横長」と表示されないように)。
        boolean outLandscape = r.width > r.height;
        boolean hdrLandscape = hdr[0] > hdr[1];
        if (r.width != r.height && outLandscape != hdrLandscape) {
            r.srcWidth = hdr[1];
            r.srcHeight = hdr[0];
        } else {
            r.srcWidth = hdr[0];
            r.srcHeight = hdr[1];
        }

        // 透過のある画像 (スクリーンショットの PNG など) は、透けた所が黒くならないよう白に敷く。
        if (bmp.hasAlpha()) {
            Bitmap flat = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(flat);
            c.drawColor(Color.WHITE);
            c.drawBitmap(bmp, 0, 0, null);
            bmp.recycle();
            bmp = flat;
        }

        // Ultra HDR の写真は明るさの地図 (gainmap) を持っていて、そのまま書くと JPEG に同梱される。
        // 軽くするのが目的なので落とす。
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                if (bmp.hasGainmap()) bmp.setGainmap(null);
            } catch (Throwable ignored) {
            }
        }

        File dir = OutProvider.dir(ctx);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdir: " + dir);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        File out = new File(dir, "resizek_" + stamp + ".jpg");
        // JPEG を新しく書くので、元の EXIF (位置情報・機種名など) は引き継がれない。
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024)) {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)) throw new IOException("compress failed");
        }
        bmp.recycle();
        r.file = out;
        prune(dir, out);
        return r;
    }

    /** 共有元が教えてくれる元ファイルの大きさ。教えてくれなければ開いて測る。どちらもだめなら -1。 */
    private static long sizeOf(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                long n = c.getLong(0);
                if (n > 0) return n;
            }
        } catch (Exception ignored) {
        }
        try (AssetFileDescriptor fd = cr.openAssetFileDescriptor(uri, "r")) {
            if (fd != null && fd.getLength() > 0) return fd.getLength();
        } catch (Exception ignored) {
        }
        return -1;
    }

    /** 古い出来上がりを捨て、新しいほうから KEEP 個だけ残す。 */
    private static void prune(File dir, File keep) {
        File[] fs = dir.listFiles();
        if (fs == null || fs.length <= KEEP) return;
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < fs.length; i++) {
            if (!fs[i].equals(keep)) fs[i].delete();
        }
    }
}
