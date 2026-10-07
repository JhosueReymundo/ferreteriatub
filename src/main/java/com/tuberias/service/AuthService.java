package com.tuberias.service;

import com.tuberias.model.Roles;
import com.tuberias.model.Usuario;
import com.tuberias.repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reglas de seguridad de usuarios: configuración inicial, login,
 * cambio de contraseña y recuperación con código.
 */
public class AuthService {

    public static final int MAX_INTENTOS = 5;
    public static final Duration BLOQUEO = Duration.ofMinutes(5);
    public static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD_BYTES = 72; // límite de BCrypt

    private static final Pattern FORMATO_USERNAME = Pattern.compile("^[a-z0-9._-]{3,50}$");
    private static final String MSG_CREDENCIALES = "Usuario o contraseña incorrectos.";
    private static final String MSG_RECUPERACION = "Usuario o código de recuperación incorrectos.";

    private final UsuarioRepository repo;
    private final PasswordHasher hasher;
    private final Clock clock;
    private String hashFalso; // para que un usuario inexistente tarde lo mismo que uno real

    public AuthService(UsuarioRepository repo, PasswordHasher hasher, Clock clock) {
        this.repo = repo;
        this.hasher = hasher;
        this.clock = clock;
    }

    // ------------------------------------------------- configuración inicial

    public boolean necesitaConfiguracionInicial() {
        return !repo.hayUsuarios();
    }

    /**
     * Crea el administrador en el primer arranque.
     *
     * @return el código de recuperación EN CLARO; es la única vez que se puede ver.
     */
    public String configuracionInicial(String username, String nombreCompleto,
                                       String password, String confirmacion) {
        if (repo.hayUsuarios()) {
            throw new AuthException("El sistema ya fue configurado.");
        }
        String usuario = normalizarUsername(username);
        if (!FORMATO_USERNAME.matcher(usuario).matches()) {
            throw new AuthException("El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.");
        }
        String nombre = nombreCompleto == null ? "" : nombreCompleto.trim();
        if (nombre.isEmpty()) {
            throw new AuthException("El nombre es obligatorio.");
        }
        if (nombre.length() > 120) {
            throw new AuthException("El nombre es demasiado largo (máximo 120 caracteres).");
        }
        validarPasswordNueva(password, confirmacion, usuario);

        String codigo = CodigoRecuperacion.generar();
        repo.crear(usuario, nombre, Roles.ADMIN,
                hasher.hash(password),
                hasher.hash(CodigoRecuperacion.normalizar(codigo)));
        return codigo;
    }

    // ----------------------------------------------------------------- login

    public Usuario login(String username, String password) {
        String usuario = normalizarUsername(username);
        Optional<Usuario> encontrado = usuario.isEmpty() ? Optional.empty() : repo.buscarPorUsername(usuario);

        if (encontrado.isEmpty() || !encontrado.get().activo()) {
            hasher.verificar(password == null ? "" : password, hashFalso());
            throw new AuthException(MSG_CREDENCIALES);
        }
        Usuario usr = encontrado.get();
        exigirNoBloqueado(usr);

        if (!hasher.verificar(password, usr.passwordHash())) {
            boolean quedoBloqueado = registrarFallo(usr);
            throw new AuthException(quedoBloqueado
                    ? MSG_CREDENCIALES + " Demasiados intentos: cuenta bloqueada por "
                      + BLOQUEO.toMinutes() + " minutos."
                    : MSG_CREDENCIALES);
        }
        repo.registrarAcceso(usr.id());
        return usr;
    }

    // ------------------------------------------------- cambio de contraseña

