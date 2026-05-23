package com.example.hybridcrypt.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.hybridcrypt.R;
import com.example.hybridcrypt.storage.KeyStorage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import androidx.activity.result.ActivityResultLauncher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla de ajustes de HybridCrypt.
 *
 * Permite al usuario:
 *   1. Generar su par de claves RSA (con un PIN)
 *   2. Ver su clave pública como QR para compartirla
 *   3. Escanear el QR de un contacto para guardar su clave pública
 *   4. Activar el teclado en los ajustes del sistema
 *   5. Ver el estado de la sesión actual
 *
 * La generación RSA tarda varios segundos, se ejecuta en hilo de fondo
 * para no bloquear la interfaz.
 */
public class SettingsActivity extends AppCompatActivity {

    private KeyStorage keyStorage;
    private ExecutorService executor;

    // Views
    private EditText    etPin;
    private EditText    etContacto;
    private ImageView   ivQrPropio;
    private TextView    tvEstado;
    private Button      btnGenerarClaves;
    private Button      btnMostrarQR;
    private Button      btnEscanearQR;
    private Button      btnActivarTeclado;

    // Lanzador del escáner de QR (ZXing)
    private ActivityResultLauncher<ScanOptions> scanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        keyStorage = new KeyStorage(this);
        executor   = Executors.newSingleThreadExecutor();

