package com.grupomds.ventas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.webkit.WebView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    public static final String EXTRA_WIDGET_URL = "com.grupomds.ventas.WIDGET_URL";
    private float swipeStartX;
    private float swipeStartY;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mantiene el WebView dentro de las barras del sistema: evita que el
        // contenido remoto quede oculto tras la cámara, la hora o la barra inferior.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.rgb(0, 116, 199));
        getWindow().setNavigationBarColor(Color.rgb(0, 91, 156));
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        configureEdgeNavigation();
        requestNotificationPermission();
        openWidgetRoute(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        WidgetUpdater.refreshAll(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openWidgetRoute(intent);
    }

    /** Abre dentro del WebView la pantalla concreta solicitada por un widget. */
    private void openWidgetRoute(Intent intent) {
        String url = intent == null ? null : intent.getStringExtra(EXTRA_WIDGET_URL);
        if (url == null || !url.startsWith(WidgetApi.BASE_URL)) return;
        getBridge().getWebView().postDelayed(() -> getBridge().getWebView().loadUrl(url), 180);
    }

    /** Solicita el permiso visible de Android 13+ para los avisos de MDS Ventas. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }

    /**
     * Mantiene los gestos dentro de los bordes para no interferir con el tablero
     * Kanban ni con el desplazamiento normal de listas y conversaciones.
     */
    private void configureEdgeNavigation() {
        WebView webView = getBridge().getWebView();
        final float density = getResources().getDisplayMetrics().density;
        final float edgeWidth = 28 * density;
        final float minimumDistance = 86 * density;

        webView.setOnTouchListener((view, event) -> {
            if (event.getPointerCount() != 1) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                swipeStartX = event.getX();
                swipeStartY = event.getY();
                return false;
            }
            if (event.getActionMasked() != MotionEvent.ACTION_UP) {
                return false;
            }

            float deltaX = event.getX() - swipeStartX;
            float deltaY = event.getY() - swipeStartY;
            boolean horizontalSwipe = Math.abs(deltaX) > minimumDistance
                    && Math.abs(deltaX) > Math.abs(deltaY) * 1.45f;
            if (!horizontalSwipe) {
                return false;
            }
            if (swipeStartX <= edgeWidth && deltaX > 0 && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            if (swipeStartX >= view.getWidth() - edgeWidth && deltaX < 0 && webView.canGoForward()) {
                webView.goForward();
                return true;
            }
            return false;
        });
    }
}
