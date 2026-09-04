package com.grupomds.ventas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Acceso 1x1 al formulario completo de nuevo flujo dentro de MDS Ventas. */
public class QuickFlowWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_flow);
            Intent intent = new Intent(context, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_WIDGET_URL, WidgetApi.BASE_URL + "/index.php?page=pipeline&quick_flow=1")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            views.setOnClickPendingIntent(R.id.widget_quick_flow_root, PendingIntent.getActivity(context, 8400 + id, intent, flags));
            manager.updateAppWidget(id, views);
        }
    }
}
