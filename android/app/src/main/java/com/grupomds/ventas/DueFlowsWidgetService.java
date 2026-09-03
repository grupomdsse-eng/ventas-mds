package com.grupomds.ventas;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Fuente desplazable del widget: no limita el número de tareas próximas. */
public class DueFlowsWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID));
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final int widgetId;
        private List<WidgetApi.Flow> flows = new ArrayList<>();

        Factory(Context context, int widgetId) { this.context = context; this.widgetId = widgetId; }
        @Override public void onCreate() { }

        @Override public void onDataSetChanged() {
            try { flows = WidgetApi.loadDueFlows(WidgetUpdater.duePipeline(context, widgetId), WidgetUpdater.dueStages(context, widgetId)); }
            catch (Exception ignored) { flows = new ArrayList<>(); }
        }

        @Override public void onDestroy() { flows.clear(); }
        @Override public int getCount() { return flows.size(); }

        @Override public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= flows.size()) return null;
            WidgetApi.Flow flow = flows.get(position);
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_due_flow_item);
            row.setTextViewText(R.id.widget_due_item_stage, flow.stageName.toUpperCase());
            row.setTextViewText(R.id.widget_due_item_title, flow.title);
            row.setTextViewText(R.id.widget_due_item_meta, WidgetUpdater.dueLabel(flow) + " · " + flow.clientName);
            row.setInt(R.id.widget_due_item_stage, "setBackgroundColor", WidgetUpdater.flowColor(flow));
            int pipeline = WidgetUpdater.duePipeline(context, widgetId);
            String url = WidgetApi.BASE_URL + "/index.php?page=pipeline" + (pipeline > 0 ? "&pipeline=" + pipeline : "");
            Intent open = new Intent().putExtra(MainActivity.EXTRA_WIDGET_URL, url);
            row.setOnClickFillInIntent(R.id.widget_due_item_root, open);
            return row;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position < 0 || position >= flows.size() ? 0 : flows.get(position).id; }
        @Override public boolean hasStableIds() { return true; }
    }
}
