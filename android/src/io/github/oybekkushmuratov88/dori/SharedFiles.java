package io.github.oybekkushmuratov88.dori;

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
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Hands files in cache/shared/ to other apps through content:// URIs:
 * the camera writes a new medicine photo here, and backup/history files are
 * shared from here (Telegram, Files, Drive...). Nothing else is reachable.
 */
public class SharedFiles extends ContentProvider {
    static final String AUTHORITY = "io.github.oybekkushmuratov88.dori.files";

    static File dir(Context c) {
        File d = new File(c.getCacheDir(), "shared");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.isEmpty() || n.startsWith(".")) n = "fayl" + n;
        return n.length() > 80 ? n.substring(n.length() - 80) : n;
    }

    static Uri uriFor(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(name));
    }

    static File fileFor(Context c, Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || !name.equals(safeName(name))) return null;
        return new File(dir(c), name);
    }

    static Uri newPhotoUri(Context c) {
        return uriFor("rasm-" + System.currentTimeMillis() + ".jpg");
    }

    static boolean hasContent(Context c, Uri uri) {
        File f = fileFor(c, uri);
        return f != null && f.isFile() && f.length() > 0;
    }

    static Uri save(Context c, String name, byte[] bytes) throws IOException {
        String safe = safeName(name);
        FileOutputStream out = new FileOutputStream(new File(dir(c), safe));
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        return uriFor(safe);
    }

    static String mimeFor(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(getContext(), uri);
        if (f == null) throw new FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String getType(Uri uri) {
        return mimeFor(uri.getLastPathSegment());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f = fileFor(getContext(), uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }
}
