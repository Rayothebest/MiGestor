package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.service.SalesService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@WebServlet("/app/sales")
public class SalesServlet extends HttpServlet {
    private final SalesService sales = new SalesService();
    private final ModuleRepository modules = new ModuleRepository();
    private final GridRepository grid = new GridRepository();
    private final PermissionRepository permissions = new PermissionRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            boolean canConfigure = sales.canConfigure(businessId, userId);
            req.setAttribute("canConfigureSales", canConfigure);
            req.setAttribute("canOverridePrice", sales.canOverridePrice(businessId, userId));
            req.setAttribute("canReturnSale", sales.canReturn(businessId, userId));

            if (canConfigure) {
                List<CustomModule> tables = modules.listTables(businessId);
                Map<Long, List<ModuleColumn>> columnsByModule = new HashMap<>();
                for (CustomModule table : tables) {
                    columnsByModule.put(table.id(), grid.columns(table.id(), businessId).stream()
                            .filter(column -> !column.isSystemId() && !column.isReadOnly()).toList());
                }
                req.setAttribute("tableModules", tables);
                req.setAttribute("columnsByModule", columnsByModule);
            }

            SalesService.SaleConfiguration configuration = sales.configuration(businessId);
            req.setAttribute("salesConfiguration", configuration);
            req.setAttribute("operationToken", UUID.randomUUID().toString());
            req.setAttribute("recentSales", sales.recent(businessId, userId, 20));
            long requestedSale = WebUtil.longParam(req, "saleId");
            if (requestedSale > 0) {
                try {
                    SalesService.SaleDetail selected = sales.detail(businessId, userId, requestedSale);
                    req.setAttribute("selectedSale", selected);
                    req.setAttribute("hasReturnableItems", selected != null && selected.items().stream()
                            .anyMatch(item -> item.productRowId() != null &&
                                    item.returnableQuantity().signum() > 0));
                    String returnCsrfToken = (String) req.getSession().getAttribute("saleReturnCsrfToken");
                    if (returnCsrfToken == null) {
                        returnCsrfToken = UUID.randomUUID().toString();
                        req.getSession().setAttribute("saleReturnCsrfToken", returnCsrfToken);
                    }
                    req.setAttribute("saleReturnCsrfToken", returnCsrfToken);
                    req.setAttribute("returnOperationToken", UUID.randomUUID().toString());
                } catch (SecurityException denied) {
                    res.sendError(404);
                    return;
                }
            }

