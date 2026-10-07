package io.resizek;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * 縮めた写真を他のアプリへ渡す口。AndroidX の FileProvider の代わり (それだけのために
 * AndroidX を入れると APK が膨らむ)。
 *
 * 外には公開していない (exported=false)。読めるのは、クリップボードに載せた URI を
 * 貼り付けた相手だけ — クリップボードが貼り付けの瞬間に一時的な読み取り許可を付ける。
 */
public final class OutProvider extends ContentProvider {
    static final String AUTHORITY = "io.resizek.out";
    static final String MIME = "image/jpeg";

    private static final String[] DEFAULT_COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

    static File dir(Context c) {
        return new File(c.getFilesDir(), "out");
    }

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(f.getName()).build();
    }

    /** URI が指すファイル。こちらが作った名前の形でなければ null (../ などで外へ出させない)。 */
    private File fileFor(Uri uri) {
        List<String> segs = uri.getPathSegments();
        if (segs.size() != 1) return null;
        String name = segs.get(0);
        if (!name.matches("[A-Za-z0-9_-]{1,64}\\.jpg")) return null;
        File f = new File(dir(getContext()), name);
        return f.isFile() ? f : null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return fileFor(uri) != null ? MIME : null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only: " + mode);
        File f = fileFor(uri);
        if (f == null) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** 受け手がファイル名と大きさを訊いてくることがあるので、その 2 つだけ答える。 */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f = fileFor(uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection : DEFAULT_COLUMNS;
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        MatrixCursor c = new MatrixCursor(cols, 1);
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
