<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es"><head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title><c:out value="${module.name}"/> | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/module-types.css?v=20261028">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
</head><body>
<jsp:include page="fragments/topbar.jsp"><jsp:param name="activeNav" value="dashboard"/></jsp:include>
<main class="container">
    <jsp:include page="fragments/flash.jsp"/>
    <a class="back" href="${pageContext.request.contextPath}/app/dashboard">← Panel principal</a>
    <section class="module-header">
        <div><span class="module-type-chip panel"><c:out value="${module.adjustPanel ? 'PANEL DE EXISTENCIAS' : module.registerPanel ? 'PANEL DE REGISTRO' : 'PANEL POR CONFIGURAR'}"/></span><h1><c:out value="${module.name}"/></h1><p><c:out value="${empty module.description ? 'Sin descripción' : module.description}"/></p></div>
        <div class="actions"><c:if test="${canManage}"><button type="button" class="secondary" data-open="configure-panel">Configurar Panel</button><button type="button" class="secondary" data-open="edit-module">Editar nombre</button></c:if></div>
    </section>

    <c:choose>
        <c:when test="${empty linkedModule}">
            <section class="empty"><span>!</span><h3>Falta una tabla vinculada</h3><p>Configura el Panel para poder registrar operaciones.</p><c:if test="${canManage}"><button class="primary" data-open="configure-panel">Configurar ahora</button></c:if></section>
        </c:when>
        <c:otherwise>
            <section class="linked-source"><div><strong>Tabla vinculada: <c:out value="${linkedModule.name}"/></strong><span><c:out value="${module.adjustPanel ? 'La operación cambia una existencia y conserva el artículo, el movimiento y su historial.' : 'Cada envío crea una nueva fila con ID automático.'}"/></span></div><a class="secondary" href="${pageContext.request.contextPath}/app/modules/${linkedModule.id}">Abrir tabla</a></section>

            <c:choose>
                <c:when test="${module.adjustPanel}">
                    <section class="capture-card"><div class="capture-card-header"><h2>Registrar movimiento</h2><p><c:out value="${module.adjustmentOperation == 'ADD' ? 'La cantidad se sumará a ' : module.adjustmentOperation == 'SET' ? 'La cantidad reemplazará ' : 'La cantidad se restará de '}"/><strong><c:out value="${module.adjustmentColumnName}"/></strong>.</p></div>
                        <c:choose><c:when test="${not empty rows}"><form class="capture-form" method="post" action="${pageContext.request.contextPath}/app/rows"><input type="hidden" name="moduleId" value="${module.id}"><input type="hidden" name="operationToken" value="${operationToken}">
                            <label>Artículo o registro<select name="rowId" required><option value="">Selecciona</option><c:forEach items="${adjustmentOptions}" var="option"><option value="${option.id}"><c:out value="${option.label}"/></option></c:forEach></select></label>
                            <label>Cantidad<input type="number" step="any" min="0.0000001" name="quantity" required></label><label>Nota (opcional)<textarea name="note" maxlength="255" placeholder="Ej. Venta mostrador, recepción de proveedor..."></textarea></label>
                            <p class="automatic-id-note"><strong>Operación segura</strong> No se elimina la fila y un doble clic no descuenta dos veces.</p>
                            <div class="actions"><c:if test="${canEdit}"><button class="primary">Confirmar movimiento</button></c:if><c:if test="${!canEdit}"><span class="inline-notice">No tienes permiso para ejecutar este Panel.</span></c:if></div>
                        </form></c:when><c:otherwise><p class="inline-notice">La tabla aún no tiene registros.</p></c:otherwise></c:choose>
                    </section>
                </c:when>
                <c:when test="${module.registerPanel}">
                    <section class="capture-card"><div class="capture-card-header"><h2>Nuevo registro</h2><p>Completa los campos configurados. La tabla asignará el ID.</p></div>
                        <form class="capture-form" method="post" action="${pageContext.request.contextPath}/app/rows"><input type="hidden" name="moduleId" value="${module.id}"><div class="automatic-id-note"><strong>ID automático</strong> Se asigna al guardar.</div>
                        <c:forEach items="${visiblePanelFields}" var="field"><label><c:out value="${field.name}"/><c:if test="${field.requiredField}"> *</c:if><c:choose>
                            <c:when test="${field.relation}"><select name="col_${field.columnId}" ${field.requiredField ? 'required' : ''}><option value="">Selecciona</option><c:forEach items="${relationOptions[field.columnId]}" var="option"><option value="${option.id}"><c:out value="${option.label}"/></option></c:forEach></select></c:when>
                            <c:when test="${field.dataType == 'BOOLEAN'}"><select name="col_${field.columnId}" ${field.requiredField ? 'required' : ''}><option value=""></option><option value="true">Sí</option><option value="false">No</option></select></c:when>
                            <c:when test="${field.dataType == 'SELECT'}"><select name="col_${field.columnId}" ${field.requiredField ? 'required' : ''}><option value=""></option><c:forEach items="${field.options}" var="option"><option value="${fn:escapeXml(option)}"><c:out value="${option}"/></option></c:forEach></select></c:when>
                            <c:otherwise><input name="col_${field.columnId}" value="${fn:escapeXml(field.defaultValue)}" type="${field.dataType == 'NUMBER' ? 'number' : field.dataType == 'DATE' ? 'date' : 'text'}" ${field.dataType == 'NUMBER' ? 'step=any' : ''} ${field.requiredField ? 'required' : ''}></c:otherwise>
                        </c:choose></label></c:forEach>
                        <c:if test="${empty visiblePanelFields}"><p class="inline-notice">No hay campos visibles. Configura el formulario antes de usarlo.</p></c:if>
                        <div class="actions"><c:if test="${canCreate && not empty visiblePanelFields}"><button class="primary">Guardar registro</button></c:if><c:if test="${!canCreate}"><span class="inline-notice">No tienes permiso para ejecutar este Panel.</span></c:if></div></form>
                    </section>
                </c:when>
                <c:otherwise><section class="empty"><span>!</span><h3>Este Panel usa el modo anterior</h3><p>Por seguridad ya no elimina artículos. Cámbialo a “Ajustar existencias” y elige una columna numérica.</p><c:if test="${canManage}"><button class="primary" data-open="configure-panel">Actualizar configuración</button></c:if></section></c:otherwise>
            </c:choose>
        </c:otherwise>
    </c:choose>
