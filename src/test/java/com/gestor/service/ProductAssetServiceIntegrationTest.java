package com.gestor.service;

import com.gestor.config.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in tests against a local database migrated through 009; all fixtures are disposable businesses. */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class ProductAssetServiceIntegrationTest {
    private final ProductAssetService assets = new ProductAssetService();
    private final SalesService sales = new SalesService();
    private final List<Long> disposableBusinesses = new ArrayList<>();

    @Test
    void codesAreNormalizedUniqueAndScopedToTheCurrentCatalog() throws Exception {
        try {
            Fixture first = fixture();
            Fixture second = fixture();
            long manager = user(first.business(), "MANAGER");
            long employee = user(first.business(), "EMPLOYEE");

            assertTrue(assets.canManage(first.business(), first.owner()));
            assertTrue(assets.canManage(first.business(), manager));
            assertFalse(assets.canManage(first.business(), employee));
            assertFalse(assets.canManage(first.business(), second.owner()));
            assertThrows(SecurityException.class, () -> assets.list(first.business(), employee));
            assertThrows(SecurityException.class, () -> assets.listMetadata(first.business(), employee,
                    first.module()));

            assets.saveCode(first.business(), first.owner(), first.firstRow(), "  abC-123  ");
            assertEquals("ABC-123", assets.listMetadata(first.business(), first.owner(),
                    first.module()).get(first.firstRow()).getBarcode());
            assertEquals("ABC-123", assets.list(first.business(), first.owner()).stream()
                    .filter(asset -> asset.getRowId() == first.firstRow()).findFirst().orElseThrow().getBarcode());
            assertThrows(IllegalArgumentException.class, () -> assets.saveCode(first.business(),
                    first.owner(), first.secondRow(), "abc-123"));
            assertEquals(1, scalar("SELECT COUNT(*) FROM product_assets WHERE business_id=?",
                    first.business()));

            // The same code belongs to another business without colliding.
            assets.saveCode(second.business(), second.owner(), second.firstRow(), "ABC-123");
            assertEquals(1, scalar("SELECT COUNT(*) FROM product_assets WHERE business_id=?",
                    second.business()));
            assertThrows(SecurityException.class, () -> assets.saveCode(first.business(), employee,
                    first.secondRow(), "OTHER"));
            assertThrows(SecurityException.class, () -> assets.saveCode(first.business(),
                    second.owner(), first.secondRow(), "OTHER"));
            assertThrows(IllegalArgumentException.class, () -> assets.saveCode(second.business(),
                    second.owner(), first.firstRow(), "FOREIGN"));
            assertThrows(IllegalArgumentException.class, () -> assets.saveCode(first.business(),
                    first.owner(), first.secondRow(), "bad code"));
            assertThrows(IllegalArgumentException.class, () -> assets.saveCode(first.business(),
                    first.owner(), first.secondRow(), "ß"));
            assertThrows(IllegalArgumentException.class, () -> assets.saveCode(first.business(),
                    first.owner(), first.secondRow(), "X".repeat(65)));

            long secondCatalog = insert("INSERT INTO custom_modules(business_id,name,module_type) " +
                    "VALUES(?,?,'TABLE')", first.business(), "Otro catálogo");
            long otherLabel = column(first.business(), secondCatalog, "Producto", "TEXT", 0);
            long otherStock = column(first.business(), secondCatalog, "Existencias", "NUMBER", 1);
            long otherPrice = column(first.business(), secondCatalog, "Precio", "NUMBER", 2);
            long otherRow = row(first.business(), secondCatalog, otherLabel, otherStock,
                    otherPrice, "Otra agua");
            sales.configure(first.business(), first.owner(), secondCatalog,
                    otherLabel, otherStock, otherPrice);
            assets.saveCode(first.business(), first.owner(), otherRow, "ABC-123");
            assertEquals(1, assets.list(first.business(), first.owner()).size());
            assertEquals(otherRow, assets.list(first.business(), first.owner()).get(0).getRowId());
            assertEquals(2, scalar("SELECT COUNT(*) FROM product_assets WHERE business_id=?",
                    first.business()));
            sales.configure(first.business(), first.owner(), first.module(),
                    first.labelColumn(), first.stockColumn(), first.priceColumn());

            assets.saveCode(first.business(), manager, first.firstRow(), "");
            assertEquals(1, scalar("SELECT COUNT(*) FROM product_assets WHERE business_id=?",
                    first.business()));
            execute("UPDATE users SET active=FALSE WHERE id=? AND business_id=?",
                    manager, first.business());
            assertFalse(assets.canManage(first.business(), manager));
            assertThrows(SecurityException.class, () -> assets.saveCode(first.business(), manager,
                    first.secondRow(), "INACTIVE"));
        } finally {
            cleanup();
        }
    }

    @Test
    void photosAreReencodedProtectedAndRemovedWithTheirRows() throws Exception {
        try {
            Fixture first = fixture();
            Fixture second = fixture();
            long employee = user(first.business(), "EMPLOYEE");
            byte[] validPng = png(20, 10);

            assertThrows(SecurityException.class, () -> assets.photo(first.business(), employee,
                    first.firstRow()));
            execute("INSERT INTO module_permissions(business_id,user_id,module_id,can_view) " +
                    "VALUES(?,?,?,TRUE)", first.business(), employee, first.module());
            assertThrows(SecurityException.class, () -> assets.savePhoto(first.business(), employee,
                    first.firstRow(), new ByteArrayInputStream(validPng)));

            assets.savePhoto(first.business(), first.owner(), first.firstRow(),
                    new ByteArrayInputStream(validPng));
            ProductAssetService.Photo photo = assets.photo(first.business(), employee,
                    first.firstRow());
            assertNotNull(photo);
            assertEquals("image/jpeg", photo.getMimeType());
            assertNotNull(photo.getUpdatedAt());
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(photo.getBytes()));
            assertNotNull(decoded);
            assertEquals(20, decoded.getWidth());
            assertEquals(10, decoded.getHeight());
            assertTrue(assets.listMetadata(first.business(), employee,
                    first.module()).get(first.firstRow()).getHasPhoto());

            byte[] largerPng = png(1_400, 700);
            assets.savePhoto(first.business(), first.owner(), first.firstRow(),
                    new ByteArrayInputStream(largerPng));
            BufferedImage scaled = ImageIO.read(new ByteArrayInputStream(
                    assets.photo(first.business(), employee, first.firstRow()).getBytes()));
            assertEquals(1_200, scaled.getWidth());
            assertEquals(600, scaled.getHeight());

            assertNull(assets.photo(second.business(), second.owner(), first.firstRow()));
            assertThrows(IllegalArgumentException.class, () -> assets.savePhoto(first.business(),
                    first.owner(), first.secondRow(), new ByteArrayInputStream(new byte[]{1, 2, 3})));
            assertThrows(IllegalArgumentException.class, () -> assets.savePhoto(first.business(),
                    first.owner(), first.secondRow(), new ByteArrayInputStream(new byte[]{
                    (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3})));
            assertThrows(IllegalArgumentException.class, () -> assets.savePhoto(first.business(),
                    first.owner(), first.secondRow(), new ByteArrayInputStream(new byte[4 * 1024 * 1024 + 1])));
            byte[] oversizedPng = png(20_000, 1);
            assertThrows(IllegalArgumentException.class, () -> assets.savePhoto(first.business(),
                    first.owner(), first.secondRow(), new ByteArrayInputStream(oversizedPng)));
            assertEquals(1, scalar("SELECT COUNT(*) FROM product_assets WHERE business_id=?",
                    first.business()));

            assets.saveCode(first.business(), first.owner(), first.firstRow(), "TEST-1");
            ByteArrayOutputStream backup = new ByteArrayOutputStream();
            new DataTransferService().writeBusinessBackup(first.business(), backup);
            Map<String, byte[]> entries = new HashMap<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(backup.toByteArray()))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
            assertTrue(new String(entries.get("productos/codigos.csv"), StandardCharsets.UTF_8)
                    .contains("TEST-1"));
            assertNotNull(ImageIO.read(new ByteArrayInputStream(entries.get(
                    "productos/fotos/" + first.module() + "-" + first.firstRow() + ".jpg"))));
            assets.removePhoto(first.business(), first.owner(), first.firstRow());
            assertFalse(assets.listMetadata(first.business(), first.owner(),
                    first.module()).get(first.firstRow()).getHasPhoto());
            assertEquals("TEST-1", assets.listMetadata(first.business(), first.owner(),
                    first.module()).get(first.firstRow()).getBarcode());
            assets.savePhoto(first.business(), first.owner(), first.firstRow(),
                    new ByteArrayInputStream(validPng));
            execute("DELETE FROM module_rows WHERE id=? AND business_id=? AND module_id=?",
                    first.firstRow(), first.business(), first.module());
            assertEquals(0, scalar("SELECT COUNT(*) FROM product_assets WHERE business_id=?",
                    first.business()));
        } finally {
            cleanup();
        }
    }

    private record Fixture(long business, long owner, long module,
                           long labelColumn, long stockColumn, long priceColumn,
                           long firstRow, long secondRow) {}

    private Fixture fixture() throws Exception {
        long business = insert("INSERT INTO businesses(name) VALUES(?)",
                "[ASSET TEST] " + UUID.randomUUID());
        disposableBusinesses.add(business);
        long owner = user(business, "OWNER");
        long module = insert("INSERT INTO custom_modules(business_id,name,module_type) " +
                "VALUES(?,?,'TABLE')", business, "Productos de prueba");
        long label = column(business, module, "Producto", "TEXT", 0);
        long stock = column(business, module, "Existencias", "NUMBER", 1);
        long price = column(business, module, "Precio", "NUMBER", 2);
        long first = row(business, module, label, stock, price, "Agua");
        long second = row(business, module, label, stock, price, "Pan");
        sales.configure(business, owner, module, label, stock, price);
        return new Fixture(business, owner, module, label, stock, price, first, second);
    }

    private long user(long business, String role) throws Exception {
        return insert("INSERT INTO users(business_id,name,email,password_hash,role,active) " +
                        "VALUES(?,?,?,?,?,TRUE)", business, "Prueba", "asset-" + UUID.randomUUID() +
                "@test.invalid", "unused", role);
    }

    private long column(long business, long module, String name, String type, int position) throws Exception {
        return insert("INSERT INTO module_columns(module_id,business_id,name,data_type,position) " +
                "VALUES(?,?,?,?,?)", module, business, name, type, position);
    }

    private long row(long business, long module, long label, long stock, long price,
                     String name) throws Exception {
        long row = insert("INSERT INTO module_rows(module_id,business_id) VALUES(?,?)",
                module, business);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                row, label, business, name);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                row, stock, business, "10");
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                row, price, business, "5");
        return row;
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x880088cc);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        return bytes.toByteArray();
    }

    private long insert(String sql, Object... values) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private int scalar(String sql, Object... values) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws Exception {
        for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
    }

    private void cleanup() throws Exception {
        for (long business : disposableBusinesses) execute("DELETE FROM businesses WHERE id=?", business);
        disposableBusinesses.clear();
    }
}
