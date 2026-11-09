package com.gestor.servlet;

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
import java.time.LocalDate;

@WebServlet("/app/backup")
public class BackupServlet extends HttpServlet {
    private final PermissionRepository permissions = new PermissionRepository();
    private final DataTransferService transfers = new DataTransferService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            long businessId = WebUtil.businessId(req);
            if (!permissions.isOwner(WebUtil.userId(req), businessId)) {
                throw new SecurityException("Solo el propietario puede descargar un respaldo completo.");
            }
            DownloadUtil.prepare(res, "application/zip", "migestor-respaldo-" + LocalDate.now() + ".zip");
            transfers.writeBusinessBackup(businessId, res.getOutputStream());
        } catch (SecurityException | IllegalArgumentException e) {
            if (!res.isCommitted()) {
                WebUtil.flash(req, "error", e.getMessage());
                res.sendRedirect(req.getContextPath() + "/app/dashboard");
            }
        } catch (Exception e) {
            if (res.isCommitted()) throw new ServletException(e);
            throw new ServletException("No fue posible crear el respaldo.", e);
        }
    }
}
