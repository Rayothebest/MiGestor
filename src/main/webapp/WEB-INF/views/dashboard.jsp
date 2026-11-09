<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Panel | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/module-types.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp"><jsp:param name="activeNav" value="dashboard"/></jsp:include>
<main class="container">
    <jsp:include page="fragments/flash.jsp"/>
    <section class="hero">
        <div><span class="eyebrow">PANEL PRINCIPAL</span><h1>Hola, <c:out value="${sessionScope.userName}"/></h1><p>Organiza la información, registra operaciones y vigila lo importante de tu negocio.</p></div>
        <c:if test="${canCreateModules}"><button type="button" class="primary" data-open="new-module">+ Nuevo módulo</button></c:if>
    </section>
    <section class="stats">
        <article><strong><c:out value="${moduleCount}"/></strong><span>Módulos visibles</span></article>
        <article><strong><c:out value="${rowCount}"/></strong><span>Filas registradas</span></article>
        <article><strong><c:out value="${alertCount}"/></strong><span>Alertas activas</span></article>
    </section>
    <div class="section-title"><h2>Tus módulos</h2><p>Las tablas almacenan datos; los paneles registran operaciones o ajustan existencias sin borrar el artículo.</p></div>
    <section class="module-grid">
        <c:forEach items="${modules}" var="module">
            <article class="module-card">
                <div class="module-card-top">
                    <span class="module-icon"><c:out value="${module.panel ? '✎' : '▦'}"/></span>
                    <div class="module-card-tools"><span class="module-kind ${module.panel ? 'panel' : 'table'}"><c:out value="${module.panel ? 'PANEL' : 'TABLA'}"/></span><c:if test="${canCreateModules}"><button type="button" class="tiny card-edit-button" data-open="edit-card-${module.id}">Editar</button></c:if></div>
                </div>
                <a class="module-title-link" href="${pageContext.request.contextPath}/app/modules/${module.id}"><h3><c:out value="${module.name}"/></h3></a>
                <p><c:out value="${empty module.description ? 'Sin descripción' : module.description}"/></p>
                <c:if test="${module.panel}">
                    <span class="linked-label"><c:out value="${module.adjustPanel ? 'Ajusta:' : module.registerPanel ? 'Guarda en:' : 'Requiere configuración:'}"/> <strong><c:out value="${empty module.linkedModuleName ? 'Tabla no disponible' : module.linkedModuleName}"/></strong></span>
                    <span class="panel-operation ${module.adjustPanel ? 'adjust' : 'register'}"><c:out value="${module.adjustPanel ? 'AJUSTE DE EXISTENCIAS' : module.registerPanel ? 'REGISTRO' : 'MODO ANTERIOR'}"/></span>
                </c:if>
                <a class="open-label" href="${pageContext.request.contextPath}/app/modules/${module.id}">Abrir →</a>
            </article>
        </c:forEach>
        <c:if test="${empty modules}">
            <div class="empty"><span>▦</span><h3>No hay módulos disponibles</h3><c:choose><c:when test="${canCreateModules}"><p>Crea primero una tabla de datos para comenzar.</p><button type="button" class="primary" data-open="new-module">Crear primer módulo</button></c:when><c:otherwise><p>Pide al propietario acceso a un módulo.</p></c:otherwise></c:choose></div>
        </c:if>
    </section>
    <c:if test="${sessionScope.userRole == 'OWNER'}">
        <section class="management-toolbar" style="margin-top:24px"><div class="toolbar-main"><div><strong>Exportar datos del negocio</strong><p class="configuration-help" style="margin:3px 0 0">Descarga un ZIP con tus tablas, ventas, devoluciones, Caja, códigos y fotos. No sustituye un respaldo SQL.</p></div><a class="secondary" href="${pageContext.request.contextPath}/app/backup">Descargar ZIP</a></div></section>
    </c:if>
</main>

