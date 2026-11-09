<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Historial | MiGestor</title>
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
    <a class="back" href="${pageContext.request.contextPath}/app/settings/users">← Usuarios y permisos</a>

    <section class="hero">
        <div>
            <span class="eyebrow">HISTORIAL</span>
            <h1>Actividad del negocio</h1>
            <p>Registro de operaciones realizadas por los usuarios en los módulos.</p>
        </div>
        <span class="status-badge active"><c:out value="${fn:length(events)}"/> EVENTOS</span>
    </section>

    <section class="permission-card">
        <div class="permission-table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Fecha</th>
                    <th>Usuario</th>
                    <th>Acción</th>
                    <th>Módulo y registro</th>
                    <th>Detalle</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${events}" var="event">
                    <tr>
                        <td><c:out value="${event.createdAt}"/></td>
                        <td><c:out value="${empty event.userName ? 'Sistema' : event.userName}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${event.action == 'CREATE' || event.action == 'CREATED'}"><span class="status-badge active">CREÓ</span></c:when>
                                <c:when test="${event.action == 'DELETE' || event.action == 'DELETED'}"><span class="status-badge warning">ELIMINÓ</span></c:when>
                                <c:when test="${event.action == 'UPDATE' || event.action == 'UPDATED'}"><span class="permission-badge edit">EDITÓ</span></c:when>
                                <c:otherwise><span class="permission-badge view"><c:out value="${event.action}"/></span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <strong><c:out value="${empty event.moduleName ? 'General' : event.moduleName}"/></strong>
                            <c:if test="${not empty event.rowId}"><small class="report-caption"> · Fila #<c:out value="${event.rowId}"/></small></c:if>
                        </td>
                        <td><c:out value="${event.details}"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty events}">
                    <tr>
                        <td class="no-rows" colspan="5">Todavía no hay actividad registrada para mostrar.</td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </section>
</main>

<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</body>
</html>
