package com.gestor.servlet;

import com.gestor.model.CustomModule;
import com.gestor.repository.ModuleRepository;
import com.gestor.repository.PermissionRepository;
import com.gestor.repository.UserRepository;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

@WebServlet("/app/settings/users")
public class UserAdminServlet extends HttpServlet {
    private final UserRepository users = new UserRepository();
    private final ModuleRepository modules = new ModuleRepository();
    private final PermissionRepository permissions = new PermissionRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            requireOwner(req);
            long businessId = WebUtil.businessId(req);
            req.setAttribute("users", users.list(businessId));
            req.setAttribute("modules", modules.list(businessId));
            req.setAttribute("permissionMatrix", permissions.matrix(businessId));
            req.getRequestDispatcher("/WEB-INF/views/users.jsp").forward(req, res);
        } catch (SecurityException error) {
            WebUtil.flash(req, "error", error.getMessage());
            res.sendRedirect(req.getContextPath() + "/app/dashboard");
        } catch (Exception error) { throw new ServletException(error); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            requireOwner(req);
            String action = WebUtil.clean(req.getParameter("action"));
            if ("create".equals(action)) create(req);
            else if ("update".equals(action)) update(req);
            else if ("permission".equals(action)) savePermissions(req);
            else throw new IllegalArgumentException("Acción de administración no válida.");
        } catch (Exception error) {
            String message = error instanceof SQLIntegrityConstraintViolationException
                    ? "Ese correo ya está registrado."
                    : error instanceof IllegalArgumentException || error instanceof SecurityException
                    ? error.getMessage() : "No se pudo actualizar el usuario.";
            WebUtil.flash(req, "error", message);
        }
        res.sendRedirect(req.getContextPath() + "/app/settings/users");
    }

    private void create(HttpServletRequest req) throws Exception {
        String name = WebUtil.clean(req.getParameter("name"));
        String email = WebUtil.clean(req.getParameter("email")).toLowerCase();
        String password = req.getParameter("password");
        String role = WebUtil.clean(req.getParameter("role"));
        if (name.isBlank() || email.isBlank()) throw new IllegalArgumentException("Nombre y correo son obligatorios.");
        if (password == null || password.length() < 8) throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        users.create(WebUtil.businessId(req), name, email, BCrypt.hashpw(password, BCrypt.gensalt(12)), role);
        WebUtil.flash(req, "success", "Usuario creado.");
    }

    private void update(HttpServletRequest req) throws Exception {
        String name = WebUtil.clean(req.getParameter("name"));
        String email = WebUtil.clean(req.getParameter("email")).toLowerCase();
        if (name.isBlank() || email.isBlank()) throw new IllegalArgumentException("Nombre y correo son obligatorios.");
        users.update(WebUtil.businessId(req), WebUtil.longParam(req, "userId"), name, email,
                WebUtil.clean(req.getParameter("role")), req.getParameter("active") != null,
                WebUtil.userId(req));
        WebUtil.flash(req, "success", "Usuario actualizado.");
    }

    private void savePermissions(HttpServletRequest req) throws Exception {
        long businessId = WebUtil.businessId(req);
        long targetUserId = WebUtil.longParam(req, "userId");
        List<CustomModule> businessModules = modules.list(businessId);
        for (CustomModule module : businessModules) {
            boolean view = checked(req, "view_" + module.id());
            boolean create = view && checked(req, "create_" + module.id());
            boolean edit = view && checked(req, "edit_" + module.id());
            boolean delete = view && checked(req, "delete_" + module.id());
            boolean manage = view && checked(req, "manage_" + module.id());
            permissions.save(businessId, targetUserId, module.id(), view, create, edit, delete, manage);
        }
        WebUtil.flash(req, "success", "Permisos actualizados.");
    }

    private boolean checked(HttpServletRequest req, String name) { return req.getParameter(name) != null; }

    private void requireOwner(HttpServletRequest req) throws Exception {
        if (!permissions.isOwner(WebUtil.userId(req), WebUtil.businessId(req)))
            throw new SecurityException("Solo un propietario puede administrar usuarios y permisos.");
    }
}
