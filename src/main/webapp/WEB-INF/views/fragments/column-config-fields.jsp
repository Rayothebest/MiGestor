<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<div class="column-config-section" data-config-section="SELECT">
    <label>Opciones separadas por comas<input name="options" value="${fn:escapeXml(editingColumn.optionsText)}" placeholder="Pendiente, En curso, Terminado"></label>
</div>
<div class="column-config-section" data-config-section="NUMBER">
    <label>Umbral de existencia baja (opcional)<input type="number" step="any" name="lowStockThreshold" value="${editingColumn.lowStockThreshold}"></label>
</div>
<div class="column-config-section" data-config-section="RELATION">
    <label>Tabla relacionada
        <select name="relatedModuleId" data-related-module>
            <option value="">Selecciona una tabla</option>
            <c:forEach items="${tableModules}" var="table"><c:if test="${table.id != module.id}"><option value="${table.id}" ${editingColumn.relatedModuleId == table.id ? 'selected' : ''}><c:out value="${table.name}"/></option></c:if></c:forEach>
        </select>
    </label>
    <label>Columna que se mostrará
        <select name="relatedDisplayColumnId" data-related-display>
            <option value="">Primer valor disponible</option>
            <c:forEach items="${tableModules}" var="table"><c:forEach items="${columnsByModule[table.id]}" var="targetColumn"><option value="${targetColumn.id}" data-related-table="${table.id}" ${editingColumn.relatedDisplayColumnId == targetColumn.id ? 'selected' : ''}><c:out value="${table.name}"/> · <c:out value="${targetColumn.name}"/></option></c:forEach></c:forEach>
        </select>
    </label>
</div>
<div class="column-config-section" data-config-section="LOOKUP">
    <label>Columna de relación
        <select name="relationColumnId" data-lookup-relation>
            <option value="">Selecciona una relación</option>
            <c:forEach items="${columns}" var="relation"><c:if test="${relation.relation && relation.id != editingColumn.id}"><option value="${relation.id}" ${editingColumn.relationColumnId == relation.id ? 'selected' : ''}><c:out value="${relation.name}"/></option></c:if></c:forEach>
        </select>
    </label>
    <label>Dato que se heredará
        <select name="sourceColumnId" data-lookup-source>
            <option value="">Selecciona un dato</option>
            <c:forEach items="${columns}" var="relation"><c:if test="${relation.relation}"><c:forEach items="${columnsByModule[relation.relatedModuleId]}" var="source"><option value="${source.id}" data-relation-column="${relation.id}" ${editingColumn.sourceColumnId == source.id ? 'selected' : ''}><c:out value="${relation.name}"/> → <c:out value="${source.name}"/></option></c:forEach></c:if></c:forEach>
        </select>
    </label>
    <p class="dialog-note">Este campo es calculado y no se escribe manualmente.</p>
</div>
<div class="column-config-section" data-config-section="FORMULA">
    <label>Expresión<input name="formulaExpression" value="${fn:escapeXml(editingColumn.formulaExpression)}" placeholder="Ej. {12} * {18}"></label>
    <p class="dialog-note">Usa el ID de cada columna numérica entre llaves. Columnas disponibles: <c:forEach items="${columns}" var="item"><c:if test="${item.dataType == 'NUMBER' || item.formula}"><strong>{<c:out value="${item.id}"/>}</strong> <c:out value="${item.name}"/> · </c:if></c:forEach></p>
</div>
<div class="column-config-common" data-config-common>
    <label class="switch-control"><input type="checkbox" name="requiredField" ${editingColumn.requiredField ? 'checked' : ''}> Campo obligatorio</label>
    <label>Valor predeterminado (opcional)<input name="defaultValue" value="${fn:escapeXml(editingColumn.defaultValue)}"></label>
</div>
