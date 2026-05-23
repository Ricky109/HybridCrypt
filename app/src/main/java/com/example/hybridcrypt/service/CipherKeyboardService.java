package com.example.hybridcrypt.service;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import com.example.hybridcrypt.R;
import com.example.hybridcrypt.crypto.HybridCrypt;
import com.example.hybridcrypt.storage.KeyStorage;
import java.util.List;

public class CipherKeyboardService extends InputMethodService {

    private KeyStorage keyStorage;
    private String     contactoActivo = null; // null = ninguno seleccionado
    private boolean    mayusculas     = false;
    private TextView   tvSelectorContacto;

    @Override
    public void onCreate() {
        super.onCreate();
        keyStorage = new KeyStorage(this);
    }

    @Override
    public View onCreateInputView() {
        View teclado = getLayoutInflater().inflate(R.layout.keyboard_layout, null);
        tvSelectorContacto = teclado.findViewById(R.id.tv_selector_contacto);

        configurarSelectorContacto();
        configurarTeclas(teclado);
        configurarBotonesCifrado(teclado);
        actualizarSelectorUI();

        return teclado;
    }

    // ──────────────────────────────────────────────────────────
    //  SELECTOR DE CONTACTO
    // ──────────────────────────────────────────────────────────

    /**
     * Al tocar el selector se muestra un PopupMenu con todos los
     * contactos que tienen clave pública guardada.
     * Al seleccionar uno se convierte en el contacto activo.
     */
    private void configurarSelectorContacto() {
        tvSelectorContacto.setOnClickListener(v -> {
            List<String> contactos = keyStorage.obtenerContactos();

            if (contactos.isEmpty()) {
                mostrarToast("No hay contactos. Escanea un QR en Ajustes.");
                return;
            }

            PopupMenu menu = new PopupMenu(getApplicationContext(), tvSelectorContacto);

            for (int i = 0; i < contactos.size(); i++) {
                menu.getMenu().add(0, i, i, contactos.get(i));
            }

            menu.setOnMenuItemClickListener(item -> {
                contactoActivo = contactos.get(item.getItemId());
                actualizarSelectorUI();
                mostrarToast("Contacto activo: " + contactoActivo);
                return true;
            });

            menu.show();
        });
    }

    private void actualizarSelectorUI() {
        if (tvSelectorContacto == null) return;
        if (contactoActivo == null) {
            tvSelectorContacto.setText("👤 Sin contacto ▾");
            tvSelectorContacto.setTextColor(0xFFAAAAAA);
        } else {
            tvSelectorContacto.setText("👤 " + contactoActivo + " ▾");
            tvSelectorContacto.setTextColor(0xFF4CAF50);
        }
    }

    // ──────────────────────────────────────────────────────────
    //  TECLAS ALFANUMÉRICAS
    // ──────────────────────────────────────────────────────────

    private void configurarTeclas(View teclado) {
        int[] idsTeclas = {
                R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, R.id.key_5,
                R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9, R.id.key_0,
                R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
                R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
                R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
                R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l, R.id.key_anio,
                R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v, R.id.key_b,
                R.id.key_n, R.id.key_m, R.id.key_coma, R.id.key_punto
        };

        for (int id : idsTeclas) {
            Button btn = teclado.findViewById(id);
            if (btn == null) continue;
            btn.setOnClickListener(v -> {
                String texto = ((Button) v).getText().toString();
                escribirCaracter(mayusculas ? texto.toUpperCase() : texto.toLowerCase());
            });
        }

        teclado.findViewById(R.id.key_space).setOnClickListener(v ->
                getCurrentInputConnection().commitText(" ", 1));

        teclado.findViewById(R.id.key_back).setOnClickListener(v ->
                getCurrentInputConnection().deleteSurroundingText(1, 0));

        teclado.findViewById(R.id.key_enter).setOnClickListener(v ->
                getCurrentInputConnection().commitText("\n", 1));

        Button btnMayus = teclado.findViewById(R.id.key_mayus);
        btnMayus.setOnClickListener(v -> {
            mayusculas = !mayusculas;
            btnMayus.setAlpha(mayusculas ? 1.0f : 0.5f);
        });
    }

    // ──────────────────────────────────────────────────────────
    //  BOTONES CIFRAR / DESCIFRAR
    // ──────────────────────────────────────────────────────────

    private void configurarBotonesCifrado(View teclado) {
        teclado.findViewById(R.id.btn_cifrar).setOnClickListener(v -> cifrarTextoSeleccionado());
        teclado.findViewById(R.id.btn_descifrar).setOnClickListener(v -> descifrarTextoSeleccionado());
    }

