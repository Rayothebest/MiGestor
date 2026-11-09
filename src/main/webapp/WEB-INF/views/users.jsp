<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Administración | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/module-types.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp">
    <jsp:param name="activeNav" value="administration"/>
</jsp:include>

<main class="container">
    <jsp:include page="fragments/flash.jsp"/>

    <section class="hero">
        <div>
            <span class="eyebrow">ADMINISTRACIÓN</span>
            <h1>Usuarios y permisos</h1>
            <p>Controla quién puede consultar o modificar cada módulo de este negocio.</p>
        </div>
        <button type="button" class="primary" data-open="new-user">+ Nuevo usuario</button>
    </section>

    <section class="permission-card">
        <div class="section-title">
            <h2>Equipo</h2>
            <p><c:out value="${fn:length(users)}"/> usuario(s) pertenecen a este negocio.</p>
        </div>
        <div class="permission-table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Usuario</th>
                    <th>Rol</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${users}" var="user">
                    <tr>
                        <td>
                            <div class="user-summary">
                                <span class="user-avatar"><c:out value="${fn:toUpperCase(fn:substring(user.name, 0, 1))}"/></span>
                                <span>
                                    <strong><c:out value="${user.name}"/> <c:if test="${user.id == sessionScope.userId}">(tú)</c:if></strong>
                                    <small><c:out value="${user.email}"/></small>
                                </span>
                            </div>
                        </td>
                        <td><span class="role-badge ${fn:toLowerCase(user.role)}"><c:out value="${user.role == 'OWNER' ? 'Propietario' : user.role == 'MANAGER' ? 'Gerente' : 'Empleado'}"/></span></td>
                        <td><span class="status-badge ${user.active ? 'active' : 'inactive'}"><c:out value="${user.active ? 'ACTIVO' : 'INACTIVO'}"/></span></td>
                        <td>
                            <div class="actions">
                                <button type="button" class="tiny" data-open="edit-user-${user.id}">Editar</button>
                                <c:if test="${!user.owner}">
                                    <button type="button" class="tiny" data-open="permissions-${user.id}">Permisos</button>
                                </c:if>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty users}">
                    <tr><td class="no-rows" colspan="4">No hay usuarios disponibles.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </section>

    <section class="management-toolbar" style="margin-top:20px">
        <div class="toolbar-main">
            <div>
                <strong>Historial de actividad</strong>
                <p class="configuration-help" style="margin:3px 0 0">Consulta los cambios realizados por los usuarios del negocio.</p>
            </div>
            <a class="secondary" href="${pageContext.request.contextPath}/app/history">Ver historial</a>
        </div>
    </section>
</main>

<dialog id="new-user">
    <form method="post" action="${pageContext.request.contextPath}/app/settings/users">
        <input type="hidden" name="action" value="create">
        <div class="dialog-head">
            <h2>Nuevo usuario</h2>
            <button type="button" class="icon-btn" data-close aria-label="Cerrar">×</button>
        </div>
        <p class="dialog-note">Comparte la contraseña inicial de forma segura. Esta versión no envía invitaciones por correo.</p>
        <label>Nombre
            <input name="name" required maxlength="100">
        </label>
        <label>Correo
            <input type="email" name="email" required maxlength="190" autocomplete="off">
        </label>
        <label>Contraseña inicial
            <input type="password" name="password" required minlength="8" maxlength="100" autocomplete="new-password">
        </label>
        <label>Rol
            <select name="role" required>
                <option value="EMPLOYEE">Empleado</option>
                <option value="MANAGER">Gerente</option>
                <option value="OWNER">Propietario</option>
            </select>
        </label>
        <div class="actions">
            <button type="button" class="secondary" data-close>Cancelar</button>
            <button type="submit" class="primary">Crear usuario</button>
        </div>
    </form>
</dialog>