            if (configuration != null) {
                CustomModule source = modules.find(configuration.moduleId(), businessId);
                req.setAttribute("salesSource", source);
                if (source != null) {
                    boolean canView = permissions.can(userId, businessId, source.id(), PermissionRepository.Capability.VIEW);
                    req.setAttribute("canViewSalesSource", canView);
                    req.setAttribute("canSell", sales.canSell(businessId, userId));
                    if (canView) req.setAttribute("products", sales.catalog(businessId, userId));
                }
            }
            req.getRequestDispatcher("/WEB-INF/views/sales.jsp").forward(req, res);
        } catch (SecurityException denied) {
            res.sendError(403);
        } catch (Exception error) {
            throw new ServletException(error);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String destination = req.getContextPath() + "/app/sales";
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            String action = WebUtil.clean(req.getParameter("action"));
            if ("configure".equals(action)) {
                sales.configure(businessId, userId,
                        WebUtil.longParam(req, "moduleId"),
                        WebUtil.longParam(req, "displayColumnId"),
                        WebUtil.longParam(req, "stockColumnId"),
                        WebUtil.longParam(req, "priceColumnId"));
                WebUtil.flash(req, "success", "Origen de productos guardado.");
            } else if ("create".equals(action)) {
                long saleId = sales.create(businessId, userId,
                        WebUtil.clean(req.getParameter("operationToken")),
                        WebUtil.clean(req.getParameter("paymentMethod")),
                        WebUtil.clean(req.getParameter("note")), saleLines(req));
                WebUtil.flash(req, "success", "Venta #" + saleId + " registrada y existencias actualizadas.");
                destination += "?saleId=" + saleId + "#sale-detail";
            } else if ("return".equals(action)) {
                long saleId = WebUtil.longParam(req, "saleId");
                if (saleId <= 0) throw new IllegalArgumentException("Selecciona una venta válida.");
                destination += "?saleId=" + saleId + "#sale-detail";
                String csrfToken = (String) req.getSession().getAttribute("saleReturnCsrfToken");
                if (csrfToken == null || !csrfToken.equals(req.getParameter("saleReturnCsrfToken"))) {
                    throw new SecurityException("La página expiró. Recárgala e inténtalo de nuevo.");
                }
                long returnId = sales.createReturn(businessId, userId, saleId,
                        WebUtil.clean(req.getParameter("returnOperationToken")),
                        WebUtil.clean(req.getParameter("refundMethod")),
                        WebUtil.clean(req.getParameter("returnNote")), returnLines(req));
                WebUtil.flash(req, "success", "Devolución #" + returnId
                        + " registrada y existencias repuestas. El método de reembolso quedó anotado.");
            } else {
                throw new IllegalArgumentException("Acción de ventas desconocida.");
            }
        } catch (Exception error) {
            if (!(error instanceof IllegalArgumentException || error instanceof SecurityException)) {
                getServletContext().log("No se pudo guardar la venta", error);
            }
            WebUtil.flash(req, "error", error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo completar la operación de ventas.");
        }
        res.sendRedirect(destination);
    }

    private List<SalesService.SaleRequestLine> saleLines(HttpServletRequest req) {
        String[] ids = req.getParameterValues("productId");
        String[] quantities = req.getParameterValues("quantity");
        String[] prices = req.getParameterValues("unitPrice");
        if (ids == null || quantities == null || prices == null || ids.length == 0
                || ids.length != quantities.length || ids.length != prices.length || ids.length > 100) {
            throw new IllegalArgumentException("Agrega entre 1 y 100 productos completos a la venta.");
        }
        List<SalesService.SaleRequestLine> lines = new java.util.ArrayList<>(ids.length);
        for (int index = 0; index < ids.length; index++) {
            try {
                long rowId = Long.parseLong(ids[index]);
                BigDecimal quantity = new BigDecimal(quantities[index]);
                BigDecimal unitPrice = new BigDecimal(prices[index]);
                if (rowId <= 0 || quantity.signum() <= 0 || unitPrice.signum() < 0) throw new NumberFormatException();
                lines.add(new SalesService.SaleRequestLine(rowId, quantity, unitPrice));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Revisa producto, cantidad y precio en cada renglón.");
            }
        }
        return lines;
    }

    private List<SalesService.ReturnRequestLine> returnLines(HttpServletRequest req) {
        String[] ids = req.getParameterValues("saleItemId");
        String[] quantities = req.getParameterValues("returnQuantity");
        if (ids == null || quantities == null || ids.length == 0 ||
                ids.length != quantities.length || ids.length > 100) {
            throw new IllegalArgumentException("Selecciona entre 1 y 100 artículos para devolver.");
        }
        List<SalesService.ReturnRequestLine> lines = new java.util.ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < ids.length; index++) {
            try {
                long itemId = Long.parseLong(ids[index]);
                String raw = WebUtil.clean(quantities[index]);
                BigDecimal quantity = raw.isEmpty() ? BigDecimal.ZERO : new BigDecimal(raw);
                if (itemId <= 0 || quantity.signum() < 0 || !seen.add(itemId)) throw new NumberFormatException();
                if (quantity.signum() > 0) lines.add(new SalesService.ReturnRequestLine(itemId, quantity));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Revisa las cantidades de la devolución.");
            }
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("Indica la cantidad que deseas devolver.");
        return lines;
    }
}
