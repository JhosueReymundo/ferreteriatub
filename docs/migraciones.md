# Migraciones (Flyway, `src/main/resources/db/migration`)

| Versión | Módulo |
|---|---|
| V1 | Usuarios y roles |
| V2 | Catálogos, productos e inventario (kardex) |
| V3 | Proveedores |
| V4 | Compras |
| V5 | Clientes y ventas |
| V6 | Caja |

El dashboard no necesita migración: son consultas de solo lectura (`JdbcDashboardRepository`).
Se calculan con parámetros y no con vistas para que el "día" lo decida la zona horaria del
negocio y no la de PostgreSQL (una venta a las 8 pm en Lima ya es "mañana" en UTC).

Regla: una migración ya ejecutada nunca se edita; los cambios van en un archivo nuevo (V7, V8…).
