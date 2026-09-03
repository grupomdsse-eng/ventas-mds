package com.grupomds.ventas;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Implementación de cámara de MDS Ventas.
 *
 * La cámara estándar de Capacitor solicita un permiso adicional al WebView en
 * algunos Android, incluso cuando el usuario ya autorizó la aplicación. Este
 * plugin expone MdsCamera.getPhoto(), abre la cámara del sistema directamente
 * y retorna una imagen comprimida en base64. El nombre propio evita cualquier
 * ambigüedad con @capacitor/camera cargado automáticamente.
 */
@CapacitorPlugin(
        name = "MdsCamera",
        permissions = {
                @Permission(alias = MdsCameraPlugin.CAMERA, strings = { Manifest.permission.CAMERA })
        }
)
public class MdsCameraPlugin extends Plugin {
    static final String CAMERA = "camera";
    private File pendingPhoto;
    private Uri pendingUri;

    @PluginMethod
    public void getPhoto(PluginCall call) {
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            call.reject("Este dispositivo no dispone de cámara.");
            return;
        }
        if (getPermissionState(CAMERA) != PermissionState.GRANTED) {
            requestPermissionForAlias(CAMERA, call, "cameraPermissionResult");
            return;
        }
        launchCamera(call);
    }

    @PermissionCallback
    private void cameraPermissionResult(PluginCall call) {
        if (getPermissionState(CAMERA) == PermissionState.GRANTED) {
            launchCamera(call);
        } else {
            call.reject("El permiso de cámara no está concedido.");
        }
    }

    private void launchCamera(PluginCall call) {
        try {
            File directory = new File(getContext().getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) {
                call.reject("No se pudo preparar el almacenamiento temporal de la cámara.");
                return;
            }
            clearPendingPhoto();
            pendingPhoto = File.createTempFile("mds-photo-", ".jpg", directory);
            pendingUri = FileProvider.getUriForFile(
                    getActivity(), getContext().getPackageName() + ".fileprovider", pendingPhoto);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingUri);
            intent.setClipData(ClipData.newRawUri("MDS Ventas", pendingUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            List<ResolveInfo> handlers = getContext().getPackageManager()
                    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (handlers.isEmpty()) {
                clearPendingPhoto();
                call.reject("No se encontró una aplicación de cámara disponible.");
                return;
            }
            for (ResolveInfo handler : handlers) {
                getContext().grantUriPermission(handler.activityInfo.packageName, pendingUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            startActivityForResult(call, intent, "cameraResult");
        } catch (IOException | SecurityException error) {
            clearPendingPhoto();
            call.reject("No se pudo preparar la cámara.", error);
        }
    }

    @ActivityCallback
    private void cameraResult(PluginCall call, ActivityResult result) {
        try {
            if (call == null) return;
            if (result.getResultCode() != Activity.RESULT_OK || pendingPhoto == null || !pendingPhoto.isFile()
                    || pendingPhoto.length() == 0) {
                call.reject("User cancelled photo");
                return;
            }

            Bitmap bitmap = decodeScaledPhoto(pendingPhoto, Math.max(320, call.getInt("width", 1600)));
            if (bitmap == null) {
                call.reject("No se pudo procesar la foto tomada.");
                return;
            }
            int quality = Math.max(35, Math.min(92, call.getInt("quality", 62)));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
            bitmap.recycle();
            if (!compressed || output.size() == 0) {
                call.reject("No se pudo comprimir la foto tomada.");
                return;
            }

            JSObject photo = new JSObject();
            photo.put("base64String", Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
            photo.put("format", "jpeg");
            photo.put("saved", false);
            call.resolve(photo);
        } catch (OutOfMemoryError error) {
            if (call != null) call.reject("La foto es demasiado grande para procesarla.");
        } finally {
            clearPendingPhoto();
        }
    }

    private Bitmap decodeScaledPhoto(File file, int largestSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > largestSide * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        // Las fotos no requieren canal alfa; RGB_565 reduce a la mitad la
        // memoria temporal en móviles modestos antes de comprimir el JPEG.
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) return null;

        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest <= largestSide) return bitmap;
        float scale = (float) largestSide / (float) largest;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private void clearPendingPhoto() {
        if (pendingUri != null) {
            getContext().revokeUriPermission(pendingUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        if (pendingPhoto != null && pendingPhoto.exists()) {
            // Archivo temporal privado; nunca se expone en el almacenamiento externo.
            pendingPhoto.delete();
        }
        pendingPhoto = null;
        pendingUri = null;
    }
}