    private void cifrarTextoSeleccionado() {
        if (contactoActivo == null) {
            mostrarToast("Toca 👤 para seleccionar un contacto primero");
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence seleccionado = ic.getSelectedText(0);
        if (seleccionado == null || seleccionado.length() == 0) {
            mostrarToast("Selecciona el texto que quieres cifrar");
            return;
        }

        byte[] claveAES = keyStorage.obtenerClaveAES(contactoActivo);
        if (claveAES == null) {
            iniciarIntercambioClaveAES(ic, seleccionado.toString());
            return;
        }

        try {
            String cifrado = HybridCrypt.cifrar(seleccionado.toString(), claveAES);
            ic.commitText(cifrado, 1);
            mostrarToast("Cifrado ✓");
        } catch (Exception e) {
            mostrarToast("Error al cifrar: " + e.getMessage());
        }
    }


    private void descifrarTextoSeleccionado() {
        if (contactoActivo == null) {
            mostrarToast("Toca 👤 para seleccionar un contacto primero");
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // Intentar obtener texto seleccionado en el campo activo
        CharSequence seleccionado = ic.getSelectedText(0);
        String texto = (seleccionado != null && seleccionado.length() > 0)
                ? seleccionado.toString().trim()
                : null;

        // Si no hay selección, leer del portapapeles
        if (texto == null || texto.isEmpty()) {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                mostrarToast("Copia el mensaje cifrado y presiona 🔓");
                return;
            }

            android.content.ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item == null || item.getText() == null) {
                mostrarToast("El portapapeles está vacío");
                return;
            }

            texto = item.getText().toString().trim();
            mostrarToast("Leyendo del portapapeles...");
        }

        // Procesar el texto obtenido
        if (HybridCrypt.esMensajeDeClave(texto)) {
            procesarMensajeDeClave(texto, ic);
            return;
        }

        byte[] claveAES = keyStorage.obtenerClaveAES(contactoActivo);
        if (claveAES == null) {
            mostrarToast("No hay sesión con " + contactoActivo + ". Intercambia claves primero.");
            return;
        }

        try {
            String descifrado = HybridCrypt.descifrar(texto, claveAES);
            // Escribir el resultado en el campo activo
            ic.commitText(descifrado, 1);
            mostrarToast("Descifrado ✓");
        } catch (SecurityException e) {
            mostrarToast("⚠ Mensaje alterado o clave incorrecta");
        } catch (Exception e) {
            mostrarToast("Error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    //  INTERCAMBIO DE CLAVE AES VÍA RSA
    // ──────────────────────────────────────────────────────────

    private void iniciarIntercambioClaveAES(InputConnection ic, String textoOriginal) {
        if (!keyStorage.tieneClavePublicaContacto(contactoActivo)) {
            mostrarToast("Escanea el QR de " + contactoActivo + " en Ajustes primero");
            return;
        }

        try {
            // 1. Generar y guardar la clave AES de sesión
            byte[] claveAES = HybridCrypt.generarClaveAES();
            keyStorage.guardarClaveAES(contactoActivo, claveAES);

            // 2. Cifrar la clave AES con RSA y enviarla
            String mensajeClave = HybridCrypt.cifrarClaveAES(
                    claveAES,
                    keyStorage.obtenerNContacto(contactoActivo),
                    keyStorage.obtenerEContacto(contactoActivo)
            );
            ic.commitText(mensajeClave, 1);

            // 3. Inmediatamente cifrar el mensaje original con la nueva clave AES
            //    y añadirlo en una nueva línea para que el receptor lo tenga
            String mensajeCifrado = HybridCrypt.cifrar(textoOriginal, claveAES);
            ic.commitText("\n" + mensajeCifrado, 1);

            mostrarToast("Sesión iniciada y mensaje cifrado enviados ✓");

        } catch (Exception e) {
            mostrarToast("Error al generar clave: " + e.getMessage());
        }
    }

    private void procesarMensajeDeClave(String mensajeClave, InputConnection ic) {
        if (!keyStorage.tieneClaveRSA()) {
            mostrarToast("Genera tu par RSA en Ajustes primero");
            return;
        }

        try {
            byte[] claveAES = HybridCrypt.descifrarClaveAES(
                    mensajeClave,
                    keyStorage.obtenerN(),
                    keyStorage.obtenerD()
            );

            keyStorage.guardarClaveAES(contactoActivo, claveAES);
            ic.commitText("[Sesión establecida con " + contactoActivo + " ✓]", 1);
            mostrarToast("Sesión establecida con " + contactoActivo);

        } catch (Exception e) {
            mostrarToast("Error al procesar clave: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    //  UTILIDADES
    // ──────────────────────────────────────────────────────────

    private void escribirCaracter(String caracter) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(caracter, 1);
    }

    private void mostrarToast(String mensaje) {
        Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
    }
}