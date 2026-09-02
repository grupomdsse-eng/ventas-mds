package com.grupomds.ventas;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Tablón compacto con los pedidos que siguen pendientes de tramitar. */
public class PendingOrdersWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetUpdater.updatePendingOrderWidgets(context, manager, ids);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (WidgetUpdater.ACTION_REFRESH_PENDING.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, PendingOrdersWidgetProvider.class));
            WidgetUpdater.updatePendingOrderWidgets(context, manager, ids);
        }
    }
}