    public void cambiarPassword(long usuarioId, String actual, String nueva, String confirmacion) {
        Usuario usr = repo.buscarPorId(usuarioId)
                .orElseThrow(() -> new AuthException("Usuario no encontrado."));
        exigirNoBloqueado(usr);

        if (!hasher.verificar(actual, usr.passwordHash())) {
            boolean quedoBloqueado = registrarFallo(usr);
            throw new AuthException(quedoBloqueado
                    ? "La contraseña actual es incorrecta. Cuenta bloqueada por " + BLOQUEO.toMinutes() + " minutos."
                    : "La contraseña actual es incorrecta.");
        }
        validarPasswordNueva(nueva, confirmacion, usr.username());
        if (hasher.verificar(nueva, usr.passwordHash())) {
            throw new AuthException("La nueva contraseña debe ser distinta de la actual.");
        }
        repo.actualizarPassword(usr.id(), hasher.hash(nueva));
    }

    // ---------------------------------------------- recuperación con código

    /**
     * Restablece la contraseña con el código de recuperación.
     *
     * @return el NUEVO código de recuperación en claro (el anterior queda inválido).
     */
    public String recuperarPassword(String username, String codigo, String nueva, String confirmacion) {
        String usuario = normalizarUsername(username);
        validarPasswordNueva(nueva, confirmacion, usuario);

        Optional<Usuario> encontrado = usuario.isEmpty() ? Optional.empty() : repo.buscarPorUsername(usuario);
        if (encontrado.isEmpty() || !encontrado.get().activo()
                || encontrado.get().codigoRecuperacionHash() == null) {
            hasher.verificar(CodigoRecuperacion.normalizar(codigo), hashFalso());
            throw new AuthException(MSG_RECUPERACION);
        }
        Usuario usr = encontrado.get();
        exigirNoBloqueado(usr);

        if (!hasher.verificar(CodigoRecuperacion.normalizar(codigo), usr.codigoRecuperacionHash())) {
            boolean quedoBloqueado = registrarFallo(usr);
            throw new AuthException(quedoBloqueado
                    ? MSG_RECUPERACION + " Demasiados intentos: bloqueado por " + BLOQUEO.toMinutes() + " minutos."
                    : MSG_RECUPERACION);
        }

        String nuevoCodigo = CodigoRecuperacion.generar();
        repo.actualizarPasswordYCodigo(usr.id(),
                hasher.hash(nueva),
                hasher.hash(CodigoRecuperacion.normalizar(nuevoCodigo)));
        return nuevoCodigo;
    }

    // --------------------------------------------------------------- helpers

    private void exigirNoBloqueado(Usuario usr) {
        Instant ahora = clock.instant();
        if (usr.estaBloqueado(ahora)) {
            long segundos = Duration.between(ahora, usr.bloqueadoHasta()).getSeconds();
            long minutos = Math.max(1, (segundos + 59) / 60);
            throw new AuthException("Cuenta bloqueada temporalmente. Intenta de nuevo en "
                    + minutos + (minutos == 1 ? " minuto." : " minutos."));
        }
    }

    /** Suma un intento fallido; al llegar al máximo bloquea la cuenta. Devuelve true si quedó bloqueada. */
    private boolean registrarFallo(Usuario usr) {
        int intentos = usr.intentosFallidos() + 1;
        if (intentos >= MAX_INTENTOS) {
            repo.registrarFallo(usr.id(), 0, clock.instant().plus(BLOQUEO));
            return true;
        }
        repo.registrarFallo(usr.id(), intentos, null);
        return false;
    }

    private void validarPasswordNueva(String password, String confirmacion, String username) {
        if (password == null || password.isEmpty()) {
            throw new AuthException("La contraseña es obligatoria.");
        }
        if (password.length() < MIN_PASSWORD) {
            throw new AuthException("La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AuthException("La contraseña es demasiado larga.");
        }
        if (password.trim().toLowerCase(Locale.ROOT).equals(username)) {
            throw new AuthException("La contraseña no puede ser igual al usuario.");
        }
        if (!password.equals(confirmacion)) {
            throw new AuthException("Las contraseñas no coinciden.");
        }
    }

    private static String normalizarUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String hashFalso() {
        if (hashFalso == null) {
            hashFalso = hasher.hash("contrasena-falsa");
        }
        return hashFalso;
    }
}
