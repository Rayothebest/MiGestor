package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.service.WorkflowService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@WebServlet("/app/workflows")
public class WorkflowServlet extends HttpServlet {
    private final WorkflowService workflows = new WorkflowService();
    private final ModuleRepository modules = new ModuleRepository();
    private final GridRepository grid = new GridRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            if (!workflows.canManage(businessId, userId)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            List<CustomModule> tables = modules.listTables(businessId);
            Map<Long, List<ModuleColumn>> columnsByModule = new HashMap<>();
            for (CustomModule table : tables) {
                columnsByModule.put(table.id(), grid.columns(table.id(), businessId).stream()
                        .filter(column -> !column.isSystemId()).toList());
            }
            req.setAttribute("workflowTables", tables);
            req.setAttribute("workflowColumns", columnsByModule);
            req.setAttribute("workflowRules", workflows.list(businessId, userId));

            String editValue = WebUtil.clean(req.getParameter("edit"));
            if (!editValue.isBlank()) {
                long editId = positiveId(editValue, "El flujo solicitado no es válido.");
                WorkflowService.Rule rule = workflows.find(businessId, userId, editId);
                if (rule == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                req.setAttribute("editWorkflow", rule);
            }

            String csrfToken = (String) req.getSession().getAttribute("workflowCsrfToken");
            if (csrfToken == null) {
                csrfToken = UUID.randomUUID().toString();
                req.getSession().setAttribute("workflowCsrfToken", csrfToken);
            }
            req.setAttribute("workflowCsrfToken", csrfToken);
            req.getRequestDispatcher("/WEB-INF/views/workflows.jsp").forward(req, res);
        } catch (IllegalArgumentException invalid) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, invalid.getMessage());
        } catch (Exception error) {
            throw new ServletException(error);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String destination = req.getContextPath() + "/app/workflows";
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            if (!workflows.canManage(businessId, userId)) throw new SecurityException("Solo propietarios y gerentes pueden configurar flujos.");
            String csrfToken = (String) req.getSession().getAttribute("workflowCsrfToken");
            if (csrfToken == null || !csrfToken.equals(req.getParameter("workflowCsrfToken"))) {
                throw new SecurityException("La página expiró. Recárgala e inténtalo de nuevo.");
            }

            String action = WebUtil.clean(req.getParameter("action"));
            if ("save".equals(action)) {
                WorkflowService.RuleInput input = input(req);
                long ruleId = workflows.save(businessId, userId, input);
                WebUtil.flash(req, "success", input.id() > 0 ? "Flujo actualizado." : "Flujo creado.");
                destination += "?edit=" + ruleId + "#workflow-builder";
            } else if ("toggle".equals(action)) {
                long ruleId = positiveId(req.getParameter("ruleId"), "Elige un flujo válido.");
                boolean active = "true".equals(req.getParameter("active"));
                workflows.setActive(businessId, userId, ruleId, active);
                WebUtil.flash(req, "success", active ? "Flujo activado." : "Flujo pausado.");
            } else if ("delete".equals(action)) {
                workflows.delete(businessId, userId, positiveId(req.getParameter("ruleId"), "Elige un flujo válido."));
                WebUtil.flash(req, "success", "Flujo eliminado.");
            } else {
                throw new IllegalArgumentException("Acción de flujo no válida.");
            }
        } catch (Exception error) {
            if (!(error instanceof IllegalArgumentException || error instanceof SecurityException)) {
                getServletContext().log("No se pudo modificar el flujo", error);
            }
            WebUtil.flash(req, "error", error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo guardar el flujo.");
            if ("save".equals(req.getParameter("action"))) {
                long editId = WebUtil.longParam(req, "ruleId");
                destination += editId > 0 ? "?edit=" + editId + "#workflow-builder" : "#workflow-builder";
            }
        }
        res.sendRedirect(destination);
    }

    private WorkflowService.RuleInput input(HttpServletRequest req) {
        String triggerType = WebUtil.clean(req.getParameter("triggerType"));
        String actionType = WebUtil.clean(req.getParameter("actionType"));
        boolean rowTrigger = "ROW_CREATED".equals(triggerType);
        boolean adjustStock = "ADJUST_STOCK".equals(actionType);
        boolean createRow = "CREATE_ROW".equals(actionType);
        String rawId = WebUtil.clean(req.getParameter("ruleId"));
        long id = rawId.isBlank() || "0".equals(rawId)
                ? 0 : positiveId(rawId, "Identificador de flujo no válido.");
        return new WorkflowService.RuleInput(
                id,
                WebUtil.clean(req.getParameter("name")),
                req.getParameter("active") != null,
                triggerType,
                rowTrigger ? optionalId(req.getParameter("sourceModuleId"), "Tabla de origen no válida.") : null,
                rowTrigger ? optionalId(req.getParameter("conditionColumnId"), "Columna de condición no válida.") : null,
                rowTrigger ? WebUtil.clean(req.getParameter("conditionValue")) : null,
                actionType,
                positiveId(req.getParameter("targetModuleId"), "Selecciona una tabla de destino válida."),
                adjustStock ? optionalId(req.getParameter("relationColumnId"), "Relación no válida.") : null,
                adjustStock ? optionalId(req.getParameter("quantityColumnId"), "Cantidad no válida.") : null,
                adjustStock ? optionalId(req.getParameter("stockColumnId"), "Existencias no válidas.") : null,
                adjustStock ? WebUtil.clean(req.getParameter("operation")) : null,
                createRow ? mappings(req) : List.of());
    }

    private List<WorkflowService.MappingInput> mappings(HttpServletRequest req) {
        String[] targets = req.getParameterValues("mappingTargetColumnId");
        String[] types = req.getParameterValues("mappingSourceType");
        String[] sources = req.getParameterValues("mappingSourceColumnId");
        String[] literals = req.getParameterValues("mappingLiteralValue");
        if (targets == null || types == null || sources == null || literals == null
                || targets.length != types.length || targets.length != sources.length || targets.length != literals.length
                || targets.length > 40) {
            throw new IllegalArgumentException("Agrega entre 1 y 40 asignaciones completas.");
        }
        List<WorkflowService.MappingInput> mappings = new ArrayList<>();
        for (int i = 0; i < targets.length; i++) {
            if (WebUtil.clean(targets[i]).isBlank() && WebUtil.clean(types[i]).isBlank()) continue;
            mappings.add(new WorkflowService.MappingInput(
                    positiveId(targets[i], "Selecciona una columna de destino en cada asignación."),
                    WebUtil.clean(types[i]),
                    optionalId(sources[i], "La columna de origen no es válida."),
                    WebUtil.clean(literals[i])));
        }
        if (mappings.isEmpty()) throw new IllegalArgumentException("Agrega al menos una asignación de columnas.");
        return mappings;
    }

    private Long optionalId(String value, String message) {
        if (WebUtil.clean(value).isBlank()) return null;
        return positiveId(value, message);
    }

    private long positiveId(String value, String message) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // Se devuelve un mensaje legible para el formulario.
        }
        throw new IllegalArgumentException(message);
    }
}
