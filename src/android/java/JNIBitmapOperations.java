package uk.co.duckmonkey.cordova.jnibitmapoperations;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.jni.bitmap_operations.JniBitmapHolder;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class JNIBitmapOperations extends CordovaPlugin {

    private static final String JNI_BITMAPS = "jnibitmaps";
    private final JniBitmapHolder bitmapHolder = new JniBitmapHolder();
    private Map<String, File> cache = new HashMap<String, File>();
    private File jniBitmapsFolder;


    protected void pluginInitialize() {
        jniBitmapsFolder = new File(cordova.getActivity().getCacheDir(), JNI_BITMAPS);
        jniBitmapsFolder.mkdirs();
    }

    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if( action.equals("scale")) {
            JSONObject jso = args.getJSONObject(0);
            final int newWidth = jso.getInt("newWidth");
            final int newHeight = jso.getInt("newHeight");
            String path = jso.getString("path");
            if(path.contains("cdvfile://")) {
                CordovaResourceApi resourceApi = webView.getResourceApi();
                Uri fileURL = resourceApi.remapUri(Uri.parse(path));
                path = fileURL.getPath();
            }
            final String finalPath = path;
            cordova.getThreadPool().submit(new Runnable() {
                @Override
                public void run() {
                    try {

                        File f = new File(finalPath);
                        FileInputStream fis = new FileInputStream(f);
                        Bitmap b = BitmapFactory.decodeStream(fis);
                        synchronized (bitmapHolder) {
                            bitmapHolder.storeBitmap(b);
                            bitmapHolder.scaleBitmap(newWidth, newHeight, JniBitmapHolder.ScaleMethod.BilinearInterpolation);
                            b = bitmapHolder.getBitmapAndFree();
                        }
                        File folder = f.getParentFile();
                        File newFile = new File(folder, "sized");
                        FileOutputStream fos = new FileOutputStream(newFile);
                        b.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                        fos.close();
                        callbackContext.success(newFile.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                        callbackContext.error(e.getMessage());
                    }
                }
            });

            return true;
        }

        return false;
    }

    public Uri remapUri(Uri uri) {
        if( uri.getPath().contains(JNI_BITMAPS) || uri.getQueryParameterNames().contains(JNI_BITMAPS)  ) {
            return toPluginUri(uri);
        }
        return null;
    }

    public CordovaResourceApi.OpenForReadResult handleOpenForRead(Uri pluginUri) throws IOException {
        final Uri uri = fromPluginUri(pluginUri);

        File file = cache.get(uri.toString());
        if( file == null || !file.exists() ) {

            String url;

            try {

                if (uri.getQueryParameterNames().contains("url")) {
                    url = uri.getQueryParameter("url");
                } else {
                    url = uri.toString().replace("jnibitmaps/", "");
                    url = url.substring(0, url.indexOf("?"));
                }

                if (url.startsWith("file:///android_asset")) {
                    url = url.replace("file:///android_asset", "");
                    file = getAsset(url);
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    file = getHttp(url);
                }

                String action = uri.getQueryParameter("action");
                if (action != null) {
                    if (action.equals("scale")) {
                        file = doScale(uri, file);
                    }
                }
            } catch (MissingParameterException e) {
                e.printStackTrace();
                return new CordovaResourceApi.OpenForReadResult(uri, IOUtils.toInputStream(""), null, 0, null);
            }

            cache.put(uri.toString(), file);
        }

        InputStream is = new FileInputStream(file);
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

        return new CordovaResourceApi.OpenForReadResult(uri, is, type, 0, null );
    }

    private File getAsset(String path) throws IOException, MissingParameterException {
        AssetManager assetManager = cordova.getActivity().getAssets();
        InputStream is = null;

        if( path.startsWith("/"))
            path = path.substring(1);
        try {
            is = assetManager.open(path);
        } catch(FileNotFoundException fnfe) {
            is = assetManager.open("www/" + path);
        }

        if( is == null )
            throw new IOException("Path not found: " + path);

        String filename = FilenameUtils.getName(path);
        File resultFile = new File( jniBitmapsFolder, filename );
        FileOutputStream fos = new FileOutputStream(resultFile);
        IOUtils.copy(is, fos);
        fos.flush();
        fos.close();
        return resultFile;
    }

    private File doCdvFile(Uri uri, File jniBitmapsFolder) throws MissingParameterException, IOException {
        checkParameter(uri, new String[]{"url"});

        String cdvfile = uri.getQueryParameter("cdvfile");

        CordovaResourceApi resourceApi = webView.getResourceApi();
        Uri fileURL = resourceApi.remapUri(Uri.parse(cdvfile));
        File f = new File(fileURL.getPath());
        File result = new File(jniBitmapsFolder, f.getName());

        FileUtils.copyFile(f, result);
        return result;
    }

    private File doLocal(Uri uri, File jniBitmapsFolder) throws MissingParameterException {
        checkParameter(uri, new String[]{"url"});
        String url = uri.getQueryParameter("url");
        return null;
    }

    private File getHttp(String url) throws IOException, MissingParameterException {
        File downloadedFile = new File(jniBitmapsFolder, Uri.parse(url).getLastPathSegment());
        FileUtils.copyURLToFile(new URL(url), downloadedFile, 500, 500);
        return downloadedFile;
    }

    private File doScale(Uri uri, File file) throws MissingParameterException, IOException {
        checkParameter(uri, new String[]{"width", "height"});

        int width = Integer.parseInt(uri.getQueryParameter("width"));
        int height = Integer.parseInt(uri.getQueryParameter("height"));
        boolean aspect = Boolean.parseBoolean(uri.getQueryParameter("aspect"));
        return createScaledBitmap(file, width, height, aspect);
    }

    private void checkParameter(Uri uri, String[] parameters) throws MissingParameterException {
        for( String param : parameters ) {
            if (!(uri.getQueryParameterNames().contains(param)))
                throw new MissingParameterException(param);
        }
    }

    private File createScaledBitmap(File f, int width, int height, boolean aspect) throws IOException {

        FileInputStream fis = new FileInputStream(f);
        Bitmap b = BitmapFactory.decodeStream(fis);

        if( aspect ) {
            Dimension boundary = new Dimension(width, height);
            Dimension imgSize = new Dimension(b.getWidth(), b.getHeight());
            Dimension newSize = getScaledDimension(imgSize, boundary);
            width = newSize.width;
            height = newSize.height;
        }

        synchronized (bitmapHolder) {
            bitmapHolder.storeBitmap(b);
            bitmapHolder.scaleBitmap(width, height, JniBitmapHolder.ScaleMethod.BilinearInterpolation);
            b = bitmapHolder.getBitmapAndFree();
        }
        File folder = f.getParentFile();
        File newFile = new File(folder, f.getName() + "sized");
        FileOutputStream fos = new FileOutputStream(newFile);
        b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();
        return newFile;
    }

    private class MissingParameterException extends Throwable {
        public MissingParameterException(String param) {
            super(param);
        }
    }

    private Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {

        int original_width = imgSize.width;
        int original_height = imgSize.height;
        int bound_width = boundary.width;
        int bound_height = boundary.height;
        int new_width = original_width;
        int new_height = original_height;

        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }

        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }

        return new Dimension(new_width, new_height);
    }

    private class Dimension {
        public int width;
        public int height;

        public Dimension(int new_width, int new_height) {
            width = new_width;
            height = new_height;
        }
    }
}
