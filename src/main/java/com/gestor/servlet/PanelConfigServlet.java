package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.model.PanelField;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PanelRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.util.WebUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/app/panels/config")
public class PanelConfigServlet extends HttpServlet {
    private final ModuleRepository modules = new ModuleRepository();
    private final GridRepository grid = new GridRepository();
    private final PanelRepository panels = new PanelRepository();
    private final PermissionRepository permissions = new PermissionRepository();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        long businessId = WebUtil.businessId(req);
        long panelId = WebUtil.longParam(req, "moduleId");
        try {
            permissions.require(WebUtil.userId(req), businessId, panelId, PermissionRepository.Capability.MANAGE);
            CustomModule panel = modules.find(panelId, businessId);
            if (panel == null || !panel.isPanel()) throw new IllegalArgumentException("El Panel no existe.");
            String action = WebUtil.clean(req.getParameter("action"));
            if ("settings".equals(action)) saveSettings(req, panel, businessId);
            else if ("fields".equals(action)) saveFields(req, panel, businessId);
            else throw new IllegalArgumentException("Acción de configuración no válida.");
        } catch (Exception error) {
            WebUtil.flash(req, "error", error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo guardar la configuración del Panel.");
        }
        res.sendRedirect(req.getContextPath() + "/app/modules/" + panelId);
    }

    private void saveSettings(HttpServletRequest req, CustomModule panel, long businessId) throws Exception {
        long linkedId = WebUtil.longParam(req, "linkedModuleId");
        String panelAction = WebUtil.clean(req.getParameter("panelAction"));
        long rawColumn = WebUtil.longParam(req, "adjustmentColumnId");
        Long adjustmentColumn = rawColumn > 0 ? rawColumn : null;
        String operation = WebUtil.clean(req.getParameter("adjustmentOperation"));
        boolean allowNegative = req.getParameter("allowNegative") != null;
        modules.updatePanelSettings(panel.id(), businessId, linkedId, panelAction,
                adjustmentColumn, operation, allowNegative);
        panels.save(panel.id(), businessId, List.of());
        WebUtil.flash(req, "success", "Configuración de operación guardada. Revisa ahora los campos visibles del formulario.");
    }

    private void saveFields(HttpServletRequest req, CustomModule panel, long businessId) throws Exception {
        if (panel.linkedModuleId() == null) throw new IllegalArgumentException("Selecciona primero una Tabla vinculada.");
        List<ModuleColumn> columns = grid.columns(panel.linkedModuleId(), businessId).stream()
                .filter(column -> !column.isSystemId() && !column.isReadOnly()).toList();
        List<PanelField> fields = new ArrayList<>();
        int fallback = 0;
        for (ModuleColumn column : columns) {
            boolean visible = req.getParameter("visible_" + column.id()) != null;
            boolean required = req.getParameter("required_" + column.id()) != null;
            String defaultValue = WebUtil.clean(req.getParameter("default_" + column.id()));
            int position = intParam(req, "position_" + column.id(), fallback++);
            if (!visible && required && defaultValue.isBlank()) {
                throw new IllegalArgumentException("El campo «" + column.name() + "» no puede estar oculto, ser obligatorio y no tener valor predeterminado.");
            }
            fields.add(new PanelField(0, businessId, panel.id(), column.id(), column.name(),
                    column.dataType(), column.optionsText(), required, defaultValue,
                    column.relatedModuleId(), column.relatedDisplayColumnId(), visible, position));
        }
        panels.save(panel.id(), businessId, fields);
        WebUtil.flash(req, "success", "Campos del formulario guardados.");
    }

    private int intParam(HttpServletRequest req, String name, int fallback) {
        try { return Math.max(0, Integer.parseInt(req.getParameter(name))); }
        catch (Exception ignored) { return fallback; }
    }
}
