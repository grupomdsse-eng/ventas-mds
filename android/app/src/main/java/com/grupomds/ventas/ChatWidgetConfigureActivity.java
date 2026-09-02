package com.grupomds.ventas;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Configuración de un acceso directo; se puede añadir uno por cada persona. */
public class ChatWidgetConfigureActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<WidgetApi.Contact> contacts = new ArrayList<>();
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private TextView status;
    private ListView list;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_chat_widget_config);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        status = findViewById(R.id.chat_widget_status);
        list = findViewById(R.id.chat_widget_list);
        findViewById(R.id.chat_widget_close).setOnClickListener(view -> finish());
        findViewById(R.id.chat_widget_retry).setOnClickListener(view -> loadContacts());
        list.setOnItemClickListener((parent, view, position, id) -> chooseContact(contacts.get(position)));
        loadContacts();
    }

    private void loadContacts() {
        status.setText("Cargando compañeros…");
        list.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                List<WidgetApi.Contact> result = WidgetApi.loadContacts();
                runOnUiThread(() -> showContacts(result));
            } catch (Exception error) {
                runOnUiThread(() -> { status.setText("Inicia sesión en MDS Ventas antes de configurar el acceso directo."); findViewById(R.id.chat_widget_retry).setVisibility(View.VISIBLE); });
            }
        });
    }

    private void showContacts(List<WidgetApi.Contact> result) {
        contacts.clear(); contacts.addAll(result);
        findViewById(R.id.chat_widget_retry).setVisibility(View.GONE);
        if (contacts.isEmpty()) { status.setText("No hay compañeros disponibles."); return; }
        List<String> names = new ArrayList<>();
        for (WidgetApi.Contact contact : contacts) names.add(contact.name + (contact.role.isEmpty() ? "" : " · " + roleLabel(contact.role)));
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        status.setText("Elige la persona para este acceso directo.");
        list.setVisibility(View.VISIBLE);
    }

    private void chooseContact(WidgetApi.Contact contact) {
        getSharedPreferences(WidgetUpdater.PREFS, MODE_PRIVATE).edit()
                .putInt(WidgetUpdater.CHAT_ID + widgetId, contact.id)
                .putString(WidgetUpdater.CHAT_NAME + widgetId, contact.name).apply();
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        WidgetUpdater.updateChatWidgets(this, manager, new int[]{widgetId});
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private String roleLabel(String role) {
        if ("ADMIN".equals(role)) return "Administrador";
        if ("SALES".equals(role)) return "Comercial";
        if ("ORDERS".equals(role)) return "Pedidos";
        return role;
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
