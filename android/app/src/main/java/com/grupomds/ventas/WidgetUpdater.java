package com.grupomds.ventas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mantiene los tres widgets ligeros y sin datos persistidos fuera de Android. */
final class WidgetUpdater {
    static final String PREFS = "mds_ventas_widgets";
    static final String CHAT_ID = "chat_contact_id_";
    static final String CHAT_NAME = "chat_contact_name_";
    static final String ACTION_REFRESH_DUE = "com.grupomds.ventas.REFRESH_DUE_WIDGET";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private WidgetUpdater() { }

    static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] dueIds = manager.getAppWidgetIds(new ComponentName(app, DueFlowsWidgetProvider.class));
        if (dueIds.length > 0) updateDueWidgets(app, manager, dueIds);
        int[] chatIds = manager.getAppWidgetIds(new ComponentName(app, ChatWidgetProvider.class));
        if (chatIds.length > 0) updateChatWidgets(app, manager, chatIds);
        int[] orderIds = manager.getAppWidgetIds(new ComponentName(app, QuickOrderWidgetProvider.class));
        if (orderIds.length > 0) updateQuickOrderWidgets(app, manager, orderIds);
    }

    static void updateDueWidgets(Context context, AppWidgetManager manager, int[] ids) {
        showDueLoading(context, manager, ids);
        EXECUTOR.execute(() -> {
            String content;
            try { content = dueText(WidgetApi.loadDueFlows()); }
            catch (Exception error) { content = "Abre MDS Ventas e inicia sesión para mostrar tus tareas."; }
            final String result = content;
            for (int id : ids) manager.updateAppWidget(id, dueViews(context, result));
        });
    }

    private static void showDueLoading(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, dueViews(context, "Actualizando tareas…"));
    }

    private static String dueText(List<WidgetApi.Flow> flows) {
        if (flows.isEmpty()) return "No hay flujos que venzan hoy ni mañana.";
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Calendar calendar = Calendar.getInstance(); calendar.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrow = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < flows.size(); i++) {
            WidgetApi.Flow flow = flows.get(i);
            if (i > 0) text.append("\n");
            String day = flow.dueDay.equals(today) ? "Hoy" : (flow.dueDay.equals(tomorrow) ? "Mañana" : flow.dueDay);
            text.append(day);
            if (!flow.dueTime.isEmpty()) text.append(" ").append(flow.dueTime);
            text.append(" · ").append(flow.title);
            if (!flow.clientName.isEmpty() && !"Sin cliente".equals(flow.clientName)) text.append("\n   ").append(flow.clientName);
        }
        return text.toString();
    }

    private static RemoteViews dueViews(Context context, String content) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_due_flows);
        views.setTextViewText(R.id.widget_due_items, content);
        views.setOnClickPendingIntent(R.id.widget_due_root, appPendingIntent(context, WidgetApi.BASE_URL + "/index.php?page=pipeline", 101));
        Intent refresh = new Intent(context, DueFlowsWidgetProvider.class).setAction(ACTION_REFRESH_DUE);
        views.setOnClickPendingIntent(R.id.widget_due_refresh, broadcastPendingIntent(context, refresh, 102));
        return views;
    }

    static void updateChatWidgets(Context context, AppWidgetManager manager, int[] ids) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int id : ids) {
            int contactId = prefs.getInt(CHAT_ID + id, 0);
            String name = prefs.getString(CHAT_NAME + id, "Selecciona una persona");
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_direct_chat);
            views.setTextViewText(R.id.widget_chat_name, name);
            boolean configured = contactId > 0;
            views.setTextViewText(R.id.widget_chat_caption, configured ? "Abrir conversación" : "Toca para elegir un compañero");
            String url = configured ? WidgetApi.BASE_URL + "/index.php?page=chat&with=" + contactId : WidgetApi.BASE_URL + "/index.php?page=chat";
            views.setOnClickPendingIntent(R.id.widget_chat_root, appPendingIntent(context, url, 200 + id));
            manager.updateAppWidget(id, views);
        }
    }

    static void updateQuickOrderWidgets(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_order);
            Intent launch = new Intent(context, QuickOrderActivity.class);
            views.setOnClickPendingIntent(R.id.widget_order_root, activityPendingIntent(context, launch, 300 + id));
            views.setOnClickPendingIntent(R.id.widget_order_action, activityPendingIntent(context, launch, 400 + id));
            manager.updateAppWidget(id, views);
        }
    }

    static void clearChatWidget(Context context, int id) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(CHAT_ID + id).remove(CHAT_NAME + id).apply();
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private static PendingIntent appPendingIntent(Context context, String url, int requestCode) {
        Intent launch = new Intent(context, MainActivity.class).putExtra(MainActivity.EXTRA_WIDGET_URL, url).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, launch, flags());
    }

    private static PendingIntent activityPendingIntent(Context context, Intent intent, int requestCode) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, flags());
    }

    private static PendingIntent broadcastPendingIntent(Context context, Intent intent, int requestCode) {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags());
    }
}
