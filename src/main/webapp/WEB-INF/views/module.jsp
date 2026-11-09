<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title><c:out value="${module.name}"/> | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/module-types.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
</head>
<body data-module-id="${module.id}">
<jsp:include page="fragments/topbar.jsp"><jsp:param name="activeNav" value="dashboard"/></jsp:include>
<main class="container">
    <jsp:include page="fragments/flash.jsp"/>
    <a class="back" href="${pageContext.request.contextPath}/app/dashboard">← Panel principal</a>
    <section class="module-header">
        <div><span class="module-type-chip table">TABLA DE DATOS</span><h1><c:out value="${module.name}"/></h1><p><c:out value="${empty module.description ? 'Sin descripción' : module.description}"/></p></div>
        <div class="actions">
            <c:if test="${canManage}"><button type="button" class="secondary" data-open="edit-module">Editar tabla</button><button type="button" class="primary" data-open="new-column">+ Columna</button></c:if>
            <c:if test="${canCreate}"><button type="button" class="primary" data-open="new-row">+ Fila</button></c:if>
        </div>
    </section>

    <section class="management-toolbar">
        <div class="view-tabs">
            <a class="view-tab ${empty selectedView ? 'active' : ''}" href="${pageContext.request.contextPath}/app/modules/${module.id}">Todos</a>
            <c:forEach items="${savedViews}" var="view">
                <a class="view-tab ${not empty selectedView && selectedView.id == view.id ? 'active' : ''}" href="${pageContext.request.contextPath}/app/modules/${module.id}?viewId=${view.id}"><c:out value="${view.name}"/></a>
                <c:if test="${not empty selectedView && selectedView.id == view.id && not empty view.userId}"><form method="post" action="${pageContext.request.contextPath}/app/views"><input type="hidden" name="moduleId" value="${module.id}"><input type="hidden" name="viewId" value="${view.id}"><button class="tiny danger" name="action" value="delete" data-confirm="¿Eliminar esta vista?">×</button></form></c:if>
            </c:forEach>
        </div>
        <form method="get" action="${pageContext.request.contextPath}/app/modules/${module.id}" class="toolbar-main">
            <label class="toolbar-search"><input name="q" value="${fn:escapeXml(searchQuery)}" placeholder="Buscar en la tabla"></label>
            <div class="toolbar-group">
                <select class="toolbar-select" name="filterColumnId"><option value="">Sin filtro</option><c:forEach items="${columns}" var="col"><option value="${col.id}" ${param.filterColumnId == col.id ? 'selected' : ''}><c:out value="${col.name}"/></option></c:forEach></select>
                <select class="toolbar-select" name="filterOperator"><option value="CONTAINS">contiene</option><option value="EQ" ${param.filterOperator == 'EQ' ? 'selected' : ''}>es igual a</option><option value="NE" ${param.filterOperator == 'NE' ? 'selected' : ''}>es distinto de</option><option value="GT" ${param.filterOperator == 'GT' ? 'selected' : ''}>mayor que</option><option value="GTE" ${param.filterOperator == 'GTE' ? 'selected' : ''}>mayor o igual</option><option value="LT" ${param.filterOperator == 'LT' ? 'selected' : ''}>menor que</option><option value="LTE" ${param.filterOperator == 'LTE' ? 'selected' : ''}>menor o igual</option><option value="EMPTY" ${param.filterOperator == 'EMPTY' ? 'selected' : ''}>está vacío</option><option value="NOT_EMPTY" ${param.filterOperator == 'NOT_EMPTY' ? 'selected' : ''}>no está vacío</option></select>
                <input name="filterValue" value="${fn:escapeXml(param.filterValue)}" placeholder="Valor">
                <select class="toolbar-select" name="sortColumnId"><option value="">Sin orden</option><c:forEach items="${columns}" var="col"><option value="${col.id}" ${sortColumnId == col.id ? 'selected' : ''}><c:out value="${col.name}"/></option></c:forEach></select>
                <select class="toolbar-select" name="sortDirection"><option value="ASC" ${sortDirection == 'ASC' ? 'selected' : ''}>Ascendente</option><option value="DESC" ${sortDirection == 'DESC' ? 'selected' : ''}>Descendente</option></select>
                <button class="secondary" type="submit">Aplicar</button>
            </div>
        </form>
        <div class="toolbar-main">
            <span class="report-caption">Mostrando <strong><c:out value="${fn:length(rows)}"/></strong> de <strong><c:out value="${totalRows}"/></strong> filas</span>
            <div class="toolbar-group">
                <button type="button" class="secondary" data-open="columns-visibility">Mostrar columnas</button>
                <button type="button" class="secondary" data-open="save-view">Guardar vista</button>
                <a class="secondary" href="${pageContext.request.contextPath}/app/export?moduleId=${module.id}&format=csv">Exportar CSV</a>
                <a class="secondary" href="${pageContext.request.contextPath}/app/export?moduleId=${module.id}&format=xlsx">Exportar Excel</a>
                <c:if test="${canCreate}"><button type="button" class="secondary" data-open="import-data">Importar</button></c:if>
            </div>
        </div>
    </section>

    <section class="table-wrap data-grid" data-data-grid>
        <table><thead><tr>
            <th class="system-id-column" data-grid-column="id"><div><span>ID</span><span class="system-badge">FIJO</span></div><small>AUTOMÁTICO</small></th>
            <c:forEach items="${columns}" var="col"><th data-grid-column="${col.id}"><div><span><c:out value="${col.name}"/><c:if test="${col.requiredField}"> *</c:if></span><c:if test="${canManage}"><button type="button" class="tiny" data-open="col-${col.id}">•••</button></c:if></div><small><c:out value="${col.dataType}"/><c:if test="${col.readOnly}"> · CALCULADO</c:if></small></th></c:forEach>
            <c:if test="${canEdit || canDelete}"><th class="row-actions">Acciones</th></c:if>
        </tr></thead><tbody>
        <c:forEach items="${rows}" var="row"><tr>
            <td class="system-id-value" data-grid-column="id"><c:out value="${row.id}"/></td>
            <c:forEach items="${columns}" var="col"><c:set var="v" value="${row.cells[col.id]}"/><td data-grid-column="${col.id}">
                <c:choose>
                    <c:when test="${col.readOnly || !canEdit}"><span class="readonly-value"><c:out value="${v}"/></span></c:when>
                    <c:when test="${col.relation}"><select form="row-${row.id}" name="col_${col.id}" ${col.requiredField ? 'required' : ''}><option value="">Selecciona</option><c:forEach items="${relationOptions[col.id]}" var="option"><option value="${option.id}" ${v == option.id ? 'selected' : ''}><c:out value="${option.label}"/></option></c:forEach></select></c:when>
                    <c:when test="${col.dataType == 'BOOLEAN'}"><select form="row-${row.id}" name="col_${col.id}" ${col.requiredField ? 'required' : ''}><option value=""></option><option value="true" ${v == 'true' ? 'selected' : ''}>Sí</option><option value="false" ${v == 'false' ? 'selected' : ''}>No</option></select></c:when>
                    <c:when test="${col.dataType == 'SELECT'}"><select form="row-${row.id}" name="col_${col.id}" ${col.requiredField ? 'required' : ''}><option value=""></option><c:forEach items="${col.options}" var="opt"><option value="${fn:escapeXml(opt)}" ${v == opt ? 'selected' : ''}><c:out value="${opt}"/></option></c:forEach></select></c:when>
                    <c:otherwise><input form="row-${row.id}" name="col_${col.id}" value="${fn:escapeXml(v)}" type="${col.dataType == 'NUMBER' ? 'number' : col.dataType == 'DATE' ? 'date' : 'text'}" ${col.dataType == 'NUMBER' ? 'step=any' : ''} ${col.requiredField ? 'required' : ''}></c:otherwise>
                </c:choose>
            </td></c:forEach>
            <c:if test="${canEdit || canDelete}"><td><c:if test="${canEdit}"><button type="submit" form="row-${row.id}" class="tiny save" name="action" value="update">Guardar</button></c:if> <c:if test="${canDelete}"><button type="submit" form="row-${row.id}" class="tiny danger" name="action" value="delete" data-confirm="¿Eliminar esta fila? Quedará registrada en el historial.">Eliminar</button></c:if></td></c:if>
        </tr></c:forEach>
        <c:if test="${empty rows}"><tr><td colspan="${fn:length(columns) + 2}" class="no-rows">No hay filas que coincidan. El ID se asigna automáticamente al crear una.</td></tr></c:if>
        </tbody></table>
    </section>
    <div hidden><c:forEach items="${rows}" var="row"><form id="row-${row.id}" method="post" action="${pageContext.request.contextPath}/app/rows"><input type="hidden" name="moduleId" value="${module.id}"><input type="hidden" name="rowId" value="${row.id}"></form></c:forEach></div>
