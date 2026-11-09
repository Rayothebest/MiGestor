package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.model.ModuleRow;
import com.gestor.model.PanelField;
import com.gestor.model.SavedView;
import com.gestor.model.ViewFilter;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PanelRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.repository.SavedViewRepository;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@WebServlet("/app/modules/*")
public class ModuleServlet extends HttpServlet {
    private final ModuleRepository modules = new ModuleRepository();
    private final GridRepository grid = new GridRepository();
    private final PanelRepository panels = new PanelRepository();
    private final PermissionRepository permissions = new PermissionRepository();
    private final SavedViewRepository views = new SavedViewRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            String path = req.getPathInfo();
            if (path == null || "/".equals(path)) { res.sendRedirect(req.getContextPath() + "/app/dashboard"); return; }
            show(req, res, pathId(path));
        } catch (SecurityException e) {
            WebUtil.flash(req, "error", e.getMessage());
            res.sendRedirect(req.getContextPath() + "/app/dashboard");
        } catch (Exception e) { throw new ServletException(e); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String path = req.getPathInfo() == null ? "" : req.getPathInfo();
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            if ("/create".equals(path)) { create(req, res, businessId); return; }

            long moduleId = pathId(path);
            String action = WebUtil.clean(req.getParameter("action"));
            if ("update".equals(action)) {
                permissions.require(userId, businessId, moduleId, PermissionRepository.Capability.MANAGE);
                String name = WebUtil.clean(req.getParameter("name"));
                if (name.isBlank()) throw new IllegalArgumentException("El nombre no puede quedar vacío.");
                if (!modules.update(moduleId, businessId, name, WebUtil.clean(req.getParameter("description"))))
                    throw new IllegalArgumentException("El módulo ya no existe.");
                WebUtil.flash(req, "success", "Módulo actualizado.");
            } else if ("delete".equals(action)) {
                permissions.require(userId, businessId, moduleId, PermissionRepository.Capability.DELETE);
                CustomModule target = modules.find(moduleId, businessId);
                if (target == null || !modules.delete(moduleId, businessId)) throw new IllegalArgumentException("El módulo ya no existe.");
                WebUtil.flash(req, "success", target.isPanel()
                        ? "Panel eliminado. La Tabla y su historial se conservaron."
                        : "Tabla eliminada junto con sus filas y columnas.");
                res.sendRedirect(req.getContextPath() + "/app/dashboard"); return;
            }
            res.sendRedirect(req.getContextPath() + "/app/modules/" + moduleId);
        } catch (Exception e) { handle(req, res, e); }
    }

    private void create(HttpServletRequest req, HttpServletResponse res, long businessId) throws Exception {
        if (!("OWNER".equals(WebUtil.userRole(req)) || "MANAGER".equals(WebUtil.userRole(req))))
            throw new SecurityException("Solo propietarios y gerentes pueden crear módulos.");
        String name = WebUtil.clean(req.getParameter("name"));
        if (name.isBlank()) throw new IllegalArgumentException("El módulo necesita un nombre.");
        String type = WebUtil.clean(req.getParameter("moduleType"));
        String panelAction = WebUtil.clean(req.getParameter("panelAction"));
        long linked = WebUtil.longParam(req, "linkedModuleId");
        long moduleId = modules.create(businessId, name, WebUtil.clean(req.getParameter("description")),
                type, linked > 0 ? linked : null, panelAction);
        String message = "Tabla creada correctamente.";
        if ("PANEL".equals(type)) message = "ADJUST".equals(panelAction)
                ? "Panel creado. Ahora selecciona la columna de existencias en Configurar Panel."
                : "Panel creado y vinculado correctamente.";
        WebUtil.flash(req, "success", message);
        res.sendRedirect(req.getContextPath() + "/app/modules/" + moduleId);
    }

    private void show(HttpServletRequest req, HttpServletResponse res, long moduleId) throws Exception {
        long businessId = WebUtil.businessId(req);
        long userId = WebUtil.userId(req);
        permissions.require(userId, businessId, moduleId, PermissionRepository.Capability.VIEW);
        CustomModule module = modules.find(moduleId, businessId);
        if (module == null) { res.sendError(404); return; }
        setAccessAttributes(req, moduleId, businessId, userId);
        req.setAttribute("module", module);
        if (module.isPanel()) showPanel(req, res, module, businessId);
        else showTable(req, res, module, businessId, userId);
    }

    private void showTable(HttpServletRequest req, HttpServletResponse res, CustomModule module,
                           long businessId, long userId) throws Exception {
        List<ModuleColumn> columns = editableColumns(grid.columns(module.id(), businessId));
        List<ModuleRow> sourceRows = grid.rows(module.id(), businessId);
        List<SavedView> savedViews = views.list(module.id(), businessId, userId);
        long selectedViewId = WebUtil.longParam(req, "viewId");
        SavedView selectedView = selectedViewId > 0 ? views.find(selectedViewId, module.id(), businessId, userId) : null;
        if (selectedView == null && selectedViewId <= 0) selectedView = savedViews.stream().filter(SavedView::defaultView).findFirst().orElse(null);

        List<ViewFilter> filters;
        Long sortColumnId;
        String sortDirection;
        if (selectedView != null) {
            filters = views.filters(selectedView.id());
            sortColumnId = selectedView.sortColumnId();
            sortDirection = selectedView.sortDirection();
        } else {
            long filterColumnId = WebUtil.longParam(req, "filterColumnId");
            String operator = WebUtil.clean(req.getParameter("filterOperator"));
            filters = filterColumnId > 0 && !operator.isBlank()
                    ? List.of(new ViewFilter(0,0,filterColumnId,operator,
                    WebUtil.clean(req.getParameter("filterValue")),WebUtil.clean(req.getParameter("filterSecondValue")),0))
                    : List.of();
            long requestedSort = WebUtil.longParam(req, "sortColumnId");
            sortColumnId = requestedSort > 0 ? requestedSort : null;
            sortDirection = "DESC".equalsIgnoreCase(req.getParameter("sortDirection")) ? "DESC" : "ASC";
        }
        String search = WebUtil.clean(req.getParameter("q"));
        List<ModuleRow> filtered = grid.filterAndSort(sourceRows, columns, search, filters, sortColumnId, sortDirection);
        req.setAttribute("columns", columns);
        req.setAttribute("rows", filtered);
        req.setAttribute("totalRows", sourceRows.size());
        req.setAttribute("savedViews", savedViews);
        req.setAttribute("selectedView", selectedView);
        req.setAttribute("activeFilters", filters);
        req.setAttribute("searchQuery", search);
        req.setAttribute("sortColumnId", sortColumnId);
        req.setAttribute("sortDirection", sortDirection);
        req.setAttribute("relationOptions", grid.relationOptions(columns, businessId));
        addModuleCatalogs(req, businessId);
        req.getRequestDispatcher("/WEB-INF/views/module.jsp").forward(req, res);
    }

    private void showPanel(HttpServletRequest req, HttpServletResponse res, CustomModule panel,
                           long businessId) throws Exception {
        CustomModule linked = panel.linkedModuleId() == null ? null : modules.find(panel.linkedModuleId(), businessId);
        List<ModuleColumn> inherited = linked == null ? List.of() : editableColumns(grid.columns(linked.id(), businessId));
        List<PanelField> configured = linked == null ? List.of() : panels.fields(panel.id(), businessId, inherited);
        req.setAttribute("linkedModule", linked);
        req.setAttribute("columns", inherited);
        req.setAttribute("panelFields", configured);
        req.setAttribute("visiblePanelFields", configured.stream().filter(PanelField::visible).toList());
        List<ModuleRow> panelRows = linked != null && (panel.isAdjustPanel() || panel.isRemovePanel())
                ? grid.rows(linked.id(), businessId) : List.of();
        req.setAttribute("rows", panelRows);
        req.setAttribute("adjustmentOptions", panelRows.stream()
                .map(row -> new GridRepository.RowOption(row.id(), adjustmentLabel(row, inherited, panel)))
                .toList());
        req.setAttribute("numericColumns", inherited.stream().filter(column -> "NUMBER".equals(column.dataType())).toList());
        req.setAttribute("relationOptions", grid.relationOptions(inherited, businessId));
        req.setAttribute("operationToken", UUID.randomUUID().toString());
        addModuleCatalogs(req, businessId);
        req.getRequestDispatcher("/WEB-INF/views/panel.jsp").forward(req, res);
    }

    private void addModuleCatalogs(HttpServletRequest req, long businessId) throws Exception {
        List<CustomModule> tables = modules.listTables(businessId);
        Map<Long,List<ModuleColumn>> columnsByModule = new HashMap<>();
        for (CustomModule table : tables) columnsByModule.put(table.id(), editableColumns(grid.columns(table.id(), businessId)));
        req.setAttribute("tableModules", tables);
        req.setAttribute("columnsByModule", columnsByModule);
    }

    private void setAccessAttributes(HttpServletRequest req,long moduleId,long businessId,long userId)throws Exception{
        req.setAttribute("canCreate",permissions.can(userId,businessId,moduleId,PermissionRepository.Capability.CREATE));
        req.setAttribute("canEdit",permissions.can(userId,businessId,moduleId,PermissionRepository.Capability.EDIT));
        req.setAttribute("canDelete",permissions.can(userId,businessId,moduleId,PermissionRepository.Capability.DELETE));
        req.setAttribute("canManage",permissions.can(userId,businessId,moduleId,PermissionRepository.Capability.MANAGE));
    }

    private List<ModuleColumn> editableColumns(List<ModuleColumn> columns){return columns.stream().filter(column->!column.isSystemId()).toList();}
    private String adjustmentLabel(ModuleRow row, List<ModuleColumn> columns, CustomModule panel) {
        StringBuilder label = new StringBuilder("#").append(row.id());
        int shown = 0;
        for (ModuleColumn column : columns) {
            if (shown == 2) break;
            if (column.id() == (panel.adjustmentColumnId() == null ? -1L : panel.adjustmentColumnId())) continue;
            if (column.isReadOnly() || !Set.of("TEXT", "SELECT").contains(column.dataType())) continue;
            String value = row.value(column.id());
            if (value == null || value.isBlank()) continue;
            label.append(" · ").append(column.name()).append(": ");
            label.append(value.length() > 50 ? value.substring(0, 47) + "..." : value);
            shown++;
        }
        if (panel.adjustmentColumnId() != null) {
            String value = row.value(panel.adjustmentColumnId());
            label.append(" · ").append(panel.adjustmentColumnName()).append(": ")
                    .append(value == null || value.isBlank() ? "0" : value);
        }
        return label.toString();
    }
    private long pathId(String path){try{return Long.parseLong(path.replace("/",""));}catch(Exception e){return-1;}}
    private void handle(HttpServletRequest req,HttpServletResponse res,Exception e)throws IOException{
        String message=(e instanceof IllegalArgumentException||e instanceof SecurityException)?e.getMessage():"No se pudo completar la operación.";
        WebUtil.flash(req,"error",message);String destination=req.getHeader("Referer")!=null?req.getHeader("Referer"):req.getContextPath()+"/app/dashboard";res.sendRedirect(destination);
    }
}
