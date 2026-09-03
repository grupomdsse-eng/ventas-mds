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

    static final class Stage {
        final int id;
        final String name;
        Stage(JSONObject item) { id=item.optInt("id"); name=item.optString("name", "Columna"); }
    }

    static final class Board {
        final int id;
        final String name;
        final List<Stage> stages=new ArrayList<>();
        Board(JSONObject item) {
            id=item.optInt("id"); name=item.optString("name", "Tablón");
            JSONArray items=item.optJSONArray("stages");
            if(items!=null)for(int index=0;index<items.length();index++)stages.add(new Stage(items.optJSONObject(index)));
        }
        @Override public String toString() { return name; }
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
        final List<Integer> companyIds = new ArrayList<>();

        Client(JSONObject item) {
            id = item.optInt("id");
            name = item.optString("name", "Cliente");
            email = item.optString("email", "");
            phone = item.optString("phone", "");
            String rawCompanies = item.optString("company_ids", "");
            for (String value : rawCompanies.split(",")) {
                try { if (!value.trim().isEmpty()) companyIds.add(Integer.parseInt(value.trim())); } catch (NumberFormatException ignored) { }
            }
        }

        boolean belongsTo(int companyId) { return companyIds.contains(companyId); }
    }

    static final class Company {
        final int id;
        final String name;

        Company(JSONObject item) { id = item.optInt("id"); name = item.optString("name", "Empresa"); }
        @Override public String toString() { return name; }
    }

    static final class OrderOptions {
        final String csrf;
        final List<Company> companies;
        final List<Client> clients;
        final List<Contact> preparers;
        final boolean canChoosePreparer;

        OrderOptions(String csrf, List<Company> companies, List<Client> clients, List<Contact> preparers, boolean canChoosePreparer) {
            this.csrf = csrf;
            this.companies = companies;
            this.clients = clients;
            this.preparers = preparers;
            this.canChoosePreparer = canChoosePreparer;
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

    static List<Flow> loadDueFlows() throws Exception { return loadDueFlows(0, new java.util.HashSet<>()); }

    static List<Flow> loadDueFlows(int pipelineId, java.util.Set<Integer> stageIds) throws Exception {
        StringBuilder query=new StringBuilder("widget_due_flows");
        if(pipelineId>0)query.append("&pipeline_id=").append(pipelineId);
        if(stageIds!=null&&!stageIds.isEmpty()){query.append("&stage_ids=");boolean first=true;for(Integer id:stageIds){if(id==null||id<=0)continue;if(!first)query.append(',');query.append(id);first=false;}}
        JSONArray source = getJson(query.toString()).optJSONArray("items");
        List<Flow> flows = new ArrayList<>();
        if (source != null) for (int i = 0; i < source.length(); i++) flows.add(new Flow(source.getJSONObject(i)));
        return flows;
    }

    static List<Board> loadDueOptions() throws Exception {
        JSONArray source=getJson("widget_due_flow_options").optJSONArray("pipelines");
        List<Board> boards=new ArrayList<>();
        if(source!=null)for(int index=0;index<source.length();index++){JSONObject item=source.optJSONObject(index);if(item!=null)boards.add(new Board(item));}
        return boards;
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
        List<Company> companies = new ArrayList<>();
        List<Client> clients = new ArrayList<>();
        List<Contact> preparers = new ArrayList<>();
        JSONArray companyItems = source.optJSONArray("companies");
        JSONArray clientItems = source.optJSONArray("clients");
        JSONArray preparerItems = source.optJSONArray("preparers");
        if (companyItems != null) for (int i = 0; i < companyItems.length(); i++) companies.add(new Company(companyItems.getJSONObject(i)));
        if (clientItems != null) for (int i = 0; i < clientItems.length(); i++) clients.add(new Client(clientItems.getJSONObject(i)));
        if (preparerItems != null) for (int i = 0; i < preparerItems.length(); i++) preparers.add(new Contact(preparerItems.getJSONObject(i)));
        return new OrderOptions(source.optString("csrf"), companies, clients, preparers, source.optBoolean("can_choose_preparer", false));
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

    static OrderResult createOrder(String csrf, int companyId, int clientId, int preparerId, String notes, File attachment) throws Exception {
        String boundary = "----MdsVentas" + System.currentTimeMillis();
        HttpURLConnection connection = open("widget_create_order", "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
            writeField(output, boundary, "csrf", csrf);
            writeField(output, boundary, "company_id", String.valueOf(companyId));
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

    static boolean quickReply(int recipientId, int groupId, String body, String clientMessageId) throws Exception {
        HttpURLConnection connection=open("native_quick_reply", "POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");connection.setRequestProperty("X-MDS-Native-Reply", "1");
        String payload="recipient_id="+java.net.URLEncoder.encode(String.valueOf(recipientId),"UTF-8")+"&group_id="+java.net.URLEncoder.encode(String.valueOf(groupId),"UTF-8")+"&body="+java.net.URLEncoder.encode(body==null?"":body,"UTF-8")+"&client_message_id="+java.net.URLEncoder.encode(clientMessageId,"UTF-8");
        try(java.io.OutputStream stream=connection.getOutputStream()){stream.write(payload.getBytes(StandardCharsets.UTF_8));}
        int code=connection.getResponseCode();saveCookies(connection);String value=read(code>=400?connection.getErrorStream():connection.getInputStream());connection.disconnect();if(code>=300||value.trim().startsWith("<"))throw new IOException("No se pudo enviar la respuesta.");return new JSONObject(value).optBoolean("ok");
    }
}
