package com.gestor.servlet;

import com.gestor.service.ProductAssetService;
import com.gestor.service.SalesService;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@WebServlet("/app/product-assets")
@MultipartConfig(fileSizeThreshold = 0, maxFileSize = 4 * 1024 * 1024,
        maxRequestSize = 5 * 1024 * 1024)
public class ProductAssetServlet extends HttpServlet {
    private static final long MAX_UPLOAD_BYTES = 4L * 1024 * 1024;
    private final ProductAssetService assets = new ProductAssetService();
    private final SalesService sales = new SalesService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        if (req.getParameter("photo") != null) {
            servePhoto(req, res);
            return;
        }
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            if (!assets.canManage(businessId, userId)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            String csrf = (String) req.getSession().getAttribute("productAssetsCsrfToken");
            if (csrf == null) {
                csrf = UUID.randomUUID().toString();
                req.getSession().setAttribute("productAssetsCsrfToken", csrf);
            }
            req.setAttribute("productAssetsCsrfToken", csrf);
            req.setAttribute("salesConfiguration", sales.configuration(businessId));
            req.setAttribute("assetProducts", assets.list(businessId, userId));
            req.getRequestDispatcher("/WEB-INF/views/product_assets.jsp").forward(req, res);
        } catch (SecurityException denied) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (Exception error) {
            throw new ServletException(error);
        }
    }

    private void servePhoto(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        try {
            long rowId = WebUtil.longParam(req, "photo");
            if (rowId <= 0) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            ProductAssetService.Photo photo = assets.photo(
                    WebUtil.businessId(req), WebUtil.userId(req), rowId);
            if (photo == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("Cache-Control", "private, no-store");
            res.setContentType(photo.getMimeType());
            res.setContentLength(photo.getBytes().length);
            res.getOutputStream().write(photo.getBytes());
        } catch (SecurityException denied) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (IllegalArgumentException missing) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception error) {
            throw new ServletException(error);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String destination = req.getContextPath() + "/app/product-assets";
        try {
            long businessId = WebUtil.businessId(req);
            long userId = WebUtil.userId(req);
            if (!assets.canManage(businessId, userId))
                throw new SecurityException("Solo propietarios y gerentes pueden modificar códigos y fotos.");
            String csrf = (String) req.getSession().getAttribute("productAssetsCsrfToken");
            if (csrf == null || !csrf.equals(req.getParameter("productAssetsCsrfToken")))
                throw new SecurityException("La página expiró. Recárgala e inténtalo de nuevo.");
            long rowId = WebUtil.longParam(req, "rowId");
            if (rowId <= 0) throw new IllegalArgumentException("Selecciona un producto válido.");
            destination += "#product-" + rowId;

            switch (WebUtil.clean(req.getParameter("action"))) {
                case "code" -> {
                    assets.saveCode(businessId, userId, rowId,
                            WebUtil.clean(req.getParameter("barcode")));
                    WebUtil.flash(req, "success", "Código de producto guardado.");
                }
                case "photo" -> {
                    Part file = req.getPart("photo");
                    if (file == null || file.getSize() == 0)
                        throw new IllegalArgumentException("Elige una foto JPEG o PNG.");
                    if (file.getSize() > MAX_UPLOAD_BYTES)
                        throw new IllegalArgumentException("La foto no puede superar 4 MB.");
                    try (InputStream data = file.getInputStream()) {
                        assets.savePhoto(businessId, userId, rowId, data);
                    }
                    WebUtil.flash(req, "success", "Foto de producto guardada.");
                }
                case "removePhoto" -> {
                    assets.removePhoto(businessId, userId, rowId);
                    WebUtil.flash(req, "success", "Foto de producto eliminada.");
                }
                default -> throw new IllegalArgumentException("Acción de catálogo no válida.");
            }
        } catch (Exception error) {
            if (!(error instanceof IllegalArgumentException || error instanceof SecurityException ||
                    error instanceof IllegalStateException)) {
                getServletContext().log("No se pudieron guardar los datos del producto", error);
            }
            String message = error instanceof IllegalStateException ? "La foto supera el tamaño permitido de 4 MB." :
                    error instanceof IllegalArgumentException || error instanceof SecurityException ?
                            error.getMessage() : "No se pudo completar la operación del catálogo.";
            WebUtil.flash(req, "error", message);
        }
        res.sendRedirect(destination);
    }
}
