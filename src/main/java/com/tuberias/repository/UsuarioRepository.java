package com.tuberias.repository;

import com.tuberias.model.Usuario;

import java.time.Instant;
import java.util.Optional;

public interface UsuarioRepository {

    boolean hayUsuarios();

    /** Busca ignorando mayúsculas/minúsculas. */
    Optional<Usuario> buscarPorUsername(String username);

    Optional<Usuario> buscarPorId(long id);

    /** Inserta el usuario y devuelve su id. */
    long crear(String username, String nombreCompleto, short rolId,
               String passwordHash, String codigoRecuperacionHash);

    /** Cambia la contraseña, quita la obligación de cambiarla y limpia el bloqueo. */
    void actualizarPassword(long id, String passwordHash);

    /** Restablecimiento por código: nueva contraseña y nuevo código, en un solo UPDATE. */
    void actualizarPasswordYCodigo(long id, String passwordHash, String codigoRecuperacionHash);

    /** Guarda los intentos fallidos y, si corresponde, hasta cuándo está bloqueado (null = no). */
    void registrarFallo(long id, int intentosFallidos, Instant bloqueadoHasta);

    /** Acceso correcto: fija ultimo_acceso y limpia intentos y bloqueo. */
    void registrarAcceso(long id);
}
