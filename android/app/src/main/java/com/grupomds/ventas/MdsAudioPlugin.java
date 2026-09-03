package com.grupomds.ventas;

import android.Manifest;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Solicita el permiso de audio desde la capa nativa antes de que el WebView
 * intente abrir MediaRecorder. Así se evita que Android conceda el permiso a
 * la aplicación, pero deje pendiente el permiso de captura del WebView.
 */
@CapacitorPlugin(
    name = "MdsAudio",
    permissions = {
        @Permission(alias = MdsAudioPlugin.MICROPHONE, strings = { Manifest.permission.RECORD_AUDIO })
    }
)
public class MdsAudioPlugin extends Plugin {
    static final String MICROPHONE = "microphone";

    @PluginMethod
    public void requestMicrophone(PluginCall call) {
        if (getPermissionState(MICROPHONE) == PermissionState.GRANTED) {
            resolve(call);
            return;
        }
        requestPermissionForAlias(MICROPHONE, call, "microphonePermissionResult");
    }

    @PermissionCallback
    private void microphonePermissionResult(PluginCall call) {
        if (getPermissionState(MICROPHONE) == PermissionState.GRANTED) {
            resolve(call);
        } else {
            call.reject("El permiso de micrófono no está concedido.");
        }
    }

    private void resolve(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", true);
        call.resolve(result);
    }
}
