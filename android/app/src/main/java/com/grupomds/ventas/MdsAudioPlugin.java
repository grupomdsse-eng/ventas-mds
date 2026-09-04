package com.grupomds.ventas;

import android.Manifest;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * Reproduce los audios de pedidos dentro de un diálogo nativo pequeño.
     * No delega la URL al visor externo de Android, que en algunos móviles se
     * mostraba como una pantalla negra sin una forma clara de volver al pedido.
     */
    @PluginMethod
    public void playOrderAudio(PluginCall call) {
        String url = call.getString("url", "");
        if (!isTrustedMdsUrl(url)) {
            call.reject("La dirección del audio no es válida.");
            return;
        }
        String name = call.getString("name", "Audio del pedido");
        getActivity().runOnUiThread(() -> {
            try {
                showOrderAudioDialog(url, name == null || name.isBlank() ? "Audio del pedido" : name);
                call.resolve();
            } catch (Exception error) {
                call.reject("No se pudo abrir el reproductor de audio.", error);
            }
        });
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

    private boolean isTrustedMdsUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI requested = new URI(value);
            URI trusted = new URI(WidgetApi.BASE_URL);
            int requestedPort = requested.getPort() < 0 ? 443 : requested.getPort();
            int trustedPort = trusted.getPort() < 0 ? 443 : trusted.getPort();
            return "https".equalsIgnoreCase(requested.getScheme())
                    && requested.getUserInfo() == null
                    && trusted.getHost() != null
                    && trusted.getHost().equalsIgnoreCase(requested.getHost())
                    && requestedPort == trustedPort;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showOrderAudioDialog(String url, String title) throws Exception {
        final Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);

        int padding = dp(18);
        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(padding, padding, padding, padding);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.WHITE);
        cardBackground.setCornerRadius(dp(14));
        card.setBackground(cardBackground);

        LinearLayout titleRow = new LinearLayout(getActivity());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(getActivity());
        heading.setText(title);
        heading.setTextColor(Color.rgb(0, 91, 156));
        heading.setTextSize(16);
        heading.setMaxLines(1);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(getActivity());
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(Color.rgb(60, 88, 113));
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setContentDescription("Cerrar audio");
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        card.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(getActivity());
        status.setText("Preparando audio…");
        status.setTextColor(Color.rgb(82, 108, 130));
        status.setTextSize(13);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        card.addView(status, statusParams);

        Button playPause = new Button(getActivity());
        playPause.setText("Preparando…");
        playPause.setEnabled(false);
        playPause.setTextColor(Color.WHITE);
        playPause.setBackgroundColor(Color.rgb(0, 116, 199));
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        controlParams.topMargin = dp(14);
        card.addView(playPause, controlParams);

        dialog.setContentView(card);
        final MediaPlayer player = new MediaPlayer();
        final AtomicBoolean released = new AtomicBoolean(false);
        final AtomicBoolean ready = new AtomicBoolean(false);
        Runnable release = () -> {
            if (!released.compareAndSet(false, true)) return;
            try { player.stop(); } catch (Exception ignored) { }
            player.reset();
            player.release();
        };
        dialog.setOnDismissListener(ignored -> release.run());
        close.setOnClickListener(view -> dialog.dismiss());
        playPause.setOnClickListener(view -> {
            if (!ready.get() || released.get()) return;
            if (player.isPlaying()) {
                player.pause();
                playPause.setText("Reproducir");
                status.setText("Audio en pausa");
            } else {
                player.start();
                playPause.setText("Pausar");
                status.setText("Reproduciendo dentro de MDS Ventas");
            }
        });
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setOnPreparedListener(mediaPlayer -> {
            if (released.get()) return;
            ready.set(true);
            playPause.setEnabled(true);
            playPause.setText("Pausar");
            status.setText("Reproduciendo dentro de MDS Ventas");
            mediaPlayer.start();
        });
        player.setOnCompletionListener(mediaPlayer -> {
            if (released.get()) return;
            playPause.setText("Reproducir");
            status.setText("Audio terminado");
        });
        player.setOnErrorListener((mediaPlayer, what, extra) -> {
            if (!released.get()) {
                playPause.setEnabled(false);
                status.setText("No se pudo reproducir este audio.");
            }
            return true;
        });
        Map<String, String> headers = new HashMap<>();
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null && !cookie.isBlank()) headers.put("Cookie", cookie);
        player.setDataSource(getActivity(), Uri.parse(url), headers);
        player.prepareAsync();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = .18f;
            window.setAttributes(attributes);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(Math.min(dp(360), getActivity().getResources().getDisplayMetrics().widthPixels - dp(32)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private int dp(int value) {
        return Math.round(value * getActivity().getResources().getDisplayMetrics().density);
    }
}