</main>

<c:if test="${canManage}">
<dialog id="configure-panel" class="wide-dialog"><div class="dialog-head"><h2>Configurar Panel</h2><button type="button" class="icon-btn" data-close>×</button></div>
    <div class="configuration-layout">
        <form class="configuration-card" method="post" action="${pageContext.request.contextPath}/app/panels/config" data-panel-settings><input type="hidden" name="moduleId" value="${module.id}"><input type="hidden" name="action" value="settings"><h2>1. Operación</h2><p class="configuration-help">Elige dónde se guardan los datos y qué hará este Panel.</p>
            <label>Tabla vinculada<select name="linkedModuleId" required data-panel-table><c:forEach items="${tableModules}" var="table"><option value="${table.id}" ${module.linkedModuleId == table.id ? 'selected' : ''}><c:out value="${table.name}"/></option></c:forEach></select></label>
            <label>Función<select name="panelAction" data-panel-operation><option value="REGISTER" ${module.registerPanel ? 'selected' : ''}>Crear un registro</option><option value="ADJUST" ${module.adjustPanel || module.removePanel ? 'selected' : ''}>Ajustar existencias</option></select></label>
            <div data-adjust-settings>
                <label>Columna numérica de existencias<select name="adjustmentColumnId" data-adjustment-column><option value="">Selecciona una columna</option><c:forEach items="${tableModules}" var="table"><c:forEach items="${columnsByModule[table.id]}" var="candidate"><c:if test="${candidate.dataType == 'NUMBER'}"><option value="${candidate.id}" data-adjustment-table="${table.id}" ${module.adjustmentColumnId == candidate.id ? 'selected' : ''}><c:out value="${table.name}"/> · <c:out value="${candidate.name}"/></option></c:if></c:forEach></c:forEach></select></label>
                <label>Operación<select name="adjustmentOperation"><option value="SUBTRACT" ${module.adjustmentOperation == 'SUBTRACT' ? 'selected' : ''}>Restar (venta/salida)</option><option value="ADD" ${module.adjustmentOperation == 'ADD' ? 'selected' : ''}>Sumar (entrada)</option><option value="SET" ${module.adjustmentOperation == 'SET' ? 'selected' : ''}>Establecer cantidad</option></select></label>
                <label class="switch-control"><input type="checkbox" name="allowNegative" ${module.allowNegative ? 'checked' : ''}> Permitir existencias negativas</label>
            </div>
            <div class="actions"><button class="primary">Guardar operación</button></div>
        </form>
        <form class="configuration-card" method="post" action="${pageContext.request.contextPath}/app/panels/config"><input type="hidden" name="moduleId" value="${module.id}"><input type="hidden" name="action" value="fields"><h2>2. Formulario</h2><p class="configuration-help">Disponible para paneles de registro. Define qué campos verá el usuario.</p>
            <div class="field-configuration-list"><c:forEach items="${panelFields}" var="field" varStatus="loop"><div class="field-configuration-row"><input type="hidden" name="position_${field.columnId}" value="${loop.index}"><label class="switch-control"><input type="checkbox" name="visible_${field.columnId}" ${field.visible ? 'checked' : ''}> Visible</label><span class="field-name"><strong><c:out value="${field.name}"/></strong><small><c:out value="${field.dataType}"/></small></span><label class="switch-control"><input type="checkbox" name="required_${field.columnId}" ${field.requiredField ? 'checked' : ''}> Obligatorio</label><label class="field-default"><small>Predeterminado</small><input name="default_${field.columnId}" value="${fn:escapeXml(field.defaultValue)}"></label></div></c:forEach></div>
            <c:if test="${empty panelFields}"><p class="inline-notice">La tabla no tiene campos configurables.</p></c:if><div class="actions"><button class="primary">Guardar formulario</button></div>
        </form>
    </div>
</dialog>

<dialog id="edit-module"><form method="post" action="${pageContext.request.contextPath}/app/modules/${module.id}"><div class="dialog-head"><h2>Editar Panel</h2><button type="button" class="icon-btn" data-close>×</button></div><label>Nombre<input name="name" value="${fn:escapeXml(module.name)}" required></label><label>Descripción<textarea name="description"><c:out value="${module.description}"/></textarea></label><c:if test="${canDelete}"><div class="danger-zone"><button class="danger-button" name="action" value="delete" data-confirm="¿Eliminar este Panel? La tabla vinculada se conservará.">Eliminar Panel</button></div></c:if><div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button class="primary" name="action" value="update">Guardar</button></div></form></dialog>
</c:if>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</body></html>
