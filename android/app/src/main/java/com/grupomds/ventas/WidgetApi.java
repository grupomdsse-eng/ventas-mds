package com.grupomds.ventas;

import android.webkit.CookieManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cliente autenticado y limitado a las rutas privadas usadas por los widgets. */
final class WidgetApi {
    static final String BASE_URL = "https://ventasmds.matriculadosdelsur.com";
    private static final int TIMEOUT_MS = 15000;

    static final class Flow {
        final int id;
        final String title;
        final String clientName;
        final String stageName;
        final String stageColor;
        final String color;
        final String dueDay;
        final String dueTime;

        Flow(JSONObject item) {
            id = item.optInt("id");
            title = item.optString("title", "Tarea sin nombre");
            clientName = item.optString("client_name", "Sin cliente");
            stageName = item.optString("stage_name", "Tarea");
            stageColor = item.optString("stage_color", "sky");
            color = item.optString("color", "");
            dueDay = item.optString("due_day", "");
            dueTime = item.optString("due_time", "");
        }
    }

    static final class Contact {
        final int id;
        final String name;
        final String role;
        final String avatarPath;

        Contact(JSONObject item) {
            id = item.optInt("id");
            name = item.optString("name", "Usuario");
            role = item.optString("role", "");
            avatarPath = item.optString("avatar_path", "");
        }
    }

    static final class Client {
        final int id;
        final String name;
        final String email;
        final String phone;

        Client(JSONObject item) {
            id = item.optInt("id");
            name = item.optString("name", "Cliente");
            email = item.optString("email", "");
            phone = item.optString("phone", "");
        }
    }

    static final class OrderOptions {
        final String csrf;
        final List<Client> clients;
        final List<Contact> preparers;

        OrderOptions(String csrf, List<Client> clients, List<Contact> preparers) {
            this.csrf = csrf;
            this.clients = clients;
            this.preparers = preparers;
        }
    }

    static final class OrderResult {
        final int id;
        final String reference;

        OrderResult(JSONObject item) {
            id = item.optInt("id");
            reference = item.optString("reference", "Pedido creado");
        }
    }

    static final class PendingOrder {
        final int id;
        final String reference;
        final String clientName;
        final String preparerName;
        final String createdAt;

        PendingOrder(JSONObject item) {
            id = item.optInt("id");
            reference = item.optString("reference", "Pedido pendiente");
            clientName = item.optString("client_name", "Sin cliente");
            preparerName = item.optString("preparer_name", "Pedidos");
            createdAt = item.optString("created_at", "");
        }
    }

    static final class PendingOrders {
        final int total;
        final List<PendingOrder> items;

        PendingOrders(int total, List<PendingOrder> items) { this.total = total; this.items = items; }
    }

    private WidgetApi() { }

    private static HttpURLConnection open(String page, String method) throws IOException {
        URL url = new URL(BASE_URL + "/index.php?page=" + page);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        String cookies = CookieManager.getInstance().getCookie(BASE_URL);
        if (cookies != null && !cookies.trim().isEmpty()) {
            connection.setRequestProperty("Cookie", cookies);
        }
        return connection;
    }

