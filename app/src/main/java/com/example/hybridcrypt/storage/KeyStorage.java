package com.example.hybridcrypt.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.hybridcrypt.crypto.RSAManager;
import java.math.BigInteger;
import java.security.SecureRandom;
import android.util.Base64;

/**
 * Gestiona la persistencia de claves en el dispositivo usando SharedPreferences.
 *
 * Almacena:
 *   - Par de claves RSA propio (generado una sola vez)
 *   - Clave AES de sesión por contacto
 *   - Clave pública RSA de cada contacto (importada por QR)
 */
public class KeyStorage {

    private static final String PREFS_NOMBRE   = "hybridcrypt_keys";
    private static final String KEY_RSA_N      = "rsa_n";
    private static final String KEY_RSA_E      = "rsa_e";
    private static final String KEY_RSA_D      = "rsa_d";
    private static final String PREFIJO_AES    = "aes_";       // + nombre contacto
    private static final String PREFIJO_PUB_N  = "pub_n_";     // + nombre contacto
    private static final String PREFIJO_PUB_E  = "pub_e_";     // + nombre contacto

    private final SharedPreferences prefs;

    public KeyStorage(Context context) {
        // MODE_PRIVATE: solo esta app puede leer estas preferencias
        this.prefs = context.getSharedPreferences(PREFS_NOMBRE, Context.MODE_PRIVATE);
    }

    // ──────────────────────────────────────────────────────────
    //  PAR DE CLAVES RSA PROPIO
    // ──────────────────────────────────────────────────────────

    /**
     * Genera y guarda el par de claves RSA si aún no existe.
     * Se llama al primer arranque de la app.
     * La generación tarda unos segundos — llamar en un hilo de fondo.
     */
    public void inicializarClaveRSA(String pin) throws Exception {
        if (tieneClaveRSA()) return; // ya existe, no regenerar

        RSAManager.ParClaves par = RSAManager.generarParClaves(pin);

        prefs.edit()
                .putString(KEY_RSA_N, RSAManager.moduloAString(par.n))
                .putString(KEY_RSA_E, RSAManager.clavePrivadaAString(par.e))
                .putString(KEY_RSA_D, RSAManager.clavePrivadaAString(par.d))
                .apply();
    }

    public boolean tieneClaveRSA() {
        return prefs.contains(KEY_RSA_N) && prefs.contains(KEY_RSA_D);
    }

    public BigInteger obtenerN() {
        return RSAManager.stringAModulo(prefs.getString(KEY_RSA_N, ""));
    }

    public BigInteger obtenerE() {
        return RSAManager.stringAClavePrivada(prefs.getString(KEY_RSA_E, ""));
    }

    public BigInteger obtenerD() {
        return RSAManager.stringAClavePrivada(prefs.getString(KEY_RSA_D, ""));
    }

    /**
     * Devuelve la clave pública propia serializada para mostrar como QR.
     */
    public String obtenerClavePublicaParaQR() {
        BigInteger n = obtenerN();
        BigInteger e = obtenerE();
        return RSAManager.clavePublicaAString(n, e);
    }

    // ──────────────────────────────────────────────────────────
    //  CLAVE PÚBLICA DE CONTACTOS
    // ──────────────────────────────────────────────────────────

    /**
     * Guarda la clave pública de un contacto después de escanear su QR.
     */
    public void guardarClavePublicaContacto(String contacto, String clavePublicaBase64) {
        BigInteger[] parPublico = RSAManager.stringAClavePublica(clavePublicaBase64);
        prefs.edit()
                .putString(PREFIJO_PUB_N + contacto, RSAManager.moduloAString(parPublico[0]))
                .putString(PREFIJO_PUB_E + contacto, RSAManager.clavePrivadaAString(parPublico[1]))
                .apply();
    }

    public boolean tieneClavePublicaContacto(String contacto) {
        return prefs.contains(PREFIJO_PUB_N + contacto);
    }

    public BigInteger obtenerNContacto(String contacto) {
        return RSAManager.stringAModulo(prefs.getString(PREFIJO_PUB_N + contacto, ""));
    }

    public BigInteger obtenerEContacto(String contacto) {
        return RSAManager.stringAClavePrivada(prefs.getString(PREFIJO_PUB_E + contacto, ""));
    }

    // ──────────────────────────────────────────────────────────
    //  CLAVE AES DE SESIÓN POR CONTACTO
    // ──────────────────────────────────────────────────────────

    /**
     * Guarda la clave AES de sesión para un contacto específico.
     * Se llama después de que el intercambio RSA se completó exitosamente.
     */
    public void guardarClaveAES(String contacto, byte[] claveAES) {
        String encoded = Base64.encodeToString(claveAES, Base64.NO_WRAP);
        prefs.edit()
                .putString(PREFIJO_AES + contacto, encoded)
                .apply();
    }

    public boolean tieneClaveAES(String contacto) {
        return prefs.contains(PREFIJO_AES + contacto);
    }

    public byte[] obtenerClaveAES(String contacto) {
        String encoded = prefs.getString(PREFIJO_AES + contacto, null);
        if (encoded == null) return null;
        return Base64.decode(encoded, Base64.NO_WRAP);
    }

    /**
     * Elimina la clave AES de un contacto para forzar una nueva sesión.
     */
    public void eliminarClaveAES(String contacto) {
        prefs.edit().remove(PREFIJO_AES + contacto).apply();
    }
    /**
     * Devuelve la lista de nombres de contactos que tienen
     * clave pública guardada. Se usa para poblar el selector
     * de contacto en el teclado.
     */
    public java.util.List<String> obtenerContactos() {
        java.util.List<String> contactos = new java.util.ArrayList<>();
        java.util.Map<String, ?> todo = prefs.getAll();
        for (String key : todo.keySet()) {
            // Incluir contactos con clave pública RSA
            if (key.startsWith(PREFIJO_PUB_N)) {
                String nombre = key.substring(PREFIJO_PUB_N.length());
                if (!contactos.contains(nombre)) contactos.add(nombre);
            }
            // Incluir también contactos con solo clave AES (modo demo)
            if (key.startsWith(PREFIJO_AES)) {
                String nombre = key.substring(PREFIJO_AES.length());
                if (!contactos.contains(nombre)) contactos.add(nombre);
            }
        }
        return contactos;
    }

    /**
     * Elimina todos los datos almacenados.
     * Útil para un reset completo de la app.
     */
    public void limpiarTodo() {
        prefs.edit().clear().apply();
    }
}
