package com.gestor.servlet;

import com.gestor.repository.AlertRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.service.AlertService;
import com.gestor.service.ScheduledAlertService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@WebServlet("/app/alerts")
public class AlertServlet extends HttpServlet {
    private final AlertRepository alerts = new AlertRepository();
    private final AlertService service = new AlertService();
    private final ScheduledAlertService scheduled = new ScheduledAlertService();
    private final PermissionRepository permissions = new PermissionRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            long businessId = WebUtil.businessId(req);
            Set<Long> visibleIds = Set.copyOf(permissions.visibleModuleIds(WebUtil.userId(req), businessId));
            List<AlertRepository.AlertRuleView> rules = alerts.list(businessId).stream()
                    .filter(rule -> visibleIds.contains(rule.moduleId())).toList();
            List<AlertRepository.AlertRuleView> withCounts = new ArrayList<>();
            for (AlertRepository.AlertRuleView rule : rules) {
                long count = rule.active() ? service.evaluate(businessId, rule).size() : 0;
                withCounts.add(rule.withMatchCount(count));
            }
            req.setAttribute("alertOptions", alerts.options(businessId).stream()
                    .filter(option -> visibleIds.contains(option.moduleId())).toList());
            req.setAttribute("rules", withCounts);
            req.setAttribute("matches", service.evaluate(businessId, rules));
            req.setAttribute("schedules", scheduled.schedules(businessId));
            List<ScheduledAlertService.Notification> notifications = scheduled.notifications(businessId, visibleIds);
            req.setAttribute("notifications", notifications);
            req.setAttribute("unreadNotificationCount", notifications.stream()
                    .filter(notification -> !notification.isAcknowledged()).count());
            req.setAttribute("canManageAlerts", manager(req));
            String token = (String) req.getSession().getAttribute("alertCsrfToken");
            if (token == null) {
                token = UUID.randomUUID().toString();
                req.getSession().setAttribute("alertCsrfToken", token);
            }
            req.setAttribute("alertCsrfToken", token);
            req.getRequestDispatcher("/WEB-INF/views/alerts.jsp").forward(req, res);
        } catch (Exception error) { throw new ServletException(error); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            if (!manager(req)) throw new SecurityException("Solo propietarios y gerentes pueden configurar alertas.");
            String token = (String) req.getSession().getAttribute("alertCsrfToken");
            if (token == null || !token.equals(req.getParameter("alertCsrfToken"))) {
                throw new SecurityException("La página expiró. Recárgala e inténtalo de nuevo.");
            }
            long businessId = WebUtil.businessId(req);
            String action = WebUtil.clean(req.getParameter("action"));
            if ("create".equals(action)) {
                String name = WebUtil.clean(req.getParameter("name"));
                if (name.isBlank()) throw new IllegalArgumentException("La alerta necesita un nombre.");
                alerts.create(businessId, name, WebUtil.longParam(req, "columnId"),
                        WebUtil.clean(req.getParameter("conditionOperator")),
                        WebUtil.clean(req.getParameter("comparisonValue")),
                        WebUtil.clean(req.getParameter("messageTemplate")));
                WebUtil.flash(req, "success", "Alerta creada.");
            } else if ("toggle".equals(action)) {
                long ruleId = WebUtil.longParam(req, "ruleId");
                if (!alerts.toggle(ruleId, businessId)) throw new IllegalArgumentException("La alerta no existe.");
                AlertRepository.AlertRuleView updated = alerts.find(ruleId, businessId);
                if (updated != null && !updated.active()) scheduled.resolveRule(businessId, ruleId);
                WebUtil.flash(req, "success", "Estado de la alerta actualizado.");
            } else if ("delete".equals(action)) {
                if (!alerts.delete(WebUtil.longParam(req, "ruleId"), businessId)) throw new IllegalArgumentException("La alerta no existe.");
                WebUtil.flash(req, "success", "Alerta eliminada.");
            } else if ("schedule".equals(action)) {
                int minutes;
                try { minutes = Integer.parseInt(WebUtil.clean(req.getParameter("intervalMinutes"))); }
                catch (NumberFormatException invalid) { throw new IllegalArgumentException("Elige una frecuencia válida."); }
                scheduled.setSchedule(businessId, WebUtil.longParam(req, "ruleId"), minutes);
                WebUtil.flash(req, "success", minutes == 0 ? "Programación desactivada." : "Frecuencia guardada. La primera revisión se hará mientras MiGestor esté encendido.");
            } else if ("runNow".equals(action)) {
                scheduled.runNow(businessId, WebUtil.longParam(req, "ruleId"));
                WebUtil.flash(req, "success", "Alerta revisada. Los avisos se actualizaron.");
            } else if ("acknowledge".equals(action)) {
                if (!scheduled.acknowledge(businessId, WebUtil.longParam(req, "notificationId"), WebUtil.userId(req))) {
                    throw new IllegalArgumentException("El aviso ya no está disponible.");
                }
                WebUtil.flash(req, "success", "Aviso marcado como visto.");
            } else throw new IllegalArgumentException("Acción de alerta no válida.");
        } catch (Exception error) {
            WebUtil.flash(req, "error", error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo modificar la alerta.");
        }
        res.sendRedirect(req.getContextPath() + "/app/alerts");
    }

    private boolean manager(HttpServletRequest req) {
        return "OWNER".equals(WebUtil.userRole(req)) || "MANAGER".equals(WebUtil.userRole(req));
    }
}
