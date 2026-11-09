package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.model.ModuleRow;
import com.gestor.repository.AuditRepository;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.util.CsvUtil;
import com.gestor.util.DownloadUtil;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Importa y exporta los datos lógicos del negocio; nunca usa nombres de tablas aportados por el usuario. */
public class DataTransferService {
    public static final int MAX_IMPORT_ROWS = 20_000;
    public static final int MAX_COLUMNS = 250;
    public static final int MAX_CELL_LENGTH = 100_000;
    public static final int MAX_EXPORT_ROWS = 50_000;

    private final GridRepository grid = new GridRepository();
    private final ModuleRepository modules = new ModuleRepository();
    private final AuditRepository audit = new AuditRepository();

    public record ImportResult(int importedRows, int skippedBlankRows) {}

    private record TableSnapshot(CustomModule module, List<ModuleColumn> columns, List<ModuleRow> rows) {}

    public ImportResult importCsv(long moduleId, long businessId, long userId,
                                  InputStream input, String sourceName) throws IOException, SQLException {
        List<List<String>> records = CsvUtil.parse(new InputStreamReader(input, StandardCharsets.UTF_8));
        return importRecords(moduleId, businessId, userId, records, sourceName);
    }

    public ImportResult importXlsx(long moduleId, long businessId, long userId,
                                   InputStream input, String sourceName) throws IOException, SQLException {
        List<List<String>> records = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("El libro no contiene hojas.");
            Sheet sheet = workbook.getSheetAt(0);
            int nonEmptyRows = 0;
            for (Row row : sheet) {
                if (row == null) continue;
                short lastCell = row.getLastCellNum();
                if (lastCell < 0) continue;
                if (lastCell > MAX_COLUMNS) {
                    throw new IllegalArgumentException("La hoja supera el límite de " + MAX_COLUMNS + " columnas.");
                }
                List<String> values = new ArrayList<>(lastCell);
                boolean hasValue = false;
                for (int index = 0; index < lastCell; index++) {
                    String value = excelValue(row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                    if (value.length() > MAX_CELL_LENGTH) {
                        throw new IllegalArgumentException("Una celda del archivo es demasiado extensa.");
                    }
                    values.add(value);
                    hasValue |= !value.isBlank();
                }
                if (!hasValue && records.isEmpty()) continue;
                records.add(values);
                if (!isBlank(values) && ++nonEmptyRows > MAX_IMPORT_ROWS + 1) {
                    throw new IllegalArgumentException("El archivo supera el límite de " + MAX_IMPORT_ROWS + " registros.");
                }
            }
        } catch (EncryptedDocumentException e) {
            throw new IllegalArgumentException("No se pueden importar libros protegidos con contraseña.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("El archivo XLSX está dañado o no tiene un formato compatible.", e);
        }
        return importRecords(moduleId, businessId, userId, records, sourceName);
    }

    public void writeCsv(long moduleId, long businessId, OutputStream output) throws SQLException, IOException {
        writeCsv(load(moduleId, businessId), output, true);
    }

    public void writeXlsx(long moduleId, long businessId, OutputStream output) throws SQLException, IOException {
        TableSnapshot snapshot = load(moduleId, businessId);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(safeSheetName(snapshot.module().name()));
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            List<ModuleColumn> columns = exportColumns(snapshot.columns());
            int[] widths = new int[columns.size() + 1];
            Row header = sheet.createRow(0);
            setHeader(header, 0, "ID", headerStyle, widths);
            for (int index = 0; index < columns.size(); index++) {
                setHeader(header, index + 1, columns.get(index).name(), headerStyle, widths);
            }

            int rowIndex = 1;
            for (ModuleRow source : snapshot.rows()) {
                Row target = sheet.createRow(rowIndex++);
                setExcelCell(target.createCell(0), Long.toString(source.id()), "ID", dateStyle);
                widths[0] = Math.max(widths[0], Long.toString(source.id()).length());
                for (int index = 0; index < columns.size(); index++) {
                    ModuleColumn column = columns.get(index);
                    String value = source.value(column.id());
                    setExcelCell(target.createCell(index + 1), value, column.dataType(), dateStyle);
                    widths[index + 1] = Math.max(widths[index + 1], displayLength(value));
                }
            }
            sheet.createFreezePane(1, 1);
            if (!columns.isEmpty()) sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size()));
            for (int index = 0; index < widths.length; index++) {
                sheet.setColumnWidth(index, Math.min(60, Math.max(8, widths[index] + 2)) * 256);
            }
            workbook.write(output);
            workbook.dispose();
        }
    }

    public void writeBusinessBackup(long businessId, OutputStream output) throws SQLException, IOException {
        List<CustomModule> tables = modules.listTables(businessId);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output), StandardCharsets.UTF_8)) {
            writeReadme(zip);

            zip.putNextEntry(new ZipEntry("manifest.csv"));
            Writer manifest = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
            manifest.write('\uFEFF');
            CsvUtil.writeRow(manifest, List.of("ID del módulo", "Tabla", "Archivo", "Filas"));
            List<TableSnapshot> snapshots = new ArrayList<>();
            for (CustomModule table : tables) {
                TableSnapshot snapshot = load(table.id(), businessId);
                snapshots.add(snapshot);
                String entryName = tableEntryName(table);
                CsvUtil.writeRow(manifest, List.of(Long.toString(table.id()), table.name(), entryName,
                        Integer.toString(snapshot.rows().size())));
            }
            manifest.flush();
            zip.closeEntry();

            for (TableSnapshot snapshot : snapshots) {
                zip.putNextEntry(new ZipEntry(tableEntryName(snapshot.module())));
                writeCsv(snapshot, zip, true);
                zip.closeEntry();
            }
            writeSalesBackup(businessId, zip);
            zip.finish();
        }
    }

    private void writeSalesBackup(long businessId, ZipOutputStream zip) throws SQLException, IOException {
        try (Connection connection = Database.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                zip.putNextEntry(new ZipEntry("ventas/ventas.csv"));
                Writer salesWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                salesWriter.write('\uFEFF');
                CsvUtil.writeRow(salesWriter, List.of("ID", "Fecha", "ID de tabla", "Tabla",
                        "ID de usuario", "Forma de pago", "Total", "Nota"));
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id,created_at,source_module_id,source_module_name,created_by,
                               payment_method,total_amount,note
                        FROM sales WHERE business_id=? ORDER BY id
                        """)) {
                    statement.setLong(1, businessId);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            CsvUtil.writeRow(salesWriter, List.of(
                                    Long.toString(rows.getLong("id")),
                                    rows.getTimestamp("created_at").toLocalDateTime().toString(),
                                    nullableId(rows, "source_module_id"),
                                    rows.getString("source_module_name"),
                                    nullableId(rows, "created_by"),
                                    rows.getString("payment_method"),
                                    rows.getBigDecimal("total_amount").toPlainString(),
                                    Objects.toString(rows.getString("note"), "")));
                        }
                    }
                }
                salesWriter.flush();
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry("ventas/productos.csv"));
                Writer itemsWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                itemsWriter.write('\uFEFF');
                CsvUtil.writeRow(itemsWriter, List.of("ID", "ID de venta", "ID de producto", "Producto",
                        "Cantidad", "Precio unitario", "Total", "Existencias antes", "Existencias después"));
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id,sale_id,product_row_id,product_label,quantity,unit_price,
                               line_total,stock_before,stock_after
                        FROM sale_items WHERE business_id=? ORDER BY sale_id,id
                        """)) {
                    statement.setLong(1, businessId);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            CsvUtil.writeRow(itemsWriter, List.of(
                                    Long.toString(rows.getLong("id")),
                                    Long.toString(rows.getLong("sale_id")),
                                    nullableId(rows, "product_row_id"),
                                    rows.getString("product_label"),
                                    rows.getBigDecimal("quantity").toPlainString(),
                                    rows.getBigDecimal("unit_price").toPlainString(),
                                    rows.getBigDecimal("line_total").toPlainString(),
                                    rows.getBigDecimal("stock_before").toPlainString(),
                                    rows.getBigDecimal("stock_after").toPlainString()));
                        }
                    }
                }
                itemsWriter.flush();
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry("ventas/devoluciones.csv"));
                Writer returnsWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                returnsWriter.write('\uFEFF');
                CsvUtil.writeRow(returnsWriter, List.of("ID", "ID de venta", "Fecha", "ID de usuario",
                        "Método de reembolso registrado", "Total", "Nota"));
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id,sale_id,created_at,created_by,refund_method,total_amount,note
                        FROM sale_returns WHERE business_id=? ORDER BY sale_id,id
                        """)) {
                    statement.setLong(1, businessId);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) CsvUtil.writeRow(returnsWriter, List.of(
                                Long.toString(rows.getLong("id")), Long.toString(rows.getLong("sale_id")),
                                rows.getTimestamp("created_at").toLocalDateTime().toString(),
                                nullableId(rows, "created_by"), rows.getString("refund_method"),
                                rows.getBigDecimal("total_amount").toPlainString(),
                                Objects.toString(rows.getString("note"), "")));
                    }
                }
                returnsWriter.flush();
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry("ventas/devolucion_productos.csv"));
                Writer returnedItemsWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                returnedItemsWriter.write('\uFEFF');
                CsvUtil.writeRow(returnedItemsWriter, List.of("ID", "ID de devolución", "ID de artículo de venta",
                        "ID de producto", "ID de columna de existencias", "Producto", "Cantidad",
                        "Precio unitario original", "Importe devuelto", "Existencias antes", "Existencias después"));
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id,return_id,sale_item_id,product_row_id,stock_column_id,product_label,
                               quantity,unit_price,line_total,stock_before,stock_after
                        FROM sale_return_items WHERE business_id=? ORDER BY return_id,id
                        """)) {
                    statement.setLong(1, businessId);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) CsvUtil.writeRow(returnedItemsWriter, List.of(
                                Long.toString(rows.getLong("id")), Long.toString(rows.getLong("return_id")),
                                Long.toString(rows.getLong("sale_item_id")),
                                Long.toString(rows.getLong("product_row_id")),
                                Long.toString(rows.getLong("stock_column_id")), rows.getString("product_label"),
                                rows.getBigDecimal("quantity").toPlainString(),
                                rows.getBigDecimal("unit_price").toPlainString(),
                                rows.getBigDecimal("line_total").toPlainString(),
                                rows.getBigDecimal("stock_before").toPlainString(),
                                rows.getBigDecimal("stock_after").toPlainString()));
                    }
                }
                returnedItemsWriter.flush();
                zip.closeEntry();
                writeCashBackup(businessId, connection, zip);
                writeProductAssetsBackup(businessId, connection, zip);
                connection.commit();
            } catch (SQLException | IOException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void writeCashBackup(long businessId, Connection connection, ZipOutputStream zip)
            throws SQLException, IOException {
        zip.putNextEntry(new ZipEntry("caja/turnos.csv"));
        Writer sessionsWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        sessionsWriter.write('\uFEFF');
        CsvUtil.writeRow(sessionsWriter, List.of("ID", "Apertura", "Cierre", "ID de quien abrió",
                "ID de quien cerró", "Fondo inicial", "Efectivo esperado", "Efectivo contado",
                "Diferencia", "Nota de apertura", "Nota de cierre"));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,opened_at,closed_at,opened_by,closed_by,opening_cash,
                       expected_cash,counted_cash,difference_cash,open_note,close_note
                FROM cash_sessions WHERE business_id=? ORDER BY id
                """)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    java.sql.Timestamp closedAt = rows.getTimestamp("closed_at");
                    CsvUtil.writeRow(sessionsWriter, List.of(
                            Long.toString(rows.getLong("id")),
                            rows.getTimestamp("opened_at").toLocalDateTime().toString(),
                            closedAt == null ? "" : closedAt.toLocalDateTime().toString(),
                            nullableId(rows, "opened_by"), nullableId(rows, "closed_by"),
                            rows.getBigDecimal("opening_cash").toPlainString(),
                            nullableDecimal(rows, "expected_cash"),
                            nullableDecimal(rows, "counted_cash"),
                            nullableDecimal(rows, "difference_cash"),
                            Objects.toString(rows.getString("open_note"), ""),
                            Objects.toString(rows.getString("close_note"), "")));
                }
            }
        }
        sessionsWriter.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("caja/movimientos.csv"));
        Writer movementsWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        movementsWriter.write('\uFEFF');
        CsvUtil.writeRow(movementsWriter, List.of("ID", "ID de turno", "Fecha", "ID de usuario",
                "Tipo", "Categoría", "Método", "Importe", "ID de venta",
                "ID de devolución", "Nota"));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,session_id,created_at,created_by,kind,category,payment_method,
                       amount,sale_id,sale_return_id,note
                FROM cash_movements WHERE business_id=? ORDER BY id
                """)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) CsvUtil.writeRow(movementsWriter, List.of(
                        Long.toString(rows.getLong("id")), nullableId(rows, "session_id"),
                        rows.getTimestamp("created_at").toLocalDateTime().toString(),
                        nullableId(rows, "created_by"), rows.getString("kind"),
                        rows.getString("category"), rows.getString("payment_method"),
                        rows.getBigDecimal("amount").toPlainString(),
                        nullableId(rows, "sale_id"), nullableId(rows, "sale_return_id"),
                        Objects.toString(rows.getString("note"), "")));
            }
        }
        movementsWriter.flush();
        zip.closeEntry();
    }

    private void writeProductAssetsBackup(long businessId, Connection connection, ZipOutputStream zip)
            throws SQLException, IOException {
        zip.putNextEntry(new ZipEntry("productos/codigos.csv"));
        Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        writer.write('\uFEFF');
        CsvUtil.writeRow(writer, List.of("ID de tabla", "ID de fila", "Código de barras", "Archivo de foto"));
        List<Long> photoRows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT module_id,row_id,barcode,image_data IS NOT NULL AS has_image
                FROM product_assets WHERE business_id=? ORDER BY module_id,row_id
                """)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long moduleId = rows.getLong("module_id");
                    long rowId = rows.getLong("row_id");
                    boolean hasImage = rows.getBoolean("has_image");
                    CsvUtil.writeRow(writer, List.of(Long.toString(moduleId), Long.toString(rowId),
                            Objects.toString(rows.getString("barcode"), ""),
                            hasImage ? photoEntryName(moduleId, rowId) : ""));
                    if (hasImage) photoRows.add(rowId);
                }
            }
        }
        writer.flush();
        zip.closeEntry();

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT module_id,image_data FROM product_assets
                WHERE business_id=? AND row_id=? AND image_data IS NOT NULL
                """)) {
            statement.setLong(1, businessId);
            for (long rowId : photoRows) {
                statement.setLong(2, rowId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) continue;
                    zip.putNextEntry(new ZipEntry(photoEntryName(rows.getLong("module_id"), rowId)));
                    try (InputStream image = rows.getBinaryStream("image_data")) {
                        image.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        }
    }

    private String photoEntryName(long moduleId, long rowId) {
        return "productos/fotos/" + moduleId + "-" + rowId + ".jpg";
    }

    private String nullableDecimal(ResultSet rows, String column) throws SQLException {
        java.math.BigDecimal value = rows.getBigDecimal(column);
        return value == null ? "" : value.toPlainString();
    }

    private String nullableId(ResultSet rows, String column) throws SQLException {
        long id = rows.getLong(column);
        return rows.wasNull() ? "" : Long.toString(id);
    }

    private ImportResult importRecords(long moduleId, long businessId, long userId,
                                       List<List<String>> records, String sourceName) throws SQLException {
        trimTrailingBlankRows(records);
        if (records.isEmpty()) throw new IllegalArgumentException("El archivo está vacío.");
        if (records.size() - 1 > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("El archivo supera el límite de " + MAX_IMPORT_ROWS + " registros.");
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<ModuleColumn> columns = grid.columns(connection, moduleId, businessId);
                if (columns.isEmpty()) throw new IllegalArgumentException("La Tabla no tiene columnas configuradas.");
                List<ModuleColumn> mapping = mapHeaders(records.get(0), columns);
                int imported = 0;
                int skipped = 0;
                String origin = compactSourceName(sourceName);
                for (int recordIndex = 1; recordIndex < records.size(); recordIndex++) {
                    List<String> record = records.get(recordIndex);
                    if (isBlank(record)) { skipped++; continue; }
                    ensureNoUnexpectedValues(record, mapping.size(), recordIndex + 1);
                    Map<Long, String> values = new HashMap<>();
                    for (int columnIndex = 0; columnIndex < mapping.size(); columnIndex++) {
                        ModuleColumn column = mapping.get(columnIndex);
                        if (column == null || column.isSystemId() || column.isReadOnly()) continue;
                        values.put(column.id(), columnIndex < record.size() ? record.get(columnIndex) : "");
                    }
                    long rowId = grid.saveRow(connection, 0, moduleId, businessId, values);
                    audit.log(connection, businessId, userId, moduleId, rowId,
                            "ROW_IMPORTED", "ROW", rowId,
                            "Fila importada desde «" + origin + "».", null, snapshot(values));
                    imported++;
                }
                audit.log(connection, businessId, userId, moduleId, null,
                        "IMPORT_COMPLETED", "MODULE", moduleId,
                        "Importación completada: " + imported + " fila(s) desde «" + origin + "».", null, null);
                connection.commit();
                return new ImportResult(imported, skipped);
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sql) throw sql;
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private List<ModuleColumn> mapHeaders(List<String> rawHeaders, List<ModuleColumn> columns) {
        int headerCount = lastNonBlankIndex(rawHeaders) + 1;
        if (headerCount == 0) throw new IllegalArgumentException("La primera fila debe contener los nombres de las columnas.");
        if (headerCount > MAX_COLUMNS) throw new IllegalArgumentException("El archivo tiene demasiadas columnas.");

        Map<String, ModuleColumn> byName = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (ModuleColumn column : columns) {
            String key = normalizeHeader(column.name());
            if (byName.putIfAbsent(key, column) != null) ambiguous.add(key);
        }
        List<ModuleColumn> mapping = new ArrayList<>(headerCount);
        Set<String> seen = new HashSet<>();
        boolean hasWritable = false;
        for (int index = 0; index < headerCount; index++) {
            String header = index < rawHeaders.size() ? rawHeaders.get(index) : "";
            String key = normalizeHeader(header);
            if (key.isBlank()) throw new IllegalArgumentException("Hay una columna sin nombre en la posición " + (index + 1) + ".");
            if (!seen.add(key)) throw new IllegalArgumentException("La columna «" + header + "» está repetida en el archivo.");
            if (ambiguous.contains(key)) {
                throw new IllegalArgumentException("El encabezado «" + header + "» coincide con más de una columna de la Tabla.");
            }
            if ("id".equals(key)) { mapping.add(null); continue; }
            ModuleColumn column = byName.get(key);
            if (column == null) throw new IllegalArgumentException("La columna «" + header + "» no existe en esta Tabla.");
            mapping.add(column);
            hasWritable |= !column.isReadOnly() && !column.isSystemId();
        }
        if (!hasWritable) throw new IllegalArgumentException("El archivo no contiene columnas que se puedan importar.");
        return mapping;
    }

    private TableSnapshot load(long moduleId, long businessId) throws SQLException {
        CustomModule module = modules.find(moduleId, businessId);
        if (module == null || !module.isTable()) throw new IllegalArgumentException("La Tabla solicitada no existe.");
        List<ModuleRow> rows = grid.rows(moduleId, businessId);
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("La Tabla supera el límite de " + MAX_EXPORT_ROWS + " filas por descarga.");
        }
        return new TableSnapshot(module, grid.columns(moduleId, businessId), rows);
    }

    private void writeCsv(TableSnapshot snapshot, OutputStream output, boolean bom) throws IOException {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        if (bom) writer.write('\uFEFF');
        List<ModuleColumn> columns = exportColumns(snapshot.columns());
        List<String> header = new ArrayList<>();
        header.add("ID");
        columns.forEach(column -> header.add(column.name()));
        CsvUtil.writeRow(writer, header);
        for (ModuleRow row : snapshot.rows()) {
            List<String> values = new ArrayList<>();
            values.add(Long.toString(row.id()));
            columns.forEach(column -> values.add(row.value(column.id())));
            CsvUtil.writeRow(writer, values);
        }
        writer.flush();
    }

    private List<ModuleColumn> exportColumns(List<ModuleColumn> columns) {
        return columns.stream().filter(column -> !column.isSystemId()).toList();
    }

    private void writeReadme(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("LEEME.txt"));
        String text = "Respaldo de MiGestor\r\n\r\n" +
                "Cada Tabla está guardada como CSV UTF-8 dentro de la carpeta tablas.\r\n" +
                "La columna ID es automática y de solo lectura. Las columnas calculadas se incluyen como valores.\r\n" +
                "Las ventas completas y devoluciones están en la carpeta ventas.\r\n" +
                "Los turnos y movimientos de caja están en la carpeta caja.\r\n" +
                "Los códigos y fotos de productos están en la carpeta productos.\r\n" +
                "Este ZIP no es un volcado SQL completo.\r\n";
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String tableEntryName(CustomModule table) {
        return "tablas/" + table.id() + "-" + DownloadUtil.slug(table.name()) + ".csv";
    }

    private String excelValue(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case BLANK -> "";
            case STRING -> cell.getRichStringCellValue().getString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case ERROR -> throw new IllegalArgumentException("El archivo contiene una celda con error de Excel.");
            default -> "";
        };
    }

    private void setHeader(Row row, int index, String value, CellStyle style, int[] widths) {
        Cell cell = row.createCell(index);
        cell.setCellValue(CsvUtil.protectSpreadsheet(value));
        cell.setCellStyle(style);
        widths[index] = Math.max(widths[index], displayLength(value));
    }

    private void setExcelCell(Cell cell, String rawValue, String dataType, CellStyle dateStyle) {
        String value = rawValue == null ? "" : rawValue;
        try {
            if (("NUMBER".equals(dataType) || "ID".equals(dataType)) && !value.isBlank()) {
                BigDecimal number = new BigDecimal(value);
                if (number.precision() <= 15) { cell.setCellValue(number.doubleValue()); return; }
            } else if ("BOOLEAN".equals(dataType) && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                cell.setCellValue(Boolean.parseBoolean(value)); return;
            } else if ("DATE".equals(dataType) && !value.isBlank()) {
                cell.setCellValue(LocalDate.parse(value));
                cell.setCellStyle(dateStyle);
                return;
            }
        } catch (NumberFormatException | DateTimeParseException ignored) {
            // Un valor histórico inválido se exporta como texto y nunca se pierde.
        }
        cell.setCellValue(CsvUtil.protectSpreadsheet(value));
    }

    private void ensureNoUnexpectedValues(List<String> record, int headerCount, int lineNumber) {
        for (int index = headerCount; index < record.size(); index++) {
            if (!record.get(index).isBlank()) {
                throw new IllegalArgumentException("La fila " + lineNumber + " contiene valores sin encabezado.");
            }
        }
    }

    private static boolean isBlank(List<String> row) {
        return row == null || row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private static int lastNonBlankIndex(List<String> row) {
        for (int index = row.size() - 1; index >= 0; index--) if (!row.get(index).isBlank()) return index;
        return -1;
    }

    private static void trimTrailingBlankRows(List<List<String>> rows) {
        while (!rows.isEmpty() && isBlank(rows.get(rows.size() - 1))) rows.remove(rows.size() - 1);
    }

    private String normalizeHeader(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String compactSourceName(String sourceName) {
        String clean = sourceName == null ? "archivo" : sourceName.replaceAll("[\\r\\n\\t]", " ").trim();
        if (clean.isBlank()) clean = "archivo";
        return clean.substring(0, Math.min(clean.length(), 120));
    }

    private String snapshot(Map<Long, String> values) {
        StringBuilder output = new StringBuilder();
        new TreeMap<>(values).forEach((key, value) -> {
            if (!output.isEmpty()) output.append(" | ");
            output.append(key).append('=').append(value == null ? "" : value.replace("|", "\\|"));
        });
        return output.toString();
    }

    private int displayLength(String value) {
        if (value == null) return 0;
        int breakAt = value.indexOf('\n');
        return Math.min(60, breakAt >= 0 ? breakAt : value.length());
    }

    private String safeSheetName(String name) {
        String safe = name == null ? "Tabla" : name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (safe.isBlank()) safe = "Tabla";
        return safe.substring(0, Math.min(31, safe.length()));
    }
}
