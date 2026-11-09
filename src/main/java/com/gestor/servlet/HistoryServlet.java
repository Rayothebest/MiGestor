package com.gestor.servlet;

import com.gestor.repository.AuditRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/app/history")
public class HistoryServlet extends HttpServlet {
    private final AuditRepository audit = new AuditRepository();
    private final PermissionRepository permissions = new PermissionRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            if (!permissions.isOwner(WebUtil.userId(req), WebUtil.businessId(req)))
                throw new SecurityException("Solo un propietario puede consultar el historial completo.");
            req.setAttribute("events", audit.list(WebUtil.businessId(req), 300));
            req.getRequestDispatcher("/WEB-INF/views/history.jsp").forward(req, res);
        } catch (SecurityException error) {
            WebUtil.flash(req, "error", error.getMessage());
            res.sendRedirect(req.getContextPath() + "/app/dashboard");
        } catch (Exception error) { throw new ServletException(error); }
    }
}
