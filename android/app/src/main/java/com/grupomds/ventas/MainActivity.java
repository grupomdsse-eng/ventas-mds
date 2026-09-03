package com.grupomds.ventas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    public static final String EXTRA_WIDGET_URL = "com.grupomds.ventas.WIDGET_URL";
    private float swipeStartX;
    private float swipeStartY;
    private WebView webView;
    private int imeBottom;
    /**
     * Android 15 may force an app to draw edge-to-edge.  When that happens we
     * reserve the system bars in the WebView's real layout, rather than relying
     * on a remote page's CSS (which may not have loaded yet).
     */
    private boolean useManualSystemBarMargins;
    private boolean layoutMetricsInitialized;
    private boolean layoutMetricsDispatchQueued;
    private int pendingCssSafeTop;
    private int pendingCssSafeBottom;
    private int pendingCssViewport;
    private int pendingCssKeyboard;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Debe registrarse antes de crear el Bridge para que esté disponible
        // como window.Capacitor.Plugins.MdsAudio desde el chat.
        registerPlugin(MdsAudioPlugin.class);
        // window.Capacitor.Plugins.MdsCamera abre una captura nativa directa,
        // sin depender del permiso adicional que solicita WebView.
        registerPlugin(MdsCameraPlugin.class);
        super.onCreate(savedInstanceState);

        // Gestionamos siempre las barras en el WebView, con márgenes físicos.
        // Android 15 puede forzar edge-to-edge incluso si el tema intenta
        // evitarlo; depender de ese comportamiento hacía que la cabecera se
        // mezclase con la barra de estado en algunos móviles.
        useManualSystemBarMargins = true;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // Chats, adjuntos y fichas de clientes pueden contener información
        // sensible. Evita capturas, grabación de pantalla y miniaturas en la
        // vista de aplicaciones recientes de Android.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        getWindow().setStatusBarColor(Color.rgb(0, 116, 199));
        getWindow().setNavigationBarColor(Color.rgb(0, 91, 156));
        // En Android recientes las barras pueden ser transparentes aunque se
        // solicite un color. El fondo del decor mantiene el azul corporativo
        // visible en el área segura que dejamos alrededor del WebView.
        getWindow().getDecorView().setBackgroundColor(Color.rgb(0, 116, 199));
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        webView = getBridge().getWebView();
        getBridge().setWebViewClient(new BridgeWebViewClient(getBridge()) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Es un documento nuevo: aunque los insets no hayan cambiado,
                // hay que volver a inyectar las métricas en su DOM.
                invalidateLayoutMetrics();
                // El DOM remoto puede terminar de crear el chat después del
                // evento de carga. Repetimos una vez para fijar sus métricas.
                view.postDelayed(MainActivity.this::publishLayoutMetrics, 80);
                view.postDelayed(MainActivity.this::publishLayoutMetrics, 420);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, insets) -> {
            Insets barsInsets = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            imeBottom = insets.isVisible(WindowInsetsCompat.Type.ime()) ? Math.max(0, ime.bottom) : 0;

            if (useManualSystemBarMargins && view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams layout = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                if (layout.leftMargin != barsInsets.left || layout.topMargin != barsInsets.top
                        || layout.rightMargin != barsInsets.right || layout.bottomMargin != barsInsets.bottom) {
                    layout.leftMargin = barsInsets.left;
                    layout.topMargin = barsInsets.top;
                    layout.rightMargin = barsInsets.right;
                    layout.bottomMargin = barsInsets.bottom;
                    view.setLayoutParams(layout);
                }
            }
            publishLayoutMetrics();
            // Igual que Capacitor, cuando nosotros ya aplicamos los márgenes no
            // propagamos las barras al contenido WebView. Las métricas nativas
            // publicadas abajo continúan disponibles para el CSS del sitio.
            return useManualSystemBarMargins ? WindowInsetsCompat.CONSUMED : insets;
        });
        // Algunos WebView de fabricantes no vuelven a invocar el listener de
        // insets durante la animación del IME. Esta comprobación ligera actúa
        // como respaldo para que el compositor del chat nunca quede bajo el
        // teclado, sin depender de visualViewport en JavaScript.
        webView.getViewTreeObserver().addOnGlobalLayoutListener(this::refreshKeyboardMetricFromRoot);
        webView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                publishLayoutMetrics());
        ViewCompat.requestApplyInsets(webView);
        webView.postDelayed(this::publishLayoutMetrics, 260);

        configureEdgeNavigation();
        requestNotificationPermission();
        openWidgetRoute(getIntent());
    }

    @Override
    public void onResume() {
        super.onResume();
        publishLayoutMetrics();
        WidgetUpdater.refreshAll(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openWidgetRoute(intent);
    }

    @Override
    public void onBackPressed() {
        WebView webView = getBridge().getWebView();
        if (isKeyboardVisible(webView)) {
            hideKeyboard(webView);
            return;
        }
        super.onBackPressed();
    }

    private boolean isKeyboardVisible(WebView webView) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(webView);
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) return true;
        // Coincide con el umbral del respaldo de layout y permite que el gesto
        // Atrás cierre el teclado incluso en WebView que consume el inset IME.
        int threshold = Math.round(getResources().getDisplayMetrics().density * 120f);
        return imeBottom > threshold;
    }

    private void hideKeyboard(WebView webView) {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(webView.getWindowToken(), 0);
    }

    private void refreshKeyboardMetricFromRoot() {
        if (webView == null) return;
        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(webView);
        int current = 0;
        if (rootInsets != null && rootInsets.isVisible(WindowInsetsCompat.Type.ime())) {
            current = Math.max(0, rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
        } else {
            // Fallback para implementaciones de WebView que no propagan el
            // inset IME: el área tapada por el teclado es visible en el frame.
            Rect visible = new Rect();
            android.view.View root = webView.getRootView();
            if (root != null) {
                root.getWindowVisibleDisplayFrame(visible);
                int[] location = new int[2];
                root.getLocationOnScreen(location);
                int hidden = Math.max(0, location[1] + root.getHeight() - visible.bottom);
                int threshold = Math.round(getResources().getDisplayMetrics().density * 120f);
                if (hidden > threshold) current = hidden;
            }
        }
        if (imeBottom != current) {
            imeBottom = current;
            publishLayoutMetrics();
        }
    }

    /**
     * Publica el alto real que tiene el WebView tras el ajuste del teclado.
     * La página no debe calcularlo otra vez con visualViewport: en ciertos
     * WebView de Android eso causaba un hueco del tamaño del teclado.
     */
    private void publishLayoutMetrics() {
        if (webView == null || webView.getWidth() == 0 || webView.getHeight() == 0) return;

        int webHeight = webView.getHeight();
        int[] position = new int[2];
        webView.getLocationInWindow(position);
        int rootHeight = webView.getRootView() == null ? 0 : webView.getRootView().getHeight();
        int viewportHeight = webHeight;

        // Si una versión de Android ignora adjustResize por edge-to-edge,
        // calculamos el límite con el borde superior del IME. Si sí lo aplica,
        // conservamos el valor (más pequeño) que ya dio el sistema.
        if (imeBottom > 0 && rootHeight > 0) {
            int keyboardTop = rootHeight - imeBottom;
            int available = keyboardTop - Math.max(0, position[1]);
            if (available > 0) viewportHeight = Math.min(viewportHeight, available);
        }
        viewportHeight = Math.max(1, viewportHeight);

        // Las barras se reservan físicamente mediante MarginLayoutParams. No
        // añadimos otro relleno dentro del HTML porque provocaría un hueco
        // duplicado al abrir el teclado o al mostrar la navegación inferior.
        final int cssSafeTop = 0;
        final int cssSafeBottom = 0;
        final int cssViewport = viewportHeight;
        final int cssKeyboard = imeBottom;

        if (layoutMetricsInitialized && pendingCssSafeTop == cssSafeTop
                && pendingCssSafeBottom == cssSafeBottom && pendingCssViewport == cssViewport
                && pendingCssKeyboard == cssKeyboard) {
            return;
        }
        layoutMetricsInitialized = true;
        pendingCssSafeTop = cssSafeTop;
        pendingCssSafeBottom = cssSafeBottom;
        pendingCssViewport = cssViewport;
        pendingCssKeyboard = cssKeyboard;

        // Los insets y el layout pueden notificar decenas de veces durante la
        // animación del teclado. Coalescerlos evita repintados y parpadeos del
        // chat, y siempre publica la última medida disponible.
        if (layoutMetricsDispatchQueued) return;
        layoutMetricsDispatchQueued = true;
        webView.postDelayed(() -> {
            layoutMetricsDispatchQueued = false;
            emitLayoutMetrics(pendingCssSafeTop, pendingCssSafeBottom,
                    pendingCssViewport, pendingCssKeyboard);
        }, 16);
    }

    private void invalidateLayoutMetrics() {
        layoutMetricsInitialized = false;
    }

    private void emitLayoutMetrics(int cssSafeTop, int cssSafeBottom,
                                   int cssViewport, int cssKeyboard) {
        if (webView == null) return;
        if (webView.getWidth() == 0 || webView.getHeight() == 0) {
            invalidateLayoutMetrics();
            webView.postDelayed(this::publishLayoutMetrics, 32);
            return;
        }
        String script = "(function(){"
                + "var root=document.documentElement;if(!root)return;"
                + "var dpr=window.devicePixelRatio||1;"
                + "var px=function(value){return Math.max(0,Math.round(value/dpr))+'px';};"
                + "root.classList.add('mds-native-shell');"
                + "var syncChat=function(){var chat=!!document.querySelector('.chat-layout');if(root.classList.contains('mds-native-chat')!==chat)root.classList.toggle('mds-native-chat',chat);};syncChat();"
                + "if(!window.__mdsNativeChatObserver&&window.MutationObserver){window.__mdsNativeChatObserver=new MutationObserver(syncChat);window.__mdsNativeChatObserver.observe(root,{childList:true,subtree:true});}"
                + "root.style.setProperty('--mds-native-safe-top',px(" + cssSafeTop + "));"
                + "root.style.setProperty('--mds-native-safe-bottom',px(" + cssSafeBottom + "));"
                + "root.style.setProperty('--mds-native-chat-height',px(" + cssViewport + "));"
                + "window.__mdsNativeKeyboardHeight=Math.max(0,Math.round(" + cssKeyboard + "/dpr));"
                + "var style=document.getElementById('mds-native-layout-bridge');"
                + "if(!style){style=document.createElement('style');style.id='mds-native-layout-bridge';style.textContent="
                + "'@media (max-width:800px){'"
                + "+'html.mds-native-shell.mds-native-chat,html.mds-native-shell.mds-native-chat body,html.mds-native-shell.mds-native-chat body .content{height:var(--mds-native-chat-height,100dvh)!important;max-height:var(--mds-native-chat-height,100dvh)!important;min-height:0!important;overflow:hidden!important}'"
                + "+'html.mds-native-shell.mds-native-chat .chat-layout,html.mds-native-shell.mds-native-chat .chat-room{min-height:0!important;height:100%!important}'"
                + "+'html.mds-native-shell body>aside{padding-bottom:calc(8px + var(--mds-native-safe-bottom,0px))!important}'"
                + "+'}';(document.head||document.documentElement).appendChild(style);}" 
                + "window.dispatchEvent(new CustomEvent('mdskeyboardinsets',{detail:{height:window.__mdsNativeKeyboardHeight}}));"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    /** Abre dentro del WebView la pantalla concreta solicitada por un widget. */
    private void openWidgetRoute(Intent intent) {
        String url = intent == null ? null : intent.getStringExtra(EXTRA_WIDGET_URL);
        if (!isTrustedMdsUrl(url)) return;
        getBridge().getWebView().postDelayed(() -> getBridge().getWebView().loadUrl(url), 180);
    }

    /** Las notificaciones y widgets nunca pueden convertir la APK en un
     * navegador de URLs ajenas. Se valida esquema, host, puerto y usuario. */
    private boolean isTrustedMdsUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            java.net.URI requested = new java.net.URI(value);
            java.net.URI trusted = new java.net.URI(WidgetApi.BASE_URL);
            int requestedPort = requested.getPort() < 0 ? 443 : requested.getPort();
            int trustedPort = trusted.getPort() < 0 ? 443 : trusted.getPort();
            return "https".equalsIgnoreCase(requested.getScheme())
                    && requested.getUserInfo() == null
                    && trusted.getHost() != null
                    && trusted.getHost().equalsIgnoreCase(requested.getHost())
                    && requestedPort == trustedPort;
        } catch (java.net.URISyntaxException ignored) {
            return false;
        }
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
            if (swipeStartX <= edgeWidth && deltaX > 0) {
                if (isKeyboardVisible(webView)) {
                    hideKeyboard(webView);
                    return true;
                }
                if (!webView.canGoBack()) return false;
                webView.goBack();
                return true;
            }
            if (swipeStartX >= view.getWidth() - edgeWidth && deltaX < 0) {
                if (isKeyboardVisible(webView)) {
                    hideKeyboard(webView);
                    return true;
                }
                if (!webView.canGoForward()) return false;
                webView.goForward();
                return true;
            }
            return false;
        });
    }
}
