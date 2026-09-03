package com.grupomds.ventas;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class DueFlowsWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetUpdater.updateDueWidgets(context, manager, ids);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (WidgetUpdater.ACTION_REFRESH_DUE.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(context, DueFlowsWidgetProvider.class));
            WidgetUpdater.updateDueWidgets(context, manager, ids);
        }
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetUpdater.clearDueConfig(context, id);
        super.onDeleted(context, ids);
    }
}
