package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.repository.ReportRepository;
import com.gestor.service.ReportService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@WebServlet("/app/reports")
public class ReportServlet extends HttpServlet {
    private final ModuleRepository modules = new ModuleRepository();
    private final GridRepository grid = new GridRepository();
    private final PermissionRepository permissions = new PermissionRepository();
    private final ReportRepository reports = new ReportRepository();
    private final ReportService service = new ReportService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            Set<Long> visibleIds = Set.copyOf(permissions.visibleModuleIds(userId, businessId));
            List<CustomModule> visibleTables = modules.listTables(businessId).stream()
                    .filter(module -> visibleIds.contains(module.id())).toList();
            req.setAttribute("modules", visibleTables);
            long moduleId = WebUtil.longParam(req, "moduleId");
            CustomModule selected = visibleTables.stream().filter(module -> module.id() == moduleId).findFirst().orElse(null);
            req.setAttribute("selectedModule", selected);
            if (selected != null) {
                req.setAttribute("columns", grid.columns(selected.id(), businessId));
                req.setAttribute("rowCount", service.rowCount(selected.id(), businessId));
                req.setAttribute("numericSummaries", service.numericSummaries(selected.id(), businessId));
                req.setAttribute("groupSummaries", service.groupSummaries(selected.id(), businessId));
            }
            List<ReportRepository.SavedReportView> saved = reports.list(businessId, userId).stream()
                    .filter(report -> visibleIds.contains(report.moduleId())).toList();
            req.setAttribute("savedReports", saved);
            Map<Long, ReportService.ChartView> charts = new HashMap<>();
            for (ReportRepository.SavedReportView report : saved) {
                if (!"CHART".equals(report.reportType())) continue;
                charts.put(report.id(), service.chart(report.moduleId(), businessId,
                        report.groupByColumnId(), report.valueColumnId(),
                        report.aggregateFunction(), report.chartType()));
            }
            req.setAttribute("charts", charts);
            req.getRequestDispatcher("/WEB-INF/views/reports.jsp").forward(req, res);
        } catch (Exception error) { throw new ServletException(error); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        long businessId = WebUtil.businessId(req);
        long userId = WebUtil.userId(req);
        long moduleId = WebUtil.longParam(req, "moduleId");
        try {
            if ("delete".equals(WebUtil.clean(req.getParameter("action")))) {
                if (!reports.delete(WebUtil.longParam(req, "reportId"), businessId, userId))
                    throw new IllegalArgumentException("El reporte no existe o no te pertenece.");
                WebUtil.flash(req, "success", "Reporte eliminado.");
                res.sendRedirect(req.getContextPath() + "/app/reports");
                return;
            }
            permissions.require(userId, businessId, moduleId, PermissionRepository.Capability.VIEW);
            String name = WebUtil.clean(req.getParameter("name"));
            if (name.isBlank()) throw new IllegalArgumentException("El reporte necesita un nombre.");
            reports.create(businessId, userId, moduleId, name,
                    WebUtil.clean(req.getParameter("reportType")),
                    WebUtil.clean(req.getParameter("aggregateFunction")),
                    positive(req, "valueColumnId"), positive(req, "groupByColumnId"),
                    WebUtil.clean(req.getParameter("chartType")));
            WebUtil.flash(req, "success", "Reporte guardado.");
        } catch (Exception error) {
            WebUtil.flash(req, "error", error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo guardar el reporte.");
        }
        res.sendRedirect(req.getContextPath() + "/app/reports" + (moduleId > 0 ? "?moduleId=" + moduleId : ""));
    }

    private Long positive(HttpServletRequest req, String name) {
        long value = WebUtil.longParam(req, name);
        return value > 0 ? value : null;
    }
}