<c:if test="${canCreateModules}">
<dialog id="new-module">
    <form method="post" action="${pageContext.request.contextPath}/app/modules/create" data-module-form>
        <div class="dialog-head"><h2>Nuevo módulo</h2><button type="button" class="icon-btn" data-close>×</button></div>
        <fieldset class="module-type-fieldset">
            <legend>¿Qué tipo de módulo será?</legend>
            <label class="type-option"><input type="radio" name="moduleType" value="TABLE" checked data-module-type><span class="type-option-body"><strong>▦ Tabla de datos</strong><small>Guarda información en columnas y filas, como Inventario o Clientes.</small></span></label>
            <label class="type-option ${empty tableModules ? 'disabled' : ''}"><input type="radio" name="moduleType" value="PANEL" data-module-type ${empty tableModules ? 'disabled' : ''}><span class="type-option-body"><strong>✎ Panel de operación</strong><small>Usa una tabla existente para capturar registros o cambiar existencias.</small></span></label>
        </fieldset>
        <c:if test="${empty tableModules}"><p class="inline-notice">Primero crea una Tabla; después podrás vincularle un Panel.</p></c:if>
        <div class="linked-module-field" data-linked-module-field hidden>
            <label>Tabla vinculada<select name="linkedModuleId" data-linked-module-select disabled><option value="">Selecciona una tabla</option><c:forEach items="${tableModules}" var="table"><option value="${table.id}"><c:out value="${table.name}"/></option></c:forEach></select></label>
            <fieldset class="panel-action-fieldset"><legend>¿Qué hará este Panel?</legend>
                <label class="type-option"><input type="radio" name="panelAction" value="REGISTER" checked data-panel-action disabled><span class="type-option-body"><strong>＋ Registrar información</strong><small>Crea una nueva fila en la tabla vinculada.</small></span></label>
                <label class="type-option"><input type="radio" name="panelAction" value="ADJUST" data-panel-action disabled><span class="type-option-body"><strong>⇅ Ajustar existencias</strong><small>Elige un artículo y suma, resta o establece una cantidad, conservando el artículo y el historial.</small></span></label>
            </fieldset>
            <p class="inline-notice">Después de crear un panel de existencias elegirás la columna numérica y la operación.</p>
        </div>
        <label>Nombre<input name="name" required maxlength="100" placeholder="Ej. Registrar venta"></label>
        <label>Descripción<textarea name="description" maxlength="255" placeholder="¿Para qué se usará este módulo?"></textarea></label>
        <div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button type="submit" class="primary">Crear módulo</button></div>
    </form>
</dialog>
<c:forEach items="${modules}" var="module">
    <dialog id="edit-card-${module.id}"><form method="post" action="${pageContext.request.contextPath}/app/modules/${module.id}">
        <div class="dialog-head"><h2>Editar módulo</h2><button type="button" class="icon-btn" data-close>×</button></div>
        <div class="module-readonly-info"><div><small>Tipo</small><strong><c:out value="${module.panel ? 'Panel' : 'Tabla de datos'}"/></strong></div><c:if test="${module.panel}"><div><small>Operación</small><strong><c:out value="${module.adjustPanel ? 'Ajustar existencias' : module.registerPanel ? 'Registrar' : 'Configurar'}"/></strong></div></c:if></div>
        <label>Nombre<input name="name" value="${fn:escapeXml(module.name)}" required maxlength="100"></label>
        <label>Descripción<textarea name="description" maxlength="255"><c:out value="${module.description}"/></textarea></label>
        <div class="danger-zone"><button type="submit" class="danger-button" name="action" value="delete" data-confirm="¿Eliminar este módulo? Los datos asociados se eliminarán cuando corresponda.">Eliminar módulo</button></div>
        <div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button type="submit" class="primary" name="action" value="update">Guardar</button></div>
    </form></dialog>
</c:forEach>
</c:if>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</body>
</html>
