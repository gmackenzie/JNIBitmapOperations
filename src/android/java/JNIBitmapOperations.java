package uk.co.duckmonkey.cordova.jnibitmapoperations;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.jni.bitmap_operations.JniBitmapHolder;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class JNIBitmapOperations extends CordovaPlugin {

    private final JniBitmapHolder bitmapHolder = new JniBitmapHolder();

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
}
