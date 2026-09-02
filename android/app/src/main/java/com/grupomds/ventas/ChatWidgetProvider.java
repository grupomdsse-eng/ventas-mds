package com.grupomds.ventas;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class ChatWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetUpdater.updateChatWidgets(context, manager, ids);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetUpdater.clearChatWidget(context, id);
    }
}
