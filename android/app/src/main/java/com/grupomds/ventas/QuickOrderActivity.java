package com.grupomds.ventas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Formulario nativo para crear un pedido desde el widget sin cargar la web. */
public class QuickOrderActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 6101;
    private static final int FILE_REQUEST = 6102;
    private static final int CAMERA_PERMISSION_REQUEST = 6103;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<WidgetApi.Client> clients = new ArrayList<>();
    private final List<WidgetApi.Client> filteredClients = new ArrayList<>();
    private final List<WidgetApi.Contact> preparers = new ArrayList<>();
    private AutoCompleteTextView clientInput;
    private Spinner companyInput;
    private Spinner preparerInput;
    private TextView preparerLabel;
    private EditText notesInput;
    private TextView status;
    private ImageView photoPreview;
    private ProgressBar progress;
    private Button submit;
    private WidgetApi.OrderOptions options;
    private int selectedClientId;
    private File attachment;
    private File cameraOutput;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_order);
        companyInput = findViewById(R.id.quick_order_company);
        clientInput = findViewById(R.id.quick_order_client);
        preparerInput = findViewById(R.id.quick_order_preparer);
        preparerLabel = findViewById(R.id.quick_order_preparer_label);
        notesInput = findViewById(R.id.quick_order_notes);
        status = findViewById(R.id.quick_order_status);
        photoPreview = findViewById(R.id.quick_order_photo_preview);
        progress = findViewById(R.id.quick_order_progress);
        submit = findViewById(R.id.quick_order_submit);
        findViewById(R.id.quick_order_close).setOnClickListener(view -> finish());
        findViewById(R.id.quick_order_camera).setOnClickListener(view -> openCamera());
        findViewById(R.id.quick_order_file).setOnClickListener(view -> openFilePicker());
        submit.setOnClickListener(view -> submitOrder());
        clientInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedClientId = filteredClients.get(position).id;
            showClientAlerts(selectedClientId);
        });
        companyInput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { filterClients(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { filterClients(); }
        });
        loadOptions();
    }

    private void loadOptions() {
        setLoading(true, "Cargando empresas, clientes y preparadores…");
        executor.execute(() -> {
            try {
                WidgetApi.OrderOptions result = WidgetApi.loadOrderOptions();
                runOnUiThread(() -> showOptions(result));
            } catch (Exception error) {
                runOnUiThread(() -> setLoading(false, "Abre MDS Ventas e inicia sesión para crear un pedido."));
            }
        });
    }

    private void showOptions(WidgetApi.OrderOptions result) {
        options = result;
        clients.clear(); clients.addAll(result.clients);
        preparers.clear(); preparers.addAll(result.preparers);
        companyInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, result.companies));
        List<String> preparerNames = new ArrayList<>();
        preparerNames.add("Cualquier persona de Pedidos");
        for (WidgetApi.Contact contact : preparers) preparerNames.add(contact.name);
        preparerInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, preparerNames));
        boolean showPreparer = result.canChoosePreparer;
        preparerInput.setVisibility(showPreparer ? View.VISIBLE : View.GONE);
        preparerLabel.setVisibility(showPreparer ? View.VISIBLE : View.GONE);
        filterClients();
        setLoading(false, result.companies.isEmpty() ? "No tienes ninguna empresa asignada para crear pedidos." : "Selecciona empresa y cliente.");
    }

    private void filterClients() {
        filteredClients.clear(); selectedClientId = 0;
        clientInput.setText("");
        if (options == null || companyInput.getSelectedItemPosition() < 0 || options.companies.isEmpty()) { clientInput.setEnabled(false); return; }
        int companyId = options.companies.get(companyInput.getSelectedItemPosition()).id;
        for (WidgetApi.Client client : clients) if (client.belongsTo(companyId)) filteredClients.add(client);
        List<String> names = new ArrayList<>();
        for (WidgetApi.Client client : filteredClients) names.add(client.name);
        clientInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names));
        clientInput.setEnabled(true);
        clientInput.setHint(filteredClients.isEmpty() ? "No hay clientes de esta empresa" : "Escribe para buscar un cliente");
    }

    private void setLoading(boolean loading, String message) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        status.setText(message);
        submit.setEnabled(!loading && options != null);
    }

    private void showClientAlerts(int clientId) {
        executor.execute(() -> {
            try {
                List<String> notes = WidgetApi.loadClientAlerts(clientId);
                if (!notes.isEmpty()) runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Notas del cliente")
                        .setMessage(joinLines(notes))
                        .setPositiveButton("Entendido", null).show());
            } catch (Exception ignored) { }
        });
    }

    private String joinLines(List<String> items) {
        StringBuilder text = new StringBuilder();
        for (String item : items) { if (text.length() > 0) text.append("\n\n"); text.append(item); }
        return text.toString();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            cameraOutput = File.createTempFile("pedido-", ".jpg", getCacheDir());
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraOutput);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAMERA_REQUEST);
        } catch (IOException error) { Toast.makeText(this, "No se pudo preparar la cámara.", Toast.LENGTH_SHORT).show(); }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, FILE_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) launchCamera();
        else if (requestCode == CAMERA_PERMISSION_REQUEST) Toast.makeText(this, "Debes permitir el uso de la cámara.", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == CAMERA_REQUEST && cameraOutput != null && cameraOutput.length() > 0) {
            attachment = cameraOutput;
            photoPreview.setImageURI(Uri.fromFile(attachment));
            photoPreview.setVisibility(View.VISIBLE);
            status.setText("Foto preparada para el pedido.");
        }
        if (requestCode == FILE_REQUEST && data != null && data.getData() != null) {
            try {
                attachment = copyToCache(data.getData());
                photoPreview.setImageURI(Uri.fromFile(attachment));
                photoPreview.setVisibility(View.VISIBLE);
                status.setText("Imagen preparada para el pedido.");
            } catch (IOException error) { Toast.makeText(this, "No se pudo leer la imagen seleccionada.", Toast.LENGTH_SHORT).show(); }
        }
    }

    private File copyToCache(Uri uri) throws IOException {
        String name = "imagen.jpg";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        }
        String suffix = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
        File target = File.createTempFile("pedido-adjunto-", suffix, getCacheDir());
        try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Archivo no disponible");
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return target;
    }

    private WidgetApi.Client selectedClient() {
        if (selectedClientId > 0) for (WidgetApi.Client client : filteredClients) if (client.id == selectedClientId) return client;
        String text = clientInput.getText().toString().trim();
        for (WidgetApi.Client client : filteredClients) if (client.name.equalsIgnoreCase(text)) { selectedClientId = client.id; return client; }
        return null;
    }

    private File compressedAttachment() throws IOException {
        if (attachment == null || !attachment.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(attachment.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return attachment;
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 2200) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options(); decode.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(attachment.getAbsolutePath(), decode);
        if (bitmap == null) return attachment;
        int width = bitmap.getWidth(), height = bitmap.getHeight(), largest = Math.max(width, height);
        if (largest > 1600) {
            float scale = 1600f / largest;
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(width * scale), Math.round(height * scale), true);
            if (scaled != bitmap) { bitmap.recycle(); bitmap = scaled; }
        }
        File compressed = File.createTempFile("pedido-comprimido-", ".jpg", getCacheDir());
        try (FileOutputStream output = new FileOutputStream(compressed)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output); }
        bitmap.recycle();
        return compressed.length() < attachment.length() || attachment.length() > 8L * 1024 * 1024 ? compressed : attachment;
    }

    private void submitOrder() {
        if (options == null) { Toast.makeText(this, "Todavía se están cargando los datos.", Toast.LENGTH_SHORT).show(); return; }
        if (options.companies.isEmpty() || companyInput.getSelectedItemPosition() < 0) { Toast.makeText(this, "Selecciona una empresa.", Toast.LENGTH_SHORT).show(); return; }
        int companyId = options.companies.get(companyInput.getSelectedItemPosition()).id;
        WidgetApi.Client client = selectedClient();
        if (client == null) { clientInput.setError("Selecciona un cliente de la lista."); clientInput.requestFocus(); return; }
        int selectedPreparer = options.canChoosePreparer ? preparerInput.getSelectedItemPosition() : 0;
        int preparerId = options.canChoosePreparer && selectedPreparer > 0 && selectedPreparer - 1 < preparers.size() ? preparers.get(selectedPreparer - 1).id : 0;
        setLoading(true, "Creando pedido…");
        final int finalCompanyId = companyId;
        final int finalPreparerId = preparerId;
        executor.execute(() -> {
            try {
                File file = compressedAttachment();
                if (file != null && file.length() > 8L * 1024 * 1024) throw new IOException("La imagen supera el límite de 8 MB.");
                WidgetApi.OrderResult result = WidgetApi.createOrder(options.csrf, finalCompanyId, client.id, finalPreparerId, notesInput.getText().toString().trim(), file);
                runOnUiThread(() -> { Toast.makeText(this, "Pedido " + result.reference + " creado.", Toast.LENGTH_LONG).show(); finish(); });
            } catch (Exception error) {
                runOnUiThread(() -> setLoading(false, error.getMessage() == null ? "No se pudo crear el pedido." : error.getMessage()));
            }
        });
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
