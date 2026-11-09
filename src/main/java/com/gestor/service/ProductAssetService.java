package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.repository.AuditRepository;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Codes and normalized photos for rows in the configured sales catalog. */
public class ProductAssetService {
    private static final int MAX_UPLOAD_BYTES = 4 * 1024 * 1024;
    private static final int MAX_STORED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_SIDE = 5_000;
    private static final long MAX_IMAGE_PIXELS = 12_000_000;
    private static final int OUTPUT_SIDE = 1_200;
    private final AuditRepository audit = new AuditRepository();

    public record ProductAsset(long rowId, String label, String barcode, boolean hasPhoto) {
        public long getRowId() { return rowId; }
        public String getLabel() { return label; }
        public String getBarcode() { return barcode; }
        public boolean getHasPhoto() { return hasPhoto; }
    }

    public record AssetMeta(String barcode, boolean hasPhoto) {
        public String getBarcode() { return barcode; }
        public boolean getHasPhoto() { return hasPhoto; }
    }

    public record Photo(byte[] bytes, String mimeType, Instant updatedAt) {
        public byte[] getBytes() { return bytes; }
        public String getMimeType() { return mimeType; }
        public Instant getUpdatedAt() { return updatedAt; }
    }

    private record SalesSource(long moduleId, long displayColumnId) {}
    private record ExistingAsset(String barcode, boolean hasPhoto) {}