    private static void saveCookies(HttpURLConnection connection) {
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() == null || !"Set-Cookie".equalsIgnoreCase(header.getKey())) continue;
            for (String cookie : header.getValue()) {
                if (cookie != null) CookieManager.getInstance().setCookie(BASE_URL, cookie);
            }
        }
        CookieManager.getInstance().flush();
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JSONObject getJson(String page) throws Exception {
        HttpURLConnection connection = open(page, "GET");
        int code = connection.getResponseCode();
        saveCookies(connection);
        String body = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (code >= 300 || body.trim().startsWith("<")) {
            throw new IOException("Abre MDS Ventas e inicia sesión para actualizar el widget.");
        }
        return new JSONObject(body);
    }

    static List<Flow> loadDueFlows() throws Exception {
        JSONArray source = getJson("widget_due_flows").optJSONArray("items");
        List<Flow> flows = new ArrayList<>();
        if (source != null) for (int i = 0; i < source.length(); i++) flows.add(new Flow(source.getJSONObject(i)));
        return flows;
    }

    static List<Contact> loadContacts() throws Exception {
        JSONArray source = getJson("widget_contacts").optJSONArray("contacts");
        List<Contact> contacts = new ArrayList<>();
        if (source != null) for (int i = 0; i < source.length(); i++) contacts.add(new Contact(source.getJSONObject(i)));
        return contacts;
    }

    static PendingOrders loadPendingOrders() throws Exception {
        JSONObject source = getJson("widget_pending_orders");
        JSONArray items = source.optJSONArray("items");
        List<PendingOrder> orders = new ArrayList<>();
        if (items != null) for (int i = 0; i < items.length(); i++) orders.add(new PendingOrder(items.getJSONObject(i)));
        return new PendingOrders(source.optInt("total", orders.size()), orders);
    }

    static Bitmap loadAvatar(String path) throws IOException {
        if (path == null || !path.matches("uploads/avatars/[A-Za-z0-9._-]+")) return null;
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + "/" + path).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS); connection.setReadTimeout(TIMEOUT_MS); connection.setInstanceFollowRedirects(false);
        String cookies = CookieManager.getInstance().getCookie(BASE_URL);
        if (cookies != null && !cookies.trim().isEmpty()) connection.setRequestProperty("Cookie", cookies);
        int code = connection.getResponseCode(); saveCookies(connection);
        Bitmap bitmap = null;
        if (code >= 200 && code < 300) {
            try (InputStream input = connection.getInputStream()) { bitmap = BitmapFactory.decodeStream(input); }
        }
        connection.disconnect(); return bitmap;
    }

    static OrderOptions loadOrderOptions() throws Exception {
        JSONObject source = getJson("widget_order_options");
        List<Client> clients = new ArrayList<>();
        List<Contact> preparers = new ArrayList<>();
        JSONArray clientItems = source.optJSONArray("clients");
        JSONArray preparerItems = source.optJSONArray("preparers");
        if (clientItems != null) for (int i = 0; i < clientItems.length(); i++) clients.add(new Client(clientItems.getJSONObject(i)));
        if (preparerItems != null) for (int i = 0; i < preparerItems.length(); i++) preparers.add(new Contact(preparerItems.getJSONObject(i)));
        return new OrderOptions(source.optString("csrf"), clients, preparers);
    }

    static List<String> loadClientAlerts(int clientId) throws Exception {
        JSONObject source = getJson("widget_client_alerts&id=" + clientId);
        JSONArray items = source.optJSONArray("notes");
        List<String> notes = new ArrayList<>();
        if (items != null) for (int i = 0; i < items.length(); i++) notes.add(items.optString(i));
        return notes;
    }

    private static void writeField(DataOutputStream output, String boundary, String name, String value) throws IOException {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        output.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        output.writeBytes("\r\n");
    }

    private static void writeFile(DataOutputStream output, String boundary, File file) throws IOException {
        if (file == null || !file.isFile()) return;
        String mime = URLConnection.guessContentTypeFromName(file.getName());
        if (mime == null) mime = "application/octet-stream";
        String name = file.getName().replace("\"", "_");
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"attachment\"; filename=\"" + name + "\"\r\n");
        output.writeBytes("Content-Type: " + mime + "\r\n\r\n");
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        output.writeBytes("\r\n");
    }

    static OrderResult createOrder(String csrf, int clientId, int preparerId, String notes, File attachment) throws Exception {
        String boundary = "----MdsVentas" + System.currentTimeMillis();
        HttpURLConnection connection = open("widget_create_order", "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
            writeField(output, boundary, "csrf", csrf);
            writeField(output, boundary, "client_id", String.valueOf(clientId));
            writeField(output, boundary, "requested_assignee_id", String.valueOf(preparerId));
            writeField(output, boundary, "notes", notes);
            writeFile(output, boundary, attachment);
            output.writeBytes("--" + boundary + "--\r\n");
            output.flush();
        }
        int code = connection.getResponseCode();
        saveCookies(connection);
        String body = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (code >= 300 || body.trim().startsWith("<")) throw new IOException("No se pudo crear el pedido. Abre la aplicación e inicia sesión.");
        JSONObject response = new JSONObject(body);
        if (!response.optBoolean("ok")) throw new IOException(response.optString("error", "No se pudo crear el pedido."));
        return new OrderResult(response);
    }
}
