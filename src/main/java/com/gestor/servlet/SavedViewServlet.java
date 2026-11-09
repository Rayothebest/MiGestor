package com.gestor.servlet;

import com.gestor.model.ViewFilter;
import com.gestor.repository.PermissionRepository;
import com.gestor.repository.SavedViewRepository;
import com.gestor.util.WebUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/app/views")
public class SavedViewServlet extends HttpServlet {
    private final SavedViewRepository views = new SavedViewRepository();
    private final PermissionRepository permissions = new PermissionRepository();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        long moduleId = WebUtil.longParam(req, "moduleId");
        long businessId = WebUtil.businessId(req);
        long userId = WebUtil.userId(req);
        try {
            permissions.require(userId, businessId, moduleId, PermissionRepository.Capability.VIEW);
            if ("delete".equals(WebUtil.clean(req.getParameter("action")))) {
                if (!views.delete(WebUtil.longParam(req, "viewId"), businessId, userId))
                    throw new IllegalArgumentException("La vista no existe o no te pertenece.");
                WebUtil.flash(req, "success", "Vista eliminada.");
            } else {
                String name = WebUtil.clean(req.getParameter("name"));
                if (name.isBlank()) throw new IllegalArgumentException("La vista necesita un nombre.");
                long rawSort = WebUtil.longParam(req, "sortColumnId");
                views.create(businessId, moduleId, userId, name,
                        rawSort > 0 ? rawSort : null,
                        "DESC".equalsIgnoreCase(req.getParameter("sortDirection")) ? "DESC" : "ASC",
                        req.getParameter("defaultView") != null, filters(req));
                WebUtil.flash(req, "success", "Vista guardada.");
            }
        } catch (Exception error) {
            WebUtil.flash(req, "error", error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo guardar la vista.");
        }
        res.sendRedirect(req.getContextPath() + "/app/modules/" + moduleId);
    }

    private List<ViewFilter> filters(HttpServletRequest req) {
        String[] columns = req.getParameterValues("filterColumnId");
        String[] operators = req.getParameterValues("filterOperator");
        String[] values = req.getParameterValues("filterValue");
        String[] seconds = req.getParameterValues("filterSecondValue");
        if (columns == null || operators == null) return List.of();
        List<ViewFilter> filters = new ArrayList<>();
        for (int index = 0; index < Math.min(columns.length, operators.length); index++) {
            long columnId;
            try { columnId = Long.parseLong(columns[index]); } catch (Exception ignored) { continue; }
            if (columnId <= 0 || operators[index] == null || operators[index].isBlank()) continue;
            filters.add(new ViewFilter(0, 0, columnId, operators[index].trim().toUpperCase(),
                    at(values, index), at(seconds, index), filters.size()));
        }
        return filters;
    }

    private String at(String[] values, int index) {
        return values != null && index < values.length ? WebUtil.clean(values[index]) : "";
    }
}