    public boolean canManage(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return isManager(connection, businessId, userId);
        }
    }

    /** Lists only the currently configured sales catalog, without fetching photo blobs. */
    public List<ProductAsset> list(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            requireManager(connection, businessId, userId);
            SalesSource source = currentSource(connection, businessId, false);
            if (source == null) return List.of();
            String sql = """
                    SELECT r.id,cv.value_text AS label,a.barcode,
                           a.image_data IS NOT NULL AS has_photo
                    FROM module_rows r
                    LEFT JOIN cell_values cv ON cv.row_id=r.id AND cv.column_id=? AND cv.business_id=r.business_id
                    LEFT JOIN product_assets a ON a.row_id=r.id AND a.business_id=r.business_id AND a.module_id=r.module_id
                    WHERE r.business_id=? AND r.module_id=? ORDER BY r.id DESC
                    """;
            List<ProductAsset> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, source.displayColumnId());
                statement.setLong(2, businessId);
                statement.setLong(3, source.moduleId());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        long rowId = rows.getLong("id");
                        String label = rows.getString("label");
                        result.add(new ProductAsset(rowId, label == null || label.isBlank() ? "#" + rowId : label,
                                rows.getString("barcode"), rows.getBoolean("has_photo")));
                    }
                }
            }
            return result;
        }
    }

    /** Lightweight metadata for catalog/search; VIEW of the given table is required. */
    public Map<Long, AssetMeta> listMetadata(long businessId, long userId, long moduleId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            requireVisibleModule(connection, businessId, userId, moduleId);
            String sql = """
                    SELECT a.row_id,a.barcode,a.image_data IS NOT NULL AS has_photo
                    FROM product_assets a JOIN module_rows r
                      ON r.id=a.row_id AND r.business_id=a.business_id AND r.module_id=a.module_id
                    WHERE a.business_id=? AND a.module_id=?
                    """;
            Map<Long, AssetMeta> result = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, businessId);
                statement.setLong(2, moduleId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.put(rows.getLong("row_id"),
                            new AssetMeta(rows.getString("barcode"), rows.getBoolean("has_photo")));
                }
            }
            return Collections.unmodifiableMap(result);
        }
    }

    /** A blank code removes the assigned code. Codes are case-insensitive within a catalog. */
    public void saveCode(long businessId, long userId, long rowId, String code) throws SQLException {
        String normalized = normalizeCode(code);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                long moduleId = requireCatalogRow(connection, businessId, rowId);
                ExistingAsset existing = existingAsset(connection, businessId, moduleId, rowId);
                if (existing == null && normalized != null) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO product_assets (business_id,module_id,row_id,barcode,updated_by)
                            VALUES (?,?,?,?,?)
                            """)) {
                        statement.setLong(1, businessId);
                        statement.setLong(2, moduleId);
                        statement.setLong(3, rowId);
                        statement.setString(4, normalized);
                        statement.setLong(5, userId);
                        statement.executeUpdate();
                    }
                } else if (existing != null) {
                    if (normalized == null && !existing.hasPhoto()) {
                        deleteAsset(connection, businessId, moduleId, rowId);
                    } else {
                        try (PreparedStatement statement = connection.prepareStatement("""
                                UPDATE product_assets SET barcode=?,updated_by=?
                                WHERE business_id=? AND module_id=? AND row_id=?
                                """)) {
                            statement.setString(1, normalized);
                            statement.setLong(2, userId);
                            statement.setLong(3, businessId);
                            statement.setLong(4, moduleId);
                            statement.setLong(5, rowId);
                            statement.executeUpdate();
                        }
                    }
                }
                if ((existing == null ? null : existing.barcode()) != null || normalized != null) {
                    audit.log(connection, businessId, userId, moduleId, rowId, "PRODUCT_CODE_UPDATED",
                            "ROW", rowId, "Código de producto actualizado", existing == null ? null : existing.barcode(), normalized);
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                if (error instanceof SQLException sql && sql.getErrorCode() == 1062) {
                    throw new IllegalArgumentException("El código ya está asignado a otro producto.", error);
                }
                throw error;
            }
        }
    }

    public void savePhoto(long businessId, long userId, long rowId, InputStream data)
            throws SQLException, IOException {
        byte[] jpeg = normalizePhoto(data);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                long moduleId = requireCatalogRow(connection, businessId, rowId);
                ExistingAsset existing = existingAsset(connection, businessId, moduleId, rowId);
                if (existing == null) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO product_assets
                              (business_id,module_id,row_id,image_mime,image_data,image_updated_at,updated_by)
                            VALUES (?,?,?,'image/jpeg',?,CURRENT_TIMESTAMP,?)
                            """)) {
                        statement.setLong(1, businessId);
                        statement.setLong(2, moduleId);
                        statement.setLong(3, rowId);
                        statement.setBytes(4, jpeg);
                        statement.setLong(5, userId);
                        statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE product_assets SET image_mime='image/jpeg',image_data=?,
                              image_updated_at=CURRENT_TIMESTAMP,updated_by=?
                            WHERE business_id=? AND module_id=? AND row_id=?
                            """)) {
                        statement.setBytes(1, jpeg);
                        statement.setLong(2, userId);
                        statement.setLong(3, businessId);
                        statement.setLong(4, moduleId);
                        statement.setLong(5, rowId);
                        statement.executeUpdate();
                    }
                }
                audit.log(connection, businessId, userId, moduleId, rowId, "PRODUCT_PHOTO_UPDATED",
                        "ROW", rowId, "Foto de producto actualizada", null, null);
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public void removePhoto(long businessId, long userId, long rowId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                long moduleId = requireCatalogRow(connection, businessId, rowId);
                ExistingAsset existing = existingAsset(connection, businessId, moduleId, rowId);
                if (existing != null && existing.hasPhoto()) {
                    if (existing.barcode() == null) {
                        deleteAsset(connection, businessId, moduleId, rowId);
                    } else {
                        try (PreparedStatement statement = connection.prepareStatement("""
                                UPDATE product_assets SET image_mime=NULL,image_data=NULL,
                                  image_updated_at=NULL,updated_by=?
                                WHERE business_id=? AND module_id=? AND row_id=?
                                """)) {
                            statement.setLong(1, userId);
                            statement.setLong(2, businessId);
                            statement.setLong(3, moduleId);
                            statement.setLong(4, rowId);
                            statement.executeUpdate();
                        }
                    }
                    audit.log(connection, businessId, userId, moduleId, rowId, "PRODUCT_PHOTO_REMOVED",
                            "ROW", rowId, "Foto de producto eliminada", null, null);
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Returns null for a missing/deleted row or absent photo. */
    public Photo photo(long businessId, long userId, long rowId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            String scopeSql = """
                    SELECT r.module_id FROM module_rows r JOIN custom_modules m
                      ON m.id=r.module_id AND m.business_id=r.business_id
                    WHERE r.id=? AND r.business_id=? AND m.module_type='TABLE'
                    """;
            long moduleId;
            try (PreparedStatement statement = connection.prepareStatement(scopeSql)) {
                statement.setLong(1, rowId);
                statement.setLong(2, businessId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return null;
                    moduleId = rows.getLong(1);
                }
            }
            requireVisibleModule(connection, businessId, userId, moduleId);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT a.image_data,a.image_mime,a.image_updated_at FROM product_assets a
                    JOIN module_rows r ON r.id=a.row_id AND r.business_id=a.business_id AND r.module_id=a.module_id
                    WHERE a.business_id=? AND a.module_id=? AND a.row_id=? AND a.image_data IS NOT NULL
                    """)) {
                statement.setLong(1, businessId);
                statement.setLong(2, moduleId);
                statement.setLong(3, rowId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return null;
                    Timestamp updated = rows.getTimestamp("image_updated_at");
                    return new Photo(rows.getBytes("image_data"), rows.getString("image_mime"),
                            updated == null ? null : updated.toInstant());
                }
            }
        }
    }

    private boolean isManager(Connection connection, long businessId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT role,active FROM users WHERE id=? AND business_id=?")) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean("active") &&
                        ("OWNER".equals(rows.getString("role")) || "MANAGER".equals(rows.getString("role")));
            }
        }
    }

    private void requireManager(Connection connection, long businessId, long userId) throws SQLException {
        if (!isManager(connection, businessId, userId))
            throw new SecurityException("No tienes permiso para administrar el catálogo de productos.");
    }

    private void requireVisibleModule(Connection connection, long businessId, long userId, long moduleId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM custom_modules WHERE id=? AND business_id=? AND module_type='TABLE'")) {
            statement.setLong(1, moduleId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("La tabla no pertenece al negocio.");
            }
        }
        String sql = """
                SELECT u.role,u.active,p.can_view FROM users u
                LEFT JOIN module_permissions p
                  ON p.user_id=u.id AND p.business_id=u.business_id AND p.module_id=?
                WHERE u.id=? AND u.business_id=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, moduleId);
            statement.setLong(2, userId);
            statement.setLong(3, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean("active") ||
                        !("OWNER".equals(rows.getString("role")) ||
                                "MANAGER".equals(rows.getString("role")) || rows.getBoolean("can_view"))) {
                    throw new SecurityException("No tienes permiso para ver esta tabla.");
                }
            }
        }
    }

    private SalesSource currentSource(Connection connection, long businessId, boolean lock) throws SQLException {
        String sql = """
                SELECT s.module_id,s.display_column_id FROM sale_settings s
                JOIN custom_modules m ON m.id=s.module_id AND m.business_id=s.business_id AND m.module_type='TABLE'
                WHERE s.business_id=?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new SalesSource(rows.getLong(1), rows.getLong(2)) : null;
            }
        }
    }

    private long requireCatalogRow(Connection connection, long businessId, long rowId) throws SQLException {
        SalesSource source = currentSource(connection, businessId, true);
        if (source == null) throw new IllegalArgumentException("Configura primero el catálogo de Ventas.");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM module_rows WHERE id=? AND business_id=? AND module_id=? FOR UPDATE")) {
            statement.setLong(1, rowId);
            statement.setLong(2, businessId);
            statement.setLong(3, source.moduleId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("El producto no pertenece al catálogo de Ventas.");
            }
        }
        return source.moduleId();
    }

    private ExistingAsset existingAsset(Connection connection, long businessId, long moduleId, long rowId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT barcode,image_data IS NOT NULL AS has_photo FROM product_assets
                WHERE business_id=? AND module_id=? AND row_id=? FOR UPDATE
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, moduleId);
            statement.setLong(3, rowId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new ExistingAsset(rows.getString("barcode"), rows.getBoolean("has_photo")) : null;
            }
        }
    }

    private void deleteAsset(Connection connection, long businessId, long moduleId, long rowId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM product_assets WHERE business_id=? AND module_id=? AND row_id=?")) {
            statement.setLong(1, businessId);
            statement.setLong(2, moduleId);
            statement.setLong(3, rowId);
            statement.executeUpdate();
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) return null;
        String trimmed = code.trim();
        if (trimmed.length() > 64) throw new IllegalArgumentException("El código admite máximo 64 caracteres.");
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '!' || c > '~')
                throw new IllegalArgumentException("El código debe contener caracteres ASCII sin espacios.");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private byte[] normalizePhoto(InputStream input) throws IOException {
        if (input == null) throw new IllegalArgumentException("Selecciona una imagen JPEG o PNG.");
        byte[] original = input.readNBytes(MAX_UPLOAD_BYTES + 1);
        if (original.length == 0 || original.length > MAX_UPLOAD_BYTES)
            throw new IllegalArgumentException("La foto debe medir entre 1 byte y 4 MB.");
        boolean jpeg = original.length >= 3 && (original[0] & 0xff) == 0xff &&
                (original[1] & 0xff) == 0xd8 && (original[2] & 0xff) == 0xff;
        boolean png = original.length >= 8 && (original[0] & 0xff) == 0x89 &&
                original[1] == 'P' && original[2] == 'N' && original[3] == 'G' &&
                original[4] == 13 && original[5] == 10 && original[6] == 26 && original[7] == 10;
        if (!jpeg && !png) throw new IllegalArgumentException("La foto debe ser JPEG o PNG.");

        BufferedImage source;
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            if (imageInput == null) throw new IllegalArgumentException("No se pudo leer la foto.");
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IllegalArgumentException("La foto no es válida.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(jpeg && format.equals("jpeg")) && !(png && format.equals("png")))
                    throw new IllegalArgumentException("La foto no coincide con su formato.");
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_SIDE || height > MAX_IMAGE_SIDE ||
                        (long) width * height > MAX_IMAGE_PIXELS)
                    throw new IllegalArgumentException("La foto tiene dimensiones demasiado grandes.");
                ImageReadParam params = reader.getDefaultReadParam();
                source = reader.read(0, params);
                if (source == null) throw new IllegalArgumentException("La foto no es válida.");
            } catch (IOException error) {
                throw new IllegalArgumentException("La foto no es válida.", error);
            } finally {
                reader.dispose();
            }
        }

        double scale = Math.min(1.0, (double) OUTPUT_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No hay codificador JPEG disponible.");
        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.82f);
            // New pixels and null metadata remove EXIF and other original metadata.
            writer.write(null, new IIOImage(normalized, null, null), params);
        } finally {
            writer.dispose();
        }
        byte[] result = output.toByteArray();
        if (result.length == 0 || result.length > MAX_STORED_BYTES)
            throw new IllegalArgumentException("La foto procesada es demasiado grande.");
        return result;
    }
}