</main>

<dialog id="columns-visibility"><form method="dialog"><div class="dialog-head"><h2>Columnas visibles</h2><button type="button" class="icon-btn" data-close>×</button></div><p class="dialog-note">Esta preferencia se guarda en este navegador.</p><label class="switch-control"><input type="checkbox" data-column-visibility="id" checked disabled> ID automático</label><c:forEach items="${columns}" var="col"><label class="switch-control"><input type="checkbox" data-column-visibility="${col.id}" checked> <c:out value="${col.name}"/></label></c:forEach><div class="actions"><button class="primary" value="close">Listo</button></div></form></dialog>

<dialog id="save-view"><form method="post" action="${pageContext.request.contextPath}/app/views"><input type="hidden" name="moduleId" value="${module.id}"><div class="dialog-head"><h2>Guardar vista</h2><button type="button" class="icon-btn" data-close>×</button></div><label>Nombre<input name="name" required maxlength="100"></label><input type="hidden" name="filterColumnId" value="${fn:escapeXml(param.filterColumnId)}"><input type="hidden" name="filterOperator" value="${fn:escapeXml(param.filterOperator)}"><input type="hidden" name="filterValue" value="${fn:escapeXml(param.filterValue)}"><input type="hidden" name="sortColumnId" value="${sortColumnId}"><input type="hidden" name="sortDirection" value="${empty sortDirection ? 'ASC' : sortDirection}"><label class="switch-control"><input type="checkbox" name="defaultView"> Usar como vista predeterminada</label><div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button class="primary">Guardar</button></div></form></dialog>

