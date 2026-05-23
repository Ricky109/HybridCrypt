package com.example.hybridcrypt;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.hybridcrypt.storage.KeyStorage;
import com.example.hybridcrypt.ui.SettingsActivity;
import java.util.List;

/**
 * Pantalla principal de HybridCrypt.
 *
 * No es la app en sí — el teclado funciona en segundo plano en cualquier
 * otra app. Esta pantalla sirve como panel de control para que el usuario
 * configure el sistema antes de usarlo por primera vez.
 *
 * Muestra el estado actual y guía al usuario paso a paso:
 *   Paso 1 → Generar claves RSA
 *   Paso 2 → Activar el teclado en el sistema
 *   Paso 3 → Intercambiar claves con un contacto
 *   Paso 4 → Listo para cifrar
 */
public class MainActivity extends AppCompatActivity {

    private KeyStorage keyStorage;
    private TextView   tvPaso1, tvPaso2, tvPaso3, tvPaso4;
    private Button     btnIrAjustes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        keyStorage = new KeyStorage(this);

        tvPaso1     = findViewById(R.id.tv_paso1);
        tvPaso2     = findViewById(R.id.tv_paso2);
        tvPaso3     = findViewById(R.id.tv_paso3);
        tvPaso4     = findViewById(R.id.tv_paso4);
        btnIrAjustes = findViewById(R.id.btn_ir_ajustes);

        btnIrAjustes.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Actualizar el estado cada vez que el usuario vuelve a esta pantalla
        actualizarEstado();
    }

    private void actualizarEstado() {
        boolean tieneRSA     = keyStorage.tieneClaveRSA();
        boolean tecladoActivo = tecladoEstaActivo();

        // Paso 1: claves RSA
        tvPaso1.setText(tieneRSA
                ? "✓  Par de claves RSA generado"
                : "✗  Genera tu par de claves RSA en Ajustes");
        tvPaso1.setTextColor(tieneRSA ? 0xFF4CAF50 : 0xFFE94560);

        // Paso 2: teclado activo
        tvPaso2.setText(tecladoActivo
                ? "✓  Teclado HybridCrypt activo"
                : "✗  Activa el teclado en Ajustes del sistema");
        tvPaso2.setTextColor(tecladoActivo ? 0xFF4CAF50 : 0xFFE94560);

        // Paso 3: contactos
        tvPaso3.setText("→  Escanea el QR de tu contacto en Ajustes");
        tvPaso3.setTextColor(0xFFAAAAAA);

        // Paso 4: listo
        boolean listo = tieneRSA && tecladoActivo;
        tvPaso4.setText(listo
                ? "✓  Listo — abre WhatsApp, selecciona texto y usa 🔒"
                : "⏳  Completa los pasos anteriores");
        tvPaso4.setTextColor(listo ? 0xFF4CAF50 : 0xFF888888);

        btnIrAjustes.setText(tieneRSA ? "Abrir Ajustes" : "Configurar ahora");
    }

    /**
     * Comprueba si HybridCrypt Keyboard está habilitado en el sistema.
     * Un teclado habilitado aparece en la lista pero puede no ser el activo —
     * el usuario también debe seleccionarlo manualmente.
     */
    private boolean tecladoEstaActivo() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        List<InputMethodInfo> metodos = imm.getEnabledInputMethodList();
        for (InputMethodInfo info : metodos) {
            if (info.getPackageName().equals(getPackageName())) return true;
        }
        return false;
    }
}