        inicializarVistas();
        configurarEscanerQR();
        actualizarEstado();
    }

    private void inicializarVistas() {
        etPin            = findViewById(R.id.et_pin);
        etContacto       = findViewById(R.id.et_contacto);
        ivQrPropio       = findViewById(R.id.iv_qr_propio);
        tvEstado         = findViewById(R.id.tv_estado_ajustes);
        btnGenerarClaves = findViewById(R.id.btn_generar_claves);
        btnMostrarQR     = findViewById(R.id.btn_mostrar_qr);
        btnEscanearQR    = findViewById(R.id.btn_escanear_qr);
        btnActivarTeclado= findViewById(R.id.btn_activar_teclado);

        btnGenerarClaves.setOnClickListener(v -> generarClavesRSA());
        btnMostrarQR.setOnClickListener(v -> mostrarQRPropio());
        btnEscanearQR.setOnClickListener(v -> escanearQRContacto());
        btnActivarTeclado.setOnClickListener(v -> abrirAjustesTeclado());
    }

    // ──────────────────────────────────────────────────────────
    //  GENERACIÓN DE CLAVES RSA
    // ──────────────────────────────────────────────────────────

    /**
     * Genera el par de claves RSA en un hilo de fondo.
     * La generación puede tardar 3-8 segundos en dispositivos normales.
     */
    private void generarClavesRSA() {
        String pin = etPin.getText().toString().trim();
        if (pin.length() < 4) {
            mostrarToast("El PIN debe tener al menos 4 dígitos");
            return;
        }

        btnGenerarClaves.setEnabled(false);
        btnGenerarClaves.setText("Generando claves...");
        tvEstado.setText("Generando par RSA-2048. Esto puede tardar varios segundos...");

        // Ejecutar en hilo de fondo para no bloquear la UI
        executor.execute(() -> {
            try {
                keyStorage.inicializarClaveRSA(pin);

                // Volver al hilo principal para actualizar la UI
                new Handler(Looper.getMainLooper()).post(() -> {
                    btnGenerarClaves.setEnabled(true);
                    btnGenerarClaves.setText("Regenerar claves RSA");
                    actualizarEstado();
                    mostrarToast("Claves RSA generadas correctamente ✓");
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    btnGenerarClaves.setEnabled(true);
                    btnGenerarClaves.setText("Generar claves RSA");
                    mostrarToast("Error al generar claves: " + e.getMessage());
                });
            }
        });
    }

    // ──────────────────────────────────────────────────────────
    //  QR DE CLAVE PÚBLICA PROPIA
    // ──────────────────────────────────────────────────────────

    /**
     * Genera y muestra el QR con la clave pública propia.
     * El contacto escanea este QR con su teclado para poder
     * cifrar mensajes destinados a nosotros.
     */
    private void mostrarQRPropio() {
        if (!keyStorage.tieneClaveRSA()) {
            mostrarToast("Primero genera tu par de claves RSA");
            return;
        }

        try {
            String clavePublica = keyStorage.obtenerClavePublicaParaQR();
            Bitmap qr = generarBitmapQR(clavePublica, 600);
            ivQrPropio.setImageBitmap(qr);
            ivQrPropio.setVisibility(android.view.View.VISIBLE);
            mostrarToast("Muestra este QR a tu contacto");

        } catch (Exception e) {
            mostrarToast("Error al generar QR: " + e.getMessage());
        }
    }

    /**
     * Genera un Bitmap con el código QR del contenido dado.
     * Usa la librería ZXing (añadir dependencia en build.gradle).
     */
    private Bitmap generarBitmapQR(String contenido, int tamanio) throws Exception {
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix matrix = writer.encode(contenido, BarcodeFormat.QR_CODE, tamanio, tamanio);

        Bitmap bitmap = Bitmap.createBitmap(tamanio, tamanio, Bitmap.Config.RGB_565);
        for (int x = 0; x < tamanio; x++) {
            for (int y = 0; y < tamanio; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bitmap;
    }

    // ──────────────────────────────────────────────────────────
    //  ESCANEO DE QR DE CONTACTO
    // ──────────────────────────────────────────────────────────

    private void configurarEscanerQR() {
        scanLauncher = registerForActivityResult(new ScanContract(), resultado -> {
            if (resultado.getContents() == null) return;

            String contacto = etContacto.getText().toString().trim();
            if (contacto.isEmpty()) {
                mostrarToast("Escribe el nombre del contacto primero");
                return;
            }

            try {
                keyStorage.guardarClavePublicaContacto(contacto, resultado.getContents());
                actualizarEstado();
                mostrarToast("Clave pública de " + contacto + " guardada ✓");
            } catch (Exception e) {
                mostrarToast("QR inválido o corrompido");
            }
        });
    }

    private void escanearQRContacto() {
        String contacto = etContacto.getText().toString().trim();
        if (contacto.isEmpty()) {
            mostrarToast("Escribe el nombre del contacto antes de escanear");
            return;
        }

        ScanOptions opciones = new ScanOptions();
        opciones.setPrompt("Escanea el QR de " + contacto);
        opciones.setBeepEnabled(false);
        opciones.setOrientationLocked(true);
        scanLauncher.launch(opciones);
    }

    // ──────────────────────────────────────────────────────────
    //  ACTIVACIÓN DEL TECLADO EN EL SISTEMA
    // ──────────────────────────────────────────────────────────

    /**
     * Abre los ajustes del sistema donde el usuario puede habilitar
     * HybridCrypt como teclado. Android requiere este paso manual
     * por razones de seguridad (los teclados tienen acceso a todo
     * lo que el usuario escribe).
     */
    private void abrirAjustesTeclado() {
        startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        mostrarToast("Activa 'HybridCrypt Keyboard' en la lista");
    }

    // ──────────────────────────────────────────────────────────
    //  ESTADO
    // ──────────────────────────────────────────────────────────

    private void actualizarEstado() {
        StringBuilder sb = new StringBuilder();
        sb.append(keyStorage.tieneClaveRSA()
                ? "✓ Par RSA generado\n"
                : "✗ Sin par RSA — genera tus claves\n");

        String contacto = etContacto.getText().toString().trim();
        if (!contacto.isEmpty()) {
            sb.append(keyStorage.tieneClavePublicaContacto(contacto)
                    ? "✓ Clave pública de " + contacto + " guardada\n"
                    : "✗ Sin clave pública de " + contacto + "\n");
            sb.append(keyStorage.tieneClaveAES(contacto)
                    ? "✓ Sesión activa con " + contacto
                    : "✗ Sin sesión con " + contacto);
        }

        tvEstado.setText(sb.toString());
    }

    // ──────────────────────────────────────────────────────────
    //  UTILIDADES
    // ──────────────────────────────────────────────────────────

    private void mostrarToast(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
