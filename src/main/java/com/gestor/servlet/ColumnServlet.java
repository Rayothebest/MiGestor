package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.util.WebUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;

@WebServlet("/app/columns")
public class ColumnServlet extends HttpServlet {
    private final GridRepository grid = new GridRepository();
    private final ModuleRepository modules = new ModuleRepository();
    private final PermissionRepository permissions = new PermissionRepository();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        long businessId = WebUtil.businessId(req);
        long moduleId = WebUtil.longParam(req, "moduleId");

        try {
            CustomModule module = modules.find(moduleId, businessId);
            if (module == null) {
                res.sendError(404);
                return;
            }
            if (!module.isTable()) {
                throw new IllegalArgumentException("Las columnas de un Panel se editan desde su tabla vinculada.");
            }
            permissions.require(WebUtil.userId(req), businessId, moduleId, PermissionRepository.Capability.MANAGE);

            String action = WebUtil.clean(req.getParameter("action"));
            if ("delete".equals(action)) {
                grid.deleteColumn(WebUtil.longParam(req, "columnId"), moduleId, businessId);
                WebUtil.flash(req, "success", "Columna eliminada.");
            } else {
                String name = WebUtil.clean(req.getParameter("name"));
                String type = WebUtil.clean(req.getParameter("dataType"));
                if (name.isBlank()) throw new IllegalArgumentException("La columna necesita un nombre.");

                String options = "SELECT".equals(type)
                        ? WebUtil.clean(req.getParameter("options"))
                        : null;
                boolean required = "on".equals(req.getParameter("requiredField"));
                String defaultValue = WebUtil.clean(req.getParameter("defaultValue"));
                Long relatedModuleId = positive(req, "relatedModuleId");
                Long displayColumnId = positive(req, "relatedDisplayColumnId");
                Long relationColumnId = positive(req, "relationColumnId");
                Long sourceColumnId = positive(req, "sourceColumnId");
                String formula = WebUtil.clean(req.getParameter("formulaExpression"));
                BigDecimal threshold = decimal(req.getParameter("lowStockThreshold"));

                if ("update".equals(action)) {
                    grid.updateColumn(WebUtil.longParam(req, "columnId"), moduleId, businessId, name, type,
                            options, required, defaultValue, relatedModuleId, displayColumnId,
                            relationColumnId, sourceColumnId, formula, threshold);
                    WebUtil.flash(req, "success", "Columna actualizada.");
                } else {
                    grid.createColumn(moduleId, businessId, name, type, options, required, defaultValue,
                            relatedModuleId, displayColumnId, relationColumnId, sourceColumnId, formula, threshold);
                    WebUtil.flash(req, "success", "Columna creada.");
                }
            }
        } catch (Exception e) {
            WebUtil.flash(req, "error", e instanceof IllegalArgumentException
                    ? e.getMessage()
                    : "No se pudo modificar la columna.");
        }

        res.sendRedirect(req.getContextPath() + "/app/modules/" + moduleId);
    }

    private Long positive(HttpServletRequest req, String name) {
        long value = WebUtil.longParam(req, name);
        return value > 0 ? value : null;
    }

    private BigDecimal decimal(String value) {
        String clean = WebUtil.clean(value);
        if (clean.isBlank()) return null;
        try { return new BigDecimal(clean.replace(',', '.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("El umbral debe ser numérico."); }
    }
}
