package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.service.AlertService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@WebServlet("/app/dashboard")
public class DashboardServlet extends HttpServlet {
    private final ModuleRepository modules = new ModuleRepository();
    private final PermissionRepository permissions = new PermissionRepository();
    private final GridRepository grid = new GridRepository();
    private final AlertService alerts = new AlertService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            Set<Long> visibleIds = Set.copyOf(permissions.visibleModuleIds(userId, businessId));
            List<CustomModule> visibleModules = modules.list(businessId).stream()
                    .filter(module -> visibleIds.contains(module.id()))
                    .toList();
            List<CustomModule> visibleTables = visibleModules.stream()
                    .filter(CustomModule::isTable)
                    .toList();
            int rowCount = 0;
            for (CustomModule table : visibleTables) rowCount += grid.rows(table.id(), businessId).size();
            long alertCount = alerts.evaluateAll(businessId).stream()
                    .filter(match -> visibleIds.contains(match.moduleId()))
                    .count();
            req.setAttribute("modules", visibleModules);
            req.setAttribute("tableModules", visibleTables);
            req.setAttribute("moduleCount", visibleModules.size());
            req.setAttribute("rowCount", rowCount);
            req.setAttribute("alertCount", alertCount);
            req.setAttribute("canCreateModules", "OWNER".equals(WebUtil.userRole(req)) || "MANAGER".equals(WebUtil.userRole(req)));
            req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, res);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