<c:forEach items="${users}" var="user">
    <dialog id="edit-user-${user.id}">
        <form method="post" action="${pageContext.request.contextPath}/app/settings/users">
            <input type="hidden" name="action" value="update">
            <input type="hidden" name="userId" value="${user.id}">
            <div class="dialog-head">
                <h2>Editar usuario</h2>
                <button type="button" class="icon-btn" data-close aria-label="Cerrar">×</button>
            </div>
            <label>Nombre
                <input name="name" value="${fn:escapeXml(user.name)}" required maxlength="100">
            </label>
            <label>Correo
                <input type="email" name="email" value="${fn:escapeXml(user.email)}" required maxlength="190">
            </label>
            <label>Rol
                <select name="role" required ${user.id == sessionScope.userId ? 'disabled' : ''}>
                    <option value="EMPLOYEE" ${user.role == 'EMPLOYEE' ? 'selected' : ''}>Empleado</option>
                    <option value="MANAGER" ${user.role == 'MANAGER' ? 'selected' : ''}>Gerente</option>
                    <option value="OWNER" ${user.role == 'OWNER' ? 'selected' : ''}>Propietario</option>
                </select>
            </label>
            <c:if test="${user.id == sessionScope.userId}">
                <input type="hidden" name="role" value="${user.role}">
            </c:if>
            <label class="switch-control">
                <input type="checkbox" name="active" value="true" ${user.active ? 'checked' : ''} ${user.id == sessionScope.userId ? 'disabled' : ''}>
                Usuario activo
            </label>
            <c:if test="${user.id == sessionScope.userId}">
                <input type="hidden" name="active" value="true">
                <p class="dialog-note">No puedes desactivar tu propia cuenta ni cambiar su rol desde esta pantalla.</p>
            </c:if>
            <div class="actions">
                <button type="button" class="secondary" data-close>Cancelar</button>
                <button type="submit" class="primary">Guardar cambios</button>
            </div>
        </form>
    </dialog>

    <c:if test="${!user.owner}">
        <dialog id="permissions-${user.id}">
            <form method="post" action="${pageContext.request.contextPath}/app/settings/users">
                <input type="hidden" name="action" value="permission">
                <input type="hidden" name="userId" value="${user.id}">
                <div class="dialog-head">
                    <h2>Permisos de <c:out value="${user.name}"/></h2>
                    <button type="button" class="icon-btn" data-close aria-label="Cerrar">×</button>
                </div>
                <p class="dialog-note">Los permisos se aplican por módulo. Ver es necesario para usar las demás acciones.</p>
                <c:set var="userPermissions" value="${permissionMatrix[user.id]}"/>
                <div class="permission-matrix">
                    <c:forEach items="${modules}" var="module">
                        <c:set var="permission" value="${userPermissions[module.id]}"/>
                        <section class="configuration-card">
                            <strong><c:out value="${module.name}"/></strong>
                            <input type="hidden" name="moduleId" value="${module.id}">
                            <div class="toolbar-group" style="margin-top:10px">
                                <label class="switch-control"><input type="checkbox" name="view_${module.id}" value="true" ${permission.canView ? 'checked' : ''}> Ver</label>
                                <label class="switch-control"><input type="checkbox" name="create_${module.id}" value="true" ${permission.canCreate ? 'checked' : ''}> Crear</label>
                                <label class="switch-control"><input type="checkbox" name="edit_${module.id}" value="true" ${permission.canEdit ? 'checked' : ''}> Editar</label>
                                <label class="switch-control"><input type="checkbox" name="delete_${module.id}" value="true" ${permission.canDelete ? 'checked' : ''}> Eliminar</label>
                                <label class="switch-control"><input type="checkbox" name="manage_${module.id}" value="true" ${permission.canManage ? 'checked' : ''}> Configurar</label>
                            </div>
                        </section>
                    </c:forEach>
                    <c:if test="${empty modules}">
                        <p class="inline-notice">Todavía no existen módulos a los cuales asignar permisos.</p>
                    </c:if>
                </div>
                <div class="actions">
                    <button type="button" class="secondary" data-close>Cancelar</button>
                    <button type="submit" class="primary">Guardar permisos</button>
                </div>
            </form>
        </dialog>
    </c:if>
</c:forEach>

<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</body>
</html>
