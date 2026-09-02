package com.grupomds.ventas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Widgets de inicio. Los datos se consultan con la sesión protegida de MDS Ventas. */
final class WidgetUpdater {
    static final String PREFS = "mds_ventas_widgets";
    static final String CHAT_ID = "chat_contact_id_";
    static final String CHAT_NAME = "chat_contact_name_";
    static final String CHAT_AVATAR = "chat_contact_avatar_";
    static final String ACTION_REFRESH_DUE = "com.grupomds.ventas.REFRESH_DUE_WIDGET";
    static final String ACTION_REFRESH_PENDING = "com.grupomds.ventas.REFRESH_PENDING_ORDERS_WIDGET";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int[] FLOW_CARDS = {R.id.widget_flow_card_1, R.id.widget_flow_card_2, R.id.widget_flow_card_3, R.id.widget_flow_card_4, R.id.widget_flow_card_5, R.id.widget_flow_card_6};
    private static final int[] FLOW_LABELS = {R.id.widget_flow_label_1, R.id.widget_flow_label_2, R.id.widget_flow_label_3, R.id.widget_flow_label_4, R.id.widget_flow_label_5, R.id.widget_flow_label_6};
    private static final int[] FLOW_TITLES = {R.id.widget_flow_title_1, R.id.widget_flow_title_2, R.id.widget_flow_title_3, R.id.widget_flow_title_4, R.id.widget_flow_title_5, R.id.widget_flow_title_6};
    private static final int[] FLOW_META = {R.id.widget_flow_meta_1, R.id.widget_flow_meta_2, R.id.widget_flow_meta_3, R.id.widget_flow_meta_4, R.id.widget_flow_meta_5, R.id.widget_flow_meta_6};

    private WidgetUpdater() { }

