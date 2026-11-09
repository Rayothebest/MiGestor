package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.model.PanelField;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PanelRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.service.AutomationService;
import com.gestor.service.RowService;
import com.gestor.util.WebUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/app/rows")
public class RowServlet extends HttpServlet {
    private final GridRepository grid = new GridRepository();
    private final ModuleRepository modules = new ModuleRepository();
    private final PanelRepository panels = new PanelRepository();
    private final PermissionRepository permissions = new PermissionRepository();
    private final RowService rows = new RowService();
    private final AutomationService automation = new AutomationService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        long businessId = WebUtil.businessId(req);
        long userId = WebUtil.userId(req);
        long originModuleId = WebUtil.longParam(req, "moduleId");
        try {
            CustomModule origin = modules.find(originModuleId, businessId);
            if (origin == null) { res.sendError(404); return; }
            if (origin.isPanel()) processPanel(req, businessId, userId, origin);
            else processTable(req, businessId, userId, origin);
        } catch (Exception e) {
            String message = e instanceof IllegalArgumentException || e instanceof SecurityException
                    ? e.getMessage() : "No se pudieron guardar los datos.";
            WebUtil.flash(req, "error", "Revisa los datos: " + message);
        }
        res.sendRedirect(req.getContextPath() + "/app/modules/" + originModuleId);
    }

    private void processPanel(HttpServletRequest req, long businessId, long userId, CustomModule panel) throws Exception {
        if (panel.linkedModuleId() == null) throw new IllegalArgumentException("Este Panel no tiene una Tabla vinculada.");
        CustomModule table = modules.find(panel.linkedModuleId(), businessId);
        if (table == null || !table.isTable()) throw new IllegalArgumentException("La Tabla vinculada no está disponible.");

        if (panel.isAdjustPanel()) {
            permissions.require(userId, businessId, panel.id(), PermissionRepository.Capability.EDIT);
            long rowId = WebUtil.longParam(req, "rowId");
            if (rowId <= 0) throw new IllegalArgumentException("Selecciona el artículo que se modificará.");
            BigDecimal quantity;
            try { quantity = new BigDecimal(WebUtil.clean(req.getParameter("quantity")).replace(',', '.')); }
            catch (Exception e) { throw new IllegalArgumentException("Escribe una cantidad válida."); }
            BigDecimal result = automation.adjust(panel, rowId, quantity, WebUtil.clean(req.getParameter("note")),
                    WebUtil.clean(req.getParameter("operationToken")), businessId, userId);
            WebUtil.flash(req, "success", "Operación registrada. Existencia actual: " + result.stripTrailingZeros().toPlainString() + ".");
            return;
        }

        if (panel.isRemovePanel()) {
            throw new IllegalArgumentException("Este Panel usa el modo antiguo que eliminaba filas. Ábrelo en Configurar Panel y elige Ajustar existencias para conservar historial.");
        }
        if (!panel.isRegisterPanel()) throw new IllegalArgumentException("El Panel no tiene una operación válida.");
        permissions.require(userId, businessId, panel.id(), PermissionRepository.Capability.CREATE);
        List<ModuleColumn> inherited = grid.columns(table.id(), businessId);
        List<PanelField> fields = panels.fields(panel.id(), businessId, inherited);
        Map<Long, String> values = new HashMap<>();
        for (PanelField field : fields) {
            if (!field.visible()) continue;
            String value = WebUtil.clean(req.getParameter("col_" + field.columnId()));
            if (value.isBlank() && field.defaultValue() != null) value = field.defaultValue();
            if (field.requiredField() && value.isBlank()) throw new IllegalArgumentException("El campo «" + field.name() + "» es obligatorio.");
            values.put(field.columnId(), value);
        }
        long rowId = rows.create(table.id(), businessId, userId, panel.id(), values);
        WebUtil.flash(req, "success", "Registro #" + rowId + " guardado en «" + table.name() + "».");
    }

    private void processTable(HttpServletRequest req, long businessId, long userId, CustomModule table) throws Exception {
        String action = WebUtil.clean(req.getParameter("action"));
        long rowId = WebUtil.longParam(req, "rowId");
        if ("delete".equals(action)) {
            permissions.require(userId, businessId, table.id(), PermissionRepository.Capability.DELETE);
            if (!rows.delete(rowId, table.id(), businessId, userId)) throw new IllegalArgumentException("La fila ya no existe.");
            WebUtil.flash(req, "success", "Fila eliminada y registrada en el historial.");
            return;
        }
        Map<Long, String> values = submittedValues(req, table.id(), businessId);
        if ("update".equals(action)) {
            permissions.require(userId, businessId, table.id(), PermissionRepository.Capability.EDIT);
            rows.update(rowId, table.id(), businessId, userId, values);
            WebUtil.flash(req, "success", "Fila actualizada.");
        } else {
            permissions.require(userId, businessId, table.id(), PermissionRepository.Capability.CREATE);
            long created = rows.create(table.id(), businessId, userId, null, values);
            WebUtil.flash(req, "success", "Fila #" + created + " creada con ID automático.");
        }
    }

    private Map<Long, String> submittedValues(HttpServletRequest req, long tableId, long businessId) throws Exception {
        Map<Long, String> values = new HashMap<>();
        for (ModuleColumn column : grid.columns(tableId, businessId)) {
            if (!column.isSystemId() && !column.isReadOnly()) {
                values.put(column.id(), WebUtil.clean(req.getParameter("col_" + column.id())));
            }
        }
        return values;
    }
}
