<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<header class="topbar management-topbar">
    <a class="brand" href="${pageContext.request.contextPath}/app/dashboard">MiGestor</a>

    <nav class="app-navigation" aria-label="Navegación principal">
        <a class="nav-link ${param.activeNav == 'dashboard' ? 'active' : ''}"
           href="${pageContext.request.contextPath}/app/dashboard"
           ${param.activeNav == 'dashboard' ? 'aria-current="page"' : ''}>Panel</a>
        <a class="nav-link ${param.activeNav == 'sales' ? 'active' : ''}"
           href="${pageContext.request.contextPath}/app/sales"
           ${param.activeNav == 'sales' ? 'aria-current="page"' : ''}>Ventas completas</a>
        <c:if test="${sessionScope.userRole == 'OWNER' || sessionScope.userRole == 'MANAGER'}">
            <a class="nav-link ${param.activeNav == 'productAssets' ? 'active' : ''}"
               href="${pageContext.request.contextPath}/app/product-assets"
               ${param.activeNav == 'productAssets' ? 'aria-current="page"' : ''}>Códigos y fotos</a>
        </c:if>
        <c:if test="${sessionScope.userRole == 'OWNER' || sessionScope.userRole == 'MANAGER'}">
            <a class="nav-link ${param.activeNav == 'cash' ? 'active' : ''}"
               href="${pageContext.request.contextPath}/app/cash"
               ${param.activeNav == 'cash' ? 'aria-current="page"' : ''}>Caja y gastos</a>
        </c:if>
        <c:if test="${sessionScope.userRole == 'OWNER' || sessionScope.userRole == 'MANAGER'}">
            <a class="nav-link ${param.activeNav == 'workflows' ? 'active' : ''}"
               href="${pageContext.request.contextPath}/app/workflows"
               ${param.activeNav == 'workflows' ? 'aria-current="page"' : ''}>Flujos</a>
        </c:if>
        <a class="nav-link ${param.activeNav == 'reports' ? 'active' : ''}"
           href="${pageContext.request.contextPath}/app/reports"
           ${param.activeNav == 'reports' ? 'aria-current="page"' : ''}>Reportes</a>
        <a class="nav-link ${param.activeNav == 'alerts' ? 'active' : ''}"
           href="${pageContext.request.contextPath}/app/alerts"
           ${param.activeNav == 'alerts' ? 'aria-current="page"' : ''}>Alertas</a>
        <c:if test="${sessionScope.userRole == 'OWNER'}">
            <a class="nav-link ${param.activeNav == 'administration' ? 'active' : ''}"
               href="${pageContext.request.contextPath}/app/settings/users"
               ${param.activeNav == 'administration' ? 'aria-current="page"' : ''}>Administración</a>
        </c:if>
    </nav>

    <div class="topbar-account">
        <div class="topbar-identity">
            <strong class="topbar-business"><c:out value="${sessionScope.businessName}"/></strong>
            <span class="topbar-user-name"><c:out value="${sessionScope.userName}"/></span>
        </div>
        <a class="link-button" href="${pageContext.request.contextPath}/logout">Salir</a>
    </div>
</header>