    static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] dueIds = manager.getAppWidgetIds(new ComponentName(app, DueFlowsWidgetProvider.class));
        if (dueIds.length > 0) updateDueWidgets(app, manager, dueIds);
        int[] chatIds = manager.getAppWidgetIds(new ComponentName(app, ChatWidgetProvider.class));
        if (chatIds.length > 0) updateChatWidgets(app, manager, chatIds);
        int[] quickOrderIds = manager.getAppWidgetIds(new ComponentName(app, QuickOrderWidgetProvider.class));
        if (quickOrderIds.length > 0) updateQuickOrderWidgets(app, manager, quickOrderIds);
        int[] pendingIds = manager.getAppWidgetIds(new ComponentName(app, PendingOrdersWidgetProvider.class));
        if (pendingIds.length > 0) updatePendingOrderWidgets(app, manager, pendingIds);
    }

    static void updateDueWidgets(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, dueViews(context, null, "Actualizando…"));
        EXECUTOR.execute(() -> {
            try {
                List<WidgetApi.Flow> flows = WidgetApi.loadDueFlows();
                for (int id : ids) manager.updateAppWidget(id, dueViews(context, flows, ""));
            } catch (Exception error) {
                for (int id : ids) manager.updateAppWidget(id, dueViews(context, null, "Abre MDS Ventas e inicia sesión."));
            }
        });
    }

    private static RemoteViews dueViews(Context context, List<WidgetApi.Flow> flows, String message) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_due_flows);
        views.setOnClickPendingIntent(R.id.widget_due_root, appPendingIntent(context, WidgetApi.BASE_URL + "/index.php?page=pipeline", 101));
        Intent refresh = new Intent(context, DueFlowsWidgetProvider.class).setAction(ACTION_REFRESH_DUE);
        views.setOnClickPendingIntent(R.id.widget_due_refresh, broadcastPendingIntent(context, refresh, 102));
        boolean ready = flows != null;
        views.setViewVisibility(R.id.widget_due_empty, !ready || flows.isEmpty() ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.widget_due_empty, message.isEmpty() ? "No hay flujos que venzan hoy ni mañana." : message);
        views.setTextViewText(R.id.widget_due_count, ready ? flows.size() + " próximos" : "Consultando");
        for (int index = 0; index < FLOW_CARDS.length; index++) {
            if (ready && index < flows.size()) {
                WidgetApi.Flow flow = flows.get(index);
                views.setViewVisibility(FLOW_CARDS[index], View.VISIBLE);
                views.setTextViewText(FLOW_LABELS[index], flow.stageName.toUpperCase(Locale.ROOT));
                views.setTextViewText(FLOW_TITLES[index], flow.title);
                views.setTextViewText(FLOW_META[index], dueLabel(flow) + " · " + flow.clientName);
                views.setInt(FLOW_LABELS[index], "setBackgroundColor", flowColor(flow));
            } else views.setViewVisibility(FLOW_CARDS[index], View.GONE);
        }
        return views;
    }

    private static String dueLabel(WidgetApi.Flow flow) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Calendar tomorrow = Calendar.getInstance(); tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String next = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.getTime());
        String day = flow.dueDay.equals(today) ? "Hoy" : (flow.dueDay.equals(next) ? "Mañana" : flow.dueDay);
        return flow.dueTime.isEmpty() ? day : day + " " + flow.dueTime;
    }

    private static int flowColor(WidgetApi.Flow flow) {
        try { if (flow.color.matches("#[0-9a-fA-F]{6}")) return Color.parseColor(flow.color); } catch (Exception ignored) { }
        if ("violet".equals(flow.stageColor)) return Color.rgb(132, 94, 247);
        if ("amber".equals(flow.stageColor)) return Color.rgb(255, 181, 34);
        if ("rose".equals(flow.stageColor)) return Color.rgb(231, 76, 104);
        if ("emerald".equals(flow.stageColor)) return Color.rgb(22, 160, 114);
        if ("slate".equals(flow.stageColor)) return Color.rgb(100, 116, 139);
        return Color.rgb(0, 116, 199);
    }

    static void updateChatWidgets(Context context, AppWidgetManager manager, int[] ids) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int id : ids) {
            int contactId = prefs.getInt(CHAT_ID + id, 0);
            String name = prefs.getString(CHAT_NAME + id, "?");
            String avatar = prefs.getString(CHAT_AVATAR + id, "");
            manager.updateAppWidget(id, chatViews(context, name, initialAvatar(name), contactId));
            if (contactId > 0 && avatar != null && !avatar.isEmpty()) {
                final int widgetId = id; final String contactName = name; final String avatarPath = avatar;
                EXECUTOR.execute(() -> {
                    try {
                        Bitmap image = WidgetApi.loadAvatar(avatarPath);
                        if (image != null) manager.updateAppWidget(widgetId, chatViews(context, contactName, circularAvatar(image), contactId));
                    } catch (Exception ignored) { }
                });
            }
        }
    }

    private static RemoteViews chatViews(Context context, String name, Bitmap avatar, int contactId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_direct_chat);
        views.setImageViewBitmap(R.id.widget_chat_avatar, avatar);
        String url = contactId > 0 ? WidgetApi.BASE_URL + "/index.php?page=chat&with=" + contactId : WidgetApi.BASE_URL + "/index.php?page=chat";
        views.setContentDescription(R.id.widget_chat_avatar, contactId > 0 ? "Abrir conversación con " + name : "Configura el chat directo");
        views.setOnClickPendingIntent(R.id.widget_chat_root, appPendingIntent(context, url, 200 + contactId));
        return views;
    }

    private static Bitmap initialAvatar(String name) {
        Bitmap bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(0, 116, 199)); canvas.drawCircle(80, 80, 80, paint);
        paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(76); paint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics metrics = paint.getFontMetrics(); canvas.drawText((name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT)), 80, 80 - (metrics.ascent + metrics.descent) / 2, paint);
        return bitmap;
    }

    private static Bitmap circularAvatar(Bitmap source) {
        int edge = Math.min(source.getWidth(), source.getHeight()); if (edge <= 0) return source;
        Bitmap result = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); canvas.drawColor(Color.TRANSPARENT);
        canvas.save(); canvas.clipRect(new RectF(0, 0, 160, 160));
        android.graphics.Path circle = new android.graphics.Path(); circle.addCircle(80, 80, 80, android.graphics.Path.Direction.CW); canvas.clipPath(circle);
        int left = (source.getWidth() - edge) / 2, top = (source.getHeight() - edge) / 2;
        canvas.drawBitmap(source, new Rect(left, top, left + edge, top + edge), new Rect(0, 0, 160, 160), paint); canvas.restore();
        return result;
    }

    static void updateQuickOrderWidgets(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_order);
            views.setOnClickPendingIntent(R.id.widget_order_root, activityPendingIntent(context, new Intent(context, QuickOrderActivity.class), 300 + id));
            manager.updateAppWidget(id, views);
        }
    }

    static void updatePendingOrderWidgets(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, pendingOrderViews(context, id, null, "Actualizando…"));
        EXECUTOR.execute(() -> {
            try {
                WidgetApi.PendingOrders orders = WidgetApi.loadPendingOrders();
                for (int id : ids) manager.updateAppWidget(id, pendingOrderViews(context, id, orders, ""));
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_pending_list);
            } catch (Exception error) {
                for (int id : ids) manager.updateAppWidget(id, pendingOrderViews(context, id, null, "Abre MDS Ventas e inicia sesión."));
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_pending_list);
            }
        });
    }

    static void refreshPendingWidgets(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, PendingOrdersWidgetProvider.class));
        if (ids.length > 0) updatePendingOrderWidgets(app, manager, ids);
    }

    private static RemoteViews pendingOrderViews(Context context, int widgetId, WidgetApi.PendingOrders orders, String message) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_pending_orders);
        views.setOnClickPendingIntent(R.id.widget_pending_root, appPendingIntent(context, WidgetApi.BASE_URL + "/index.php?page=orders", 501));
        Intent refresh = new Intent(context, PendingOrdersWidgetProvider.class).setAction(ACTION_REFRESH_PENDING);
        views.setOnClickPendingIntent(R.id.widget_pending_refresh, broadcastPendingIntent(context, refresh, 502));
        Intent adapter = new Intent(context, PendingOrdersWidgetService.class);
        adapter.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        adapter.setData(Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_pending_list, adapter);
        views.setEmptyView(R.id.widget_pending_list, R.id.widget_pending_empty);
        boolean ready = orders != null;
        views.setViewVisibility(R.id.widget_pending_empty, View.GONE);
        views.setTextViewText(R.id.widget_pending_empty, message.isEmpty() ? "No hay pedidos pendientes." : message);
        views.setTextViewText(R.id.widget_pending_count, ready ? orders.total + " pendientes" : "Consultando");
        return views;
    }

    static void clearChatWidget(Context context, int id) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(CHAT_ID + id).remove(CHAT_NAME + id).remove(CHAT_AVATAR + id).apply();
    }

    private static int flags() { return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0); }

    private static PendingIntent appPendingIntent(Context context, String url, int requestCode) {
        Intent launch = new Intent(context, MainActivity.class).putExtra(MainActivity.EXTRA_WIDGET_URL, url).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, launch, flags());
    }

    private static PendingIntent activityPendingIntent(Context context, Intent intent, int requestCode) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, flags());
    }

    private static PendingIntent broadcastPendingIntent(Context context, Intent intent, int requestCode) { return PendingIntent.getBroadcast(context, requestCode, intent, flags()); }
}
