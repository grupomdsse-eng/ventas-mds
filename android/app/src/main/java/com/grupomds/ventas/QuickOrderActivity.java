package com.grupomds.ventas;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.media.MediaRecorder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Formulario nativo para crear un pedido desde el widget sin cargar la web. */
public class QuickOrderActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 6101;
    private static final int FILE_REQUEST = 6102;
    private static final int CAMERA_PERMISSION_REQUEST = 6103;
    private static final int AUDIO_PERMISSION_REQUEST = 6104;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<WidgetApi.Company> companyChoices = new ArrayList<>();
    private final List<WidgetApi.Client> clients = new ArrayList<>();
    private final List<WidgetApi.Client> filteredClients = new ArrayList<>();
    private final List<WidgetApi.Contact> preparers = new ArrayList<>();
    private AutoCompleteTextView clientInput;
    /* El Spinner anterior dejaba el primer elemento "Selecciona una empresa"
       como valor efectivo en algunos launchers. Un desplegable explícito evita
       esa ambigüedad y permite buscar si el usuario tiene muchas empresas. */
    private AutoCompleteTextView companyInput;
    private Spinner preparerInput;
    private TextView preparerLabel;
    private EditText notesInput;
    private TextView status;
    private ImageView photoPreview;
    private ProgressBar progress;
    private Button submit;
    private WidgetApi.OrderOptions options;
    private int selectedCompanyId;
    private int selectedClientId;
    private File attachment;
    private File cameraOutput;
    private File audioOutput;
    private MediaRecorder recorder;
    private Button audioButton;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_quick_order);
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            // Esta pantalla es nativa: el padding evita que su cabecera o el
            // botón de crear pedido queden detrás de barras del sistema.
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        companyInput = findViewById(R.id.quick_order_company);
        clientInput = findViewById(R.id.quick_order_client);
        // La empresa se selecciona siempre de la lista autorizada: así no es
        // posible conservar por error una empresa anterior tras editar texto.
        companyInput.setKeyListener(null);
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
        audioButton = findViewById(R.id.quick_order_audio);
        audioButton.setOnClickListener(view -> toggleAudioRecording());
        submit.setOnClickListener(view -> submitOrder());
        companyInput.setOnClickListener(view -> { if (companyInput.isEnabled()) companyInput.post(companyInput::showDropDown); });
        companyInput.setOnFocusChangeListener((view, focused) -> { if (focused && companyInput.isEnabled()) companyInput.post(companyInput::showDropDown); });
        companyInput.setOnItemClickListener((parent, view, position, id) -> {
            Object selected = parent.getItemAtPosition(position);
            if (selected instanceof WidgetApi.Company) {
                WidgetApi.Company company = (WidgetApi.Company) selected;
                selectedCompanyId = company.id;
                companyInput.setText(company.name, false);
                companyInput.setError(null);
                filterClients();
                status.setText(filteredClients.isEmpty()
                        ? "No hay clientes asociados a " + company.name + "."
                        : "Empresa " + company.name + " seleccionada. Ahora elige el cliente.");
            }
        });
        clientInput.setOnClickListener(view -> { if (clientInput.isEnabled()) clientInput.post(clientInput::showDropDown); });
        clientInput.setOnFocusChangeListener((view, focused) -> { if (focused && clientInput.isEnabled()) clientInput.post(clientInput::showDropDown); });
        clientInput.setOnItemClickListener((parent, view, position, id) -> {
            Object selected = parent.getItemAtPosition(position);
            selectedClientId = selected instanceof WidgetApi.Client ? ((WidgetApi.Client) selected).id : 0;
            if (selectedClientId > 0) {
                clientInput.setError(null);
                showClientAlerts(selectedClientId);
            }
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
        companyChoices.clear();
        companyChoices.addAll(result.companies);
        companyInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, companyChoices));
        companyInput.setText("", false);
        companyInput.setHint(result.companies.isEmpty() ? "No hay empresas disponibles" : "Selecciona una empresa");
        selectedCompanyId = 0;
        companyInput.setEnabled(!result.companies.isEmpty());
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
        clientInput.setText("", false);
        if (options == null || selectedCompanyId <= 0 || options.companies.isEmpty()) {
            clientInput.setAdapter(null);
            clientInput.setEnabled(false);
            clientInput.setHint("Primero selecciona una empresa");
            return;
        }
        for (WidgetApi.Client client : clients) if (client.belongsTo(selectedCompanyId)) filteredClients.add(client);
        clientInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, filteredClients));
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
            intent.setClipData(ClipData.newRawUri("MDS Ventas", uri));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (handlers.isEmpty()) {
                Toast.makeText(this, "No hay una aplicación de cámara disponible.", Toast.LENGTH_SHORT).show();
                return;
            }
            for (ResolveInfo handler : handlers) {
                grantUriPermission(handler.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            startActivityForResult(intent, CAMERA_REQUEST);
        } catch (IOException | SecurityException error) {
            Toast.makeText(this, "No se pudo preparar la cámara.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf", "audio/*"});
        startActivityForResult(intent, FILE_REQUEST);
    }

    private void toggleAudioRecording() {
        if (recorder != null) { stopAudioRecording(); return; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST);
            return;
        }
        startAudioRecording();
    }

    private void startAudioRecording() {
        try {
            audioOutput=File.createTempFile("pedido-audio-", ".m4a", getCacheDir());
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(audioOutput.getAbsolutePath());
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.prepare(); recorder.start();
            audioButton.setText("Detener grabación"); status.setText("Grabando audio… Pulsa Detener cuando termines.");
        } catch (Exception error) { releaseRecorder(); Toast.makeText(this,"No se pudo iniciar la grabación.",Toast.LENGTH_SHORT).show(); }
    }

    private void stopAudioRecording() {
        try { recorder.stop(); attachment=audioOutput; photoPreview.setVisibility(View.GONE); status.setText("Audio preparado para el pedido."); }
        catch (RuntimeException error) { if(audioOutput!=null) audioOutput.delete(); Toast.makeText(this,"La grabación fue demasiado corta.",Toast.LENGTH_SHORT).show(); }
        finally { releaseRecorder(); audioButton.setText("Grabar audio"); }
    }

    private void releaseRecorder() { if(recorder!=null){try{recorder.reset();}catch(Exception ignored){} recorder.release();recorder=null;} }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) launchCamera();
        else if (requestCode == CAMERA_PERMISSION_REQUEST) Toast.makeText(this, "Debes permitir el uso de la cámara.", Toast.LENGTH_SHORT).show();
        if (requestCode == AUDIO_PERMISSION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startAudioRecording();
        else if (requestCode == AUDIO_PERMISSION_REQUEST) Toast.makeText(this, "Debes permitir el uso del micrófono.", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST && cameraOutput != null) {
            // Se revoca el acceso concedido a la cámara externa al regresar.
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraOutput);
                revokeUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
        }
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
                String type=getContentResolver().getType(data.getData());
                if(type!=null&&type.startsWith("image/")){photoPreview.setImageURI(Uri.fromFile(attachment));photoPreview.setVisibility(View.VISIBLE);status.setText("Imagen preparada para el pedido.");}
                else {photoPreview.setVisibility(View.GONE);status.setText(type!=null&&type.startsWith("audio/")?"Audio preparado para el pedido.":"Documento preparado para el pedido.");}
            } catch (IOException error) { Toast.makeText(this, "No se pudo leer el archivo seleccionado.", Toast.LENGTH_SHORT).show(); }
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
        String text = clientInput.getText().toString().trim();
        if (selectedClientId > 0) {
            for (WidgetApi.Client client : filteredClients) {
                if (client.id == selectedClientId && (client.toString().equalsIgnoreCase(text) || client.name.equalsIgnoreCase(text))) return client;
            }
        }
        for (WidgetApi.Client client : filteredClients) if (client.toString().equalsIgnoreCase(text) || client.name.equalsIgnoreCase(text)) { selectedClientId = client.id; return client; }
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
        if (options.companies.isEmpty() || selectedCompanyId <= 0) { companyInput.setError("Selecciona una empresa."); companyInput.requestFocus(); companyInput.showDropDown(); return; }
        int companyId = selectedCompanyId;
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

    @Override protected void onDestroy() { releaseRecorder(); executor.shutdownNow(); super.onDestroy(); }
}
