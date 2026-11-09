package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.service.DataTransferService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@WebServlet("/app/import")
@MultipartConfig(fileSizeThreshold = 1_048_576, maxFileSize = 10_485_760, maxRequestSize = 11_534_336)
public class ImportServlet extends HttpServlet {
    private final PermissionRepository permissions = new PermissionRepository();
    private final ModuleRepository modules = new ModuleRepository();
    private final DataTransferService transfers = new DataTransferService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        long moduleId = WebUtil.longParam(req, "moduleId");
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            permissions.require(userId, businessId, moduleId, PermissionRepository.Capability.CREATE);
            CustomModule module = modules.find(moduleId, businessId);
            if (module == null || !module.isTable()) throw new IllegalArgumentException("La Tabla seleccionada no existe.");

            Part file = req.getPart("file");
            if (file == null || file.getSize() == 0) throw new IllegalArgumentException("Selecciona un archivo CSV o XLSX.");
            String fileName = file.getSubmittedFileName() == null ? "archivo" : file.getSubmittedFileName();
            String lowerName = fileName.toLowerCase(Locale.ROOT);
            DataTransferService.ImportResult result;
            try (InputStream input = file.getInputStream()) {
                if (lowerName.endsWith(".csv")) {
                    result = transfers.importCsv(moduleId, businessId, userId, input, fileName);
                } else if (lowerName.endsWith(".xlsx")) {
                    result = transfers.importXlsx(moduleId, businessId, userId, input, fileName);
                } else {
                    throw new IllegalArgumentException("Formato no compatible. Usa un archivo .csv o .xlsx.");
                }
            }
            String message = "Importación completada: " + result.importedRows() + " fila(s) agregada(s).";
            if (result.skippedBlankRows() > 0) message += " Se omitieron " + result.skippedBlankRows() + " fila(s) vacía(s).";
            WebUtil.flash(req, "success", message);
        } catch (IllegalStateException e) {
            WebUtil.flash(req, "error", "El archivo supera el límite permitido de 10 MB.");
        } catch (IllegalArgumentException | SecurityException e) {
            WebUtil.flash(req, "error", e.getMessage());
        } catch (Exception e) {
            throw new ServletException("No fue posible importar el archivo.", e);
        }
        res.sendRedirect(req.getContextPath() + "/app/modules/" + moduleId);
    }
}
