package com.grupomds.ventas;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Lista desplazable: permite ver todos los pedidos pendientes desde el widget. */
public class PendingOrdersWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private List<WidgetApi.PendingOrder> orders = new ArrayList<>();

        Factory(Context context) { this.context = context; }
        @Override public void onCreate() { }

        @Override public void onDataSetChanged() {
            try { orders = WidgetApi.loadPendingOrders().items; }
            catch (Exception ignored) { orders = new ArrayList<>(); }
        }

        @Override public void onDestroy() { orders.clear(); }
        @Override public int getCount() { return orders.size(); }

        @Override public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= orders.size()) return null;
            WidgetApi.PendingOrder order = orders.get(position);
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_pending_order_item);
            row.setTextViewText(R.id.widget_pending_item_ref, order.reference);
            row.setTextViewText(R.id.widget_pending_item_client, order.clientName);
            row.setTextViewText(R.id.widget_pending_item_meta, order.preparerName);
            Intent open = new Intent(context, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_WIDGET_URL, WidgetApi.BASE_URL + "/index.php?page=orders&order=" + order.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
            row.setOnClickPendingIntent(R.id.widget_pending_item_root, PendingIntent.getActivity(context, 7000 + order.id, open, flags));
            return row;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position < 0 || position >= orders.size() ? 0 : orders.get(position).id; }
        @Override public boolean hasStableIds() { return true; }
    }
}
