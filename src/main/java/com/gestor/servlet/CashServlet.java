package com.gestor.servlet;

import com.gestor.service.CashService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

@WebServlet("/app/cash")
public class CashServlet extends HttpServlet {
    private final CashService cash = new CashService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            if (!cash.canManage(businessId, userId)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            String csrf = (String) req.getSession().getAttribute("cashCsrfToken");
            if (csrf == null) {
                csrf = UUID.randomUUID().toString();
                req.getSession().setAttribute("cashCsrfToken", csrf);
            }
            req.setAttribute("cashCsrfToken", csrf);
            req.setAttribute("openToken", UUID.randomUUID().toString());
            req.setAttribute("movementToken", UUID.randomUUID().toString());
            req.setAttribute("closeToken", UUID.randomUUID().toString());
            req.setAttribute("cashSummary", cash.summary(businessId, userId));
            req.setAttribute("cashMovements", cash.movements(businessId, userId, 80));
            req.setAttribute("cashClosings", cash.closings(businessId, userId, 10));
            req.getRequestDispatcher("/WEB-INF/views/cash.jsp").forward(req, res);
        } catch (SecurityException denied) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (Exception error) {
            throw new ServletException(error);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String destination = req.getContextPath() + "/app/cash";
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            if (!cash.canManage(businessId, userId))
                throw new SecurityException("Solo propietarios y gerentes pueden gestionar la caja.");
            String csrf = (String) req.getSession().getAttribute("cashCsrfToken");
            if (csrf == null || !csrf.equals(req.getParameter("cashCsrfToken")))
                throw new SecurityException("La página expiró. Recárgala e inténtalo de nuevo.");

            String action = WebUtil.clean(req.getParameter("action"));
            if ("open".equals(action)) {
                destination += "#cash-current";
                long sessionId = cash.open(businessId, userId,
                        WebUtil.clean(req.getParameter("operationToken")),
                        decimal(req, "openingCash", "fondo inicial"),
                        WebUtil.clean(req.getParameter("note")));
                WebUtil.flash(req, "success", "Turno de caja #" + sessionId + " abierto.");
            } else if ("movement".equals(action)) {
                destination += "#cash-movements";
                long movementId = cash.createMovement(businessId, userId,
                        WebUtil.clean(req.getParameter("operationToken")),
                        WebUtil.clean(req.getParameter("kind")),
                        WebUtil.clean(req.getParameter("category")),
                        WebUtil.clean(req.getParameter("method")),
                        decimal(req, "amount", "importe"),
                        WebUtil.clean(req.getParameter("note")));
                WebUtil.flash(req, "success", "Movimiento #" + movementId + " registrado.");
            } else if ("close".equals(action)) {
                destination += "#cash-closings";
                long closingId = cash.close(businessId, userId,
                        WebUtil.clean(req.getParameter("operationToken")),
                        decimal(req, "countedCash", "efectivo contado"),
                        WebUtil.clean(req.getParameter("note")));
                WebUtil.flash(req, "success", "Turno #" + closingId + " cerrado; consulta el arqueo guardado.");
            } else {
                throw new IllegalArgumentException("Acción de caja no válida.");
            }
        } catch (Exception error) {
            if (!(error instanceof IllegalArgumentException || error instanceof SecurityException))
                getServletContext().log("No se pudo guardar el movimiento de caja", error);
            WebUtil.flash(req, "error", error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo completar la operación de caja.");
        }
        res.sendRedirect(destination);
    }

    private BigDecimal decimal(HttpServletRequest req, String name, String label) {
        try {
            return new BigDecimal(WebUtil.clean(req.getParameter(name)));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Revisa el " + label + ".");
        }
    }
}
