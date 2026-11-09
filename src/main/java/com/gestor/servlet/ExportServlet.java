package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.service.DataTransferService;
import com.gestor.util.DownloadUtil;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;

@WebServlet("/app/export")
public class ExportServlet extends HttpServlet {
    private final PermissionRepository permissions = new PermissionRepository();
    private final ModuleRepository modules = new ModuleRepository();
    private final DataTransferService transfers = new DataTransferService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        long moduleId = WebUtil.longParam(req, "moduleId");
        try {
            long businessId = WebUtil.businessId(req);
            permissions.require(WebUtil.userId(req), businessId, moduleId, PermissionRepository.Capability.VIEW);
            CustomModule module = modules.find(moduleId, businessId);
            if (module == null || !module.isTable()) throw new IllegalArgumentException("La Tabla seleccionada no existe.");
            String format = WebUtil.clean(req.getParameter("format")).toLowerCase(Locale.ROOT);
            String baseName = DownloadUtil.slug(module.name());
            if ("xlsx".equals(format)) {
                DownloadUtil.prepare(res,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baseName + ".xlsx");
                transfers.writeXlsx(moduleId, businessId, res.getOutputStream());
            } else if (format.isBlank() || "csv".equals(format)) {
                DownloadUtil.prepare(res, "text/csv;charset=UTF-8", baseName + ".csv");
                transfers.writeCsv(moduleId, businessId, res.getOutputStream());
            } else throw new IllegalArgumentException("Formato de exportación no compatible.");
        } catch (IllegalArgumentException | SecurityException e) {
            if (!res.isCommitted()) {
                WebUtil.flash(req, "error", e.getMessage());
                res.sendRedirect(req.getContextPath() + "/app/modules/" + moduleId);
            }
        } catch (Exception e) {
            if (res.isCommitted()) throw new ServletException(e);
            throw new ServletException("No fue posible exportar la Tabla.", e);
        }
    }
}
