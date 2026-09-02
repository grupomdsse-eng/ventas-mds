package com.grupomds.ventas;

import com.capacitorjs.plugins.pushnotifications.MessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** Conserva la entrega de Capacitor y actualiza los widgets ante un pedido nuevo. */
public class MdsMessagingService extends MessagingService {
    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        if ("orders".equals(message.getData().get("widget_refresh"))) {
            WidgetUpdater.refreshPendingWidgets(getApplicationContext());
        }
    }
}
