package com.tuberias.repository;

import com.tuberias.model.Formato;
import com.tuberias.service.NegocioException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Operaciones de caja que se ejecutan DENTRO de la transacción de quien las llama
 * (venta, compra o movimiento manual), para que el dinero y el documento se guarden juntos.
 *
 * Toda escritura bloquea primero la fila de la caja abierta (FOR UPDATE): así dos operaciones
 * simultáneas no pueden dejar el efectivo en negativo y nadie registra algo mientras se cierra.
 */
final class CajaSql {

    private CajaSql() {}

    /** Id de la caja abierta (bloqueada hasta el fin de la transacción), o null si no hay. */
    static Long cajaAbiertaBloqueada(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id FROM caja_sesion WHERE estado = 'ABIERTA' FOR UPDATE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    /** Efectivo que debería haber en el cajón ahora mismo. */
    static BigDecimal efectivoEnCaja(Connection con, long cajaId) throws SQLException {
        String sql = """
                SELECT s.monto_apertura
                       + coalesce(sum(CASE WHEN m.tipo = 'INGRESO' THEN m.monto ELSE -m.monto END), 0)
                  FROM caja_sesion s
                  LEFT JOIN movimiento_caja m
                         ON m.caja_sesion_id = s.id AND m.metodo_pago = 'EFECTIVO'
                 WHERE s.id = ?
                 GROUP BY s.id, s.monto_apertura
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, cajaId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /**
     * Registra un movimiento en la caja abierta.
     *
     * @param mensajeSinCaja texto para el usuario si no hay caja abierta
     * @throws NegocioException si no hay caja abierta o si un egreso en efectivo supera lo que hay
     */
    static void registrar(Connection con, String mensajeSinCaja, String tipo, String origen,
                          String concepto, BigDecimal monto, String metodoPago,
                          Long ventaId, Long compraId, long usuarioId) throws SQLException {
        Long cajaId = cajaAbiertaBloqueada(con);
        if (cajaId == null) {
            throw new NegocioException(mensajeSinCaja);
        }
        if ("EGRESO".equals(tipo) && "EFECTIVO".equals(metodoPago)) {
            BigDecimal hay = efectivoEnCaja(con, cajaId);
            if (monto.compareTo(hay) > 0) {
                throw new NegocioException("Efectivo insuficiente en caja: hay " + Formato.dinero(hay)
                        + " y se necesitan " + Formato.dinero(monto) + ".");
            }
        }
        String sql = """
                INSERT INTO movimiento_caja
                       (caja_sesion_id, tipo, origen, concepto, monto, metodo_pago, venta_id, compra_id, usuario_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, cajaId);
            ps.setString(2, tipo);
            ps.setString(3, origen);
            ps.setString(4, concepto);
            ps.setBigDecimal(5, monto);
            ps.setString(6, metodoPago);
            ps.setObject(7, ventaId, Types.BIGINT);
            ps.setObject(8, compraId, Types.BIGINT);
            ps.setLong(9, usuarioId);
            ps.executeUpdate();
        }
    }

    /**
     * Si la venta/compra se registró en caja, devuelve el dinero con el movimiento contrario
     * (en la caja abierta HOY, aunque el original sea de un turno ya cerrado). Si nunca pasó
     * por caja (total 0, o anterior a este módulo) no hace nada.
     */
    static void revertirDeVenta(Connection con, long ventaId, long usuarioId) throws SQLException {
        revertir(con, "venta_id", "VENTA", "ANULACION_VENTA", "EGRESO",
                "Anulación de la venta #" + ventaId, ventaId, null, usuarioId);
    }

    static void revertirDeCompra(Connection con, long compraId, long usuarioId) throws SQLException {
        revertir(con, "compra_id", "COMPRA", "ANULACION_COMPRA", "INGRESO",
                "Anulación de la compra #" + compraId, null, compraId, usuarioId);
    }

    private static void revertir(Connection con, String columna, String origenOriginal, String origenReverso,
                                 String tipoReverso, String concepto, Long ventaId, Long compraId,
                                 long usuarioId) throws SQLException {
        BigDecimal monto;
        String metodo;
        // columna viene de constantes de este archivo, nunca de datos del usuario
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT monto, metodo_pago FROM movimiento_caja WHERE " + columna + " = ? AND origen = ?")) {
            ps.setLong(1, ventaId != null ? ventaId : compraId);
            ps.setString(2, origenOriginal);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                monto = rs.getBigDecimal(1);
                metodo = rs.getString(2);
            }
        }
        registrar(con, "Abre la caja para poder anular: el dinero se devuelve desde la caja actual.",
                tipoReverso, origenReverso, concepto, monto, metodo, ventaId, compraId, usuarioId);
    }
}
