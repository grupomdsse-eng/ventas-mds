package com.grupomds.ventas;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class QuickOrderWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetUpdater.updateQuickOrderWidgets(context, manager, ids);
    }
}