<c:if test="${canCreate}"><dialog id="import-data"><form method="post" enctype="multipart/form-data" action="${pageContext.request.contextPath}/app/import"><input type="hidden" name="moduleId" value="${module.id}"><div class="dialog-head"><h2>Importar datos</h2><button type="button" class="icon-btn" data-close>×</button></div><div class="import-dropzone"><label><strong>Selecciona un CSV o Excel (.xlsx)</strong><span>La primera fila debe contener nombres de columnas. Máximo 20,000 filas y 10 MB.</span><input type="file" name="file" accept=".csv,.xlsx" required></label></div><p class="dialog-note">La importación es transaccional: si una fila falla, no se guarda ninguna.</p><div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button class="primary">Importar</button></div></form></dialog></c:if>

<c:if test="${canCreate}"><dialog id="new-row"><form method="post" action="${pageContext.request.contextPath}/app/rows"><input type="hidden" name="moduleId" value="${module.id}"><div class="dialog-head"><h2>Nueva fila</h2><button type="button" class="icon-btn" data-close>×</button></div><p class="dialog-note">El ID se asignará automáticamente.</p><c:forEach items="${columns}" var="col"><c:if test="${!col.readOnly}"><label><c:out value="${col.name}"/><c:if test="${col.requiredField}"> *</c:if><c:choose><c:when test="${col.relation}"><select name="col_${col.id}" ${col.requiredField ? 'required' : ''}><option value="">Selecciona</option><c:forEach items="${relationOptions[col.id]}" var="option"><option value="${option.id}"><c:out value="${option.label}"/></option></c:forEach></select></c:when><c:when test="${col.dataType == 'BOOLEAN'}"><select name="col_${col.id}" ${col.requiredField ? 'required' : ''}><option value=""></option><option value="true">Sí</option><option value="false">No</option></select></c:when><c:when test="${col.dataType == 'SELECT'}"><select name="col_${col.id}" ${col.requiredField ? 'required' : ''}><option value=""></option><c:forEach items="${col.options}" var="opt"><option value="${fn:escapeXml(opt)}"><c:out value="${opt}"/></option></c:forEach></select></c:when><c:otherwise><input name="col_${col.id}" value="${fn:escapeXml(col.defaultValue)}" type="${col.dataType == 'NUMBER' ? 'number' : col.dataType == 'DATE' ? 'date' : 'text'}" ${col.dataType == 'NUMBER' ? 'step=any' : ''} ${col.requiredField ? 'required' : ''}></c:otherwise></c:choose></label></c:if></c:forEach><div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button class="primary">Guardar fila</button></div></form></dialog></c:if>

<c:if test="${canManage}">
<dialog id="edit-module"><form method="post" action="${pageContext.request.contextPath}/app/modules/${module.id}"><div class="dialog-head"><h2>Editar tabla</h2><button type="button" class="icon-btn" data-close>×</button></div><label>Nombre<input name="name" value="${fn:escapeXml(module.name)}" required></label><label>Descripción<textarea name="description"><c:out value="${module.description}"/></textarea></label><c:if test="${canDelete}"><div class="danger-zone"><button class="danger-button" name="action" value="delete" data-confirm="¿Eliminar la tabla, sus columnas y sus filas?">Eliminar tabla</button></div></c:if><div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button class="primary" name="action" value="update">Guardar</button></div></form></dialog>

<dialog id="new-column"><form method="post" action="${pageContext.request.contextPath}/app/columns" data-column-config><input type="hidden" name="moduleId" value="${module.id}"><div class="dialog-head"><h2>Nueva columna</h2><button type="button" class="icon-btn" data-close>×</button></div><p class="dialog-note">ID ya existe y no se puede editar.</p><label>Nombre<input name="name" required maxlength="100"></label><label>Tipo<select name="dataType" data-type-select><option value="TEXT">Texto</option><option value="NUMBER">Número</option><option value="DATE">Fecha</option><option value="BOOLEAN">Sí / No</option><option value="SELECT">Lista de opciones</option><option value="RELATION">Relación con otra tabla</option><option value="LOOKUP">Dato heredado de relación</option><option value="FORMULA">Fórmula numérica</option></select></label><jsp:include page="fragments/column-config-fields.jsp"/><div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button class="primary">Agregar columna</button></div></form></dialog>

<c:forEach items="${columns}" var="col"><dialog id="col-${col.id}"><form method="post" action="${pageContext.request.contextPath}/app/columns" data-column-config><input type="hidden" name="moduleId" value="${module.id}"><input type="hidden" name="columnId" value="${col.id}"><div class="dialog-head"><h2>Editar columna</h2><button type="button" class="icon-btn" data-close>×</button></div><label>Nombre<input name="name" value="${fn:escapeXml(col.name)}" required></label><label>Tipo<select name="dataType" data-type-select><c:forEach items="${['TEXT','NUMBER','DATE','BOOLEAN','SELECT','RELATION','LOOKUP','FORMULA']}" var="type"><option value="${type}" ${col.dataType == type ? 'selected' : ''}><c:out value="${type}"/></option></c:forEach></select></label><c:set var="editingColumn" value="${col}" scope="request"/><jsp:include page="fragments/column-config-fields.jsp"/><div class="danger-zone"><button class="danger-button" name="action" value="delete" data-confirm="¿Eliminar esta columna y sus valores?">Eliminar columna</button></div><div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button class="primary" name="action" value="update">Guardar</button></div></form></dialog></c:forEach>
</c:if>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</body></html>
