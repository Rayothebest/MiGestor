<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Flujos | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/workflows.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp"><jsp:param name="activeNav" value="workflows"/></jsp:include>
<main class="container workflows-page">
    <jsp:include page="fragments/flash.jsp"/>

    <section class="hero workflows-hero">
        <div>
            <span class="eyebrow">AUTOMATIZACIÓN</span>
            <h1>Flujos</h1>
            <p>Cuando se guarda una fila, una venta o una devolución, una regla puede crear un registro o ajustar existencias en una tabla vinculada.</p>
        </div>
        <c:if test="${not empty workflowTables}"><a class="primary" href="${pageContext.request.contextPath}/app/workflows#workflow-builder">+ Nuevo flujo</a></c:if>
    </section>

    <section class="workflow-explainer" aria-label="Cómo funcionan los flujos">
        <div><span class="workflow-step">1</span><strong>Se crea un dato</strong><p>Una fila nueva, una venta completa o una devolución guardada.</p></div>
        <div><span class="workflow-step">2</span><strong>Se verifica la regla</strong><p>En filas nuevas puedes exigir que una columna tenga un valor exacto, por ejemplo Estado = Recibida.</p></div>
        <div><span class="workflow-step">3</span><strong>Se ejecuta una acción</strong><p>Crear otra fila o sumar/restar existencias usando una relación entre tablas.</p></div>
    </section>

    <section class="workflow-list" aria-labelledby="workflow-list-title">
        <div class="section-title">
            <h2 id="workflow-list-title">Reglas configuradas</h2>
            <p>Una regla pausada conserva su configuración y deja de actuar sobre nuevos registros.</p>
        </div>
        <c:choose>
            <c:when test="${empty workflowRules}">
                <div class="empty workflow-empty"><span>↗</span><h3>Todavía no hay flujos</h3>
                    <p>Crea el primero para conectar dos tablas o guardar un resumen de cada venta completa.</p></div>
            </c:when>
            <c:otherwise>
                <div class="workflow-rule-grid">
                    <c:forEach items="${workflowRules}" var="rule">
                        <article class="workflow-rule-card ${rule.active ? '' : 'is-paused'}">
                            <div class="workflow-rule-top">
                                <span class="workflow-status ${rule.active ? 'is-active' : 'is-inactive'}"><c:out value="${rule.active ? 'Activo' : 'Pausado'}"/></span>
                                <small>Flujo #<c:out value="${rule.id}"/></small>
                            </div>
                            <h3><c:out value="${rule.name}"/></h3>
                            <p class="workflow-rule-detail"><strong>Cuando:</strong>
                                <c:choose>
                                    <c:when test="${rule.triggerType == 'SALE_CREATED'}">se guarda una venta completa</c:when>
                                    <c:when test="${rule.triggerType == 'RETURN_CREATED'}">se guarda una devolución de venta</c:when>
                                    <c:otherwise>se crea una fila en <c:out value="${rule.sourceModuleName}"/></c:otherwise>
                                </c:choose>
                            </p>
                            <c:if test="${not empty rule.conditionColumnId}">
                                <p class="workflow-rule-detail"><strong>Condición:</strong>
                                    <c:forEach items="${workflowColumns[rule.sourceModuleId]}" var="column"><c:if test="${column.id == rule.conditionColumnId}"><c:out value="${column.name}"/> = </c:if></c:forEach>
                                    “<c:out value="${rule.conditionValue}"/>”.</p>
                            </c:if>
                            <p class="workflow-rule-detail"><strong>Entonces:</strong>
                                <c:out value="${rule.actionType == 'ADJUST_STOCK' ? 'ajustar existencias en' : 'crear una fila en'}"/>
                                <c:out value="${rule.targetModuleName}"/>
                            </p>
                            <div class="workflow-rule-actions">
                                <a class="secondary" href="${pageContext.request.contextPath}/app/workflows?edit=${rule.id}#workflow-builder">Editar</a>
                                <form method="post" action="${pageContext.request.contextPath}/app/workflows">
                                    <input type="hidden" name="workflowCsrfToken" value="${workflowCsrfToken}">
                                    <input type="hidden" name="action" value="toggle">
                                    <input type="hidden" name="ruleId" value="${rule.id}">
                                    <input type="hidden" name="active" value="${not rule.active}">
                                    <button class="secondary" type="submit"><c:out value="${rule.active ? 'Pausar' : 'Activar'}"/></button>
                                </form>
                                <form method="post" action="${pageContext.request.contextPath}/app/workflows">
                                    <input type="hidden" name="workflowCsrfToken" value="${workflowCsrfToken}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="ruleId" value="${rule.id}">
                                    <button class="tiny danger" type="submit" data-confirm="¿Eliminar este flujo? Los registros ya creados no se borrarán.">Eliminar</button>
                                </form>
                            </div>
                        </article>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

    <section id="workflow-builder" class="workflow-builder" aria-labelledby="workflow-builder-title">
        <div class="workflow-builder-heading">
            <div><span class="eyebrow"><c:out value="${empty editWorkflow ? 'NUEVA REGLA' : 'EDITAR REGLA'}"/></span>
                <h2 id="workflow-builder-title"><c:out value="${empty editWorkflow ? 'Construye un flujo' : editWorkflow.name}"/></h2>
                <p>Elige el evento, la tabla de destino y los datos que se usarán. Las reglas se ejecutan una vez por evento guardado.</p>
            </div>
            <c:if test="${not empty editWorkflow}"><a class="secondary" href="${pageContext.request.contextPath}/app/workflows#workflow-builder">Crear otro flujo</a></c:if>
        </div>

        <c:choose><c:when test="${empty workflowTables}">
            <div class="empty"><span>▦</span><h3>Primero crea una tabla</h3>
                <p>Los flujos necesitan al menos una tabla para guardar datos o ajustar existencias.</p>
                <a class="primary" href="${pageContext.request.contextPath}/app/dashboard">Ir al panel principal</a></div>
        </c:when><c:otherwise>
        <form method="post" action="${pageContext.request.contextPath}/app/workflows" data-workflow-form>
            <input type="hidden" name="workflowCsrfToken" value="${workflowCsrfToken}">
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="ruleId" value="${empty editWorkflow ? '0' : editWorkflow.id}">

            <section class="workflow-form-section" aria-labelledby="wf-general-title">
                <div class="workflow-section-head"><span class="workflow-step">1</span><div><h3 id="wf-general-title">Nombre y estado</h3><p>El nombre sólo sirve para reconocer la regla en esta página.</p></div></div>
                <div class="workflow-fields">
                    <label>Nombre del flujo
                        <input name="name" maxlength="100" required placeholder="Ej. Al recibir compra, sumar existencias" value="<c:out value='${editWorkflow.name}'/>">
                    </label>
                    <label class="workflow-checkbox"><input type="checkbox" name="active" ${empty editWorkflow || editWorkflow.active ? 'checked' : ''}><span>Activar al guardar</span></label>
                </div>
            </section>

            <section class="workflow-form-section" aria-labelledby="wf-trigger-title">
                <div class="workflow-section-head"><span class="workflow-step">2</span><div><h3 id="wf-trigger-title">¿Cuándo debe ejecutarse?</h3><p>El evento de origen determina qué datos estarán disponibles para la acción.</p></div></div>
                <div class="workflow-fields">
                    <label>Evento
                        <select name="triggerType" data-wf-trigger required>
                            <option value="ROW_CREATED" ${editWorkflow.triggerType == 'ROW_CREATED' ? 'selected' : ''}>Al crear una fila</option>
                            <option value="SALE_CREATED" ${editWorkflow.triggerType == 'SALE_CREATED' ? 'selected' : ''}>Al guardar una venta completa</option>
                            <option value="RETURN_CREATED" ${editWorkflow.triggerType == 'RETURN_CREATED' ? 'selected' : ''}>Al guardar una devolución</option>
                        </select>
                    </label>
                </div>
                <div class="workflow-subsection" data-wf-row-trigger>
                    <label>Tabla de origen
                        <select name="sourceModuleId" data-wf-source-module required>
                            <option value="">Selecciona una tabla</option>
                            <c:forEach items="${workflowTables}" var="table"><option value="${table.id}" ${editWorkflow.sourceModuleId == table.id ? 'selected' : ''}><c:out value="${table.name}"/></option></c:forEach>
                        </select>
                    </label>
                    <p class="workflow-field-help">Se activa al crear una fila directamente o desde cualquier panel de registro vinculado a esta tabla. Editar filas, ajustar desde un panel o importar CSV no activa este flujo.</p>
                    <div class="workflow-condition">
                        <strong>Condición opcional</strong>
                        <p>Úsala para actuar sólo cuando la fila nueva ya tenga un valor concreto. La coincidencia es exacta después de quitar espacios al principio y al final.</p>
                        <div class="workflow-field-pair">
                            <label>Columna a comprobar
                                <select name="conditionColumnId" data-wf-condition-column>
                                    <option value="">Sin condición</option>
                                    <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${not column.systemId && not column.readOnly}">
                                        <option value="${column.id}" data-wf-module="${table.id}" ${editWorkflow.conditionColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/> · <c:out value="${column.dataType}"/></option>
                                    </c:if></c:forEach></c:forEach>
                                </select>
                            </label>
                            <label>Valor igual a
                                <input name="conditionValue" data-wf-condition-value maxlength="255" placeholder="Ej. Recibida" value="<c:out value='${editWorkflow.conditionValue}'/>">
                            </label>
                        </div>
                    </div>
                </div>
                <div class="workflow-subsection workflow-event-note" data-wf-sale-trigger hidden>
                    <strong>Venta completa guardada</strong>
                    <p>Se activa con ventas de “Ventas completas”. Puedes asignar el número de venta, total o método de pago a columnas de otra tabla. No incluye datos por artículo.</p>
                </div>
                <div class="workflow-subsection workflow-event-note" data-wf-return-trigger hidden>
                    <strong>Devolución guardada</strong>
                    <p>Se activa al registrar una devolución parcial o completa. La venta repone existencias automáticamente; el flujo puede crear un asiento en otra tabla con el ID de devolución, venta original, importe y método registrado. No procesa dinero externo.</p>
                </div>
            </section>

            <section class="workflow-form-section" aria-labelledby="wf-action-title">
                <div class="workflow-section-head"><span class="workflow-step">3</span><div><h3 id="wf-action-title">¿Qué debe hacer?</h3><p>Elige una sola acción. Si la acción falla, el evento de origen no se guarda.</p></div></div>
                <div class="workflow-field-pair">
                    <label>Acción
                        <select name="actionType" data-wf-action required>
                            <option value="ADJUST_STOCK" ${editWorkflow.actionType == 'ADJUST_STOCK' ? 'selected' : ''}>Sumar o restar existencias</option>
                            <option value="CREATE_ROW" ${editWorkflow.actionType == 'CREATE_ROW' ? 'selected' : ''}>Crear una fila en otra tabla</option>
                        </select>
                    </label>
                    <label>Tabla de destino
                        <select name="targetModuleId" data-wf-target-module required>
                            <option value="">Selecciona una tabla</option>
                            <c:forEach items="${workflowTables}" var="table"><option value="${table.id}" ${editWorkflow.targetModuleId == table.id ? 'selected' : ''}><c:out value="${table.name}"/></option></c:forEach>
                        </select>
                    </label>
                </div>

                <div class="workflow-subsection" data-wf-adjust-action>
                    <p class="workflow-field-help">La fila de origen debe tener una columna de relación que apunte al registro de destino. Se toma la cantidad numérica de esa fila y se suma o resta a la columna de existencias elegida.</p>
                    <div class="workflow-field-pair">
                        <label>Artículo vinculado
                            <select name="relationColumnId" data-wf-relation required>
                                <option value="">Selecciona una relación</option>
                                <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${column.dataType == 'RELATION'}">
                                    <option value="${column.id}" data-wf-module="${table.id}" data-wf-related-module="${column.relatedModuleId}" ${editWorkflow.relationColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/></option>
                                </c:if></c:forEach></c:forEach>
                            </select>
                        </label>
                        <label>Cantidad en la fila de origen
                            <select name="quantityColumnId" data-wf-quantity required>
                                <option value="">Selecciona una columna numérica</option>
                                <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${column.dataType == 'NUMBER' && not column.systemId && not column.readOnly}">
                                    <option value="${column.id}" data-wf-module="${table.id}" ${editWorkflow.quantityColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/></option>
                                </c:if></c:forEach></c:forEach>
                            </select>
                        </label>
                        <label>Columna de existencias en destino
                            <select name="stockColumnId" data-wf-stock required>
                                <option value="">Selecciona una columna numérica</option>
                                <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${column.dataType == 'NUMBER' && not column.systemId && not column.readOnly}">
                                    <option value="${column.id}" data-wf-module="${table.id}" ${editWorkflow.stockColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/></option>
                                </c:if></c:forEach></c:forEach>
                            </select>
                        </label>
                        <label>Operación
                            <select name="operation" data-wf-operation required>
                                <option value="ADD" ${editWorkflow.operation == 'ADD' ? 'selected' : ''}>Sumar cantidad</option>
                                <option value="SUBTRACT" ${editWorkflow.operation == 'SUBTRACT' ? 'selected' : ''}>Restar cantidad</option>
                            </select>
                        </label>
                    </div>
                    <p class="workflow-example">Ejemplo: al crear una recepción con Estado = Recibida, tomar Artículo + Cantidad y sumar esa cantidad a Existencias del artículo.</p>
                </div>

                <div class="workflow-subsection" data-wf-create-action hidden>
                    <p class="workflow-field-help">Asigna columnas de la tabla de destino con valores de la fila de origen, datos de la venta o devolución, o un valor fijo. Las columnas obligatorias sin valor predeterminado deben quedar cubiertas.</p>
                    <div class="workflow-mappings-heading"><strong>Asignaciones de columnas</strong><button class="secondary" type="button" data-wf-add-mapping>+ Agregar asignación</button></div>
                    <div class="workflow-mappings" data-wf-mappings>
                        <c:forEach items="${editWorkflow.mappings}" var="mapping">
                            <div class="workflow-mapping" data-wf-mapping>
                                <label>Columna de destino
                                    <select name="mappingTargetColumnId" data-wf-mapping-target required>
                                        <option value="">Selecciona una columna</option>
                                        <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${not column.systemId && not column.readOnly}">
                                            <option value="${column.id}" data-wf-module="${table.id}" data-wf-type="${column.dataType}" data-wf-options="<c:out value='${column.optionsText}'/>" ${mapping.targetColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/> · <c:out value="${column.dataType}"/></option>
                                        </c:if></c:forEach></c:forEach>
                                    </select>
                                </label>
                                <label>Tomar valor de
                                    <select name="mappingSourceType" data-wf-mapping-type required>
                                        <option value="">Selecciona el origen</option>
                                        <option value="SOURCE_COLUMN" ${mapping.sourceType == 'SOURCE_COLUMN' ? 'selected' : ''}>Columna de la fila nueva</option>
                                        <option value="SALE_ID" ${mapping.sourceType == 'SALE_ID' ? 'selected' : ''}>Número de venta</option>
                                        <option value="SALE_TOTAL" ${mapping.sourceType == 'SALE_TOTAL' ? 'selected' : ''}>Total de venta</option>
                                        <option value="SALE_PAYMENT" ${mapping.sourceType == 'SALE_PAYMENT' ? 'selected' : ''}>Método de pago (código)</option>
                                        <option value="RETURN_ID" ${mapping.sourceType == 'RETURN_ID' ? 'selected' : ''}>Número de devolución</option>
                                        <option value="RETURN_TOTAL" ${mapping.sourceType == 'RETURN_TOTAL' ? 'selected' : ''}>Importe devuelto</option>
                                        <option value="RETURN_METHOD" ${mapping.sourceType == 'RETURN_METHOD' ? 'selected' : ''}>Método de reembolso (código)</option>
                                        <option value="LITERAL" ${mapping.sourceType == 'LITERAL' ? 'selected' : ''}>Valor fijo</option>
                                    </select>
                                </label>
                                <label data-wf-mapping-source-field>Columna de la fila nueva
                                    <select name="mappingSourceColumnId" data-wf-mapping-source>
                                        <option value="">Selecciona una columna</option>
                                        <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${not column.systemId && not column.readOnly}">
                                            <option value="${column.id}" data-wf-module="${table.id}" data-wf-type="${column.dataType}" ${mapping.sourceColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/> · <c:out value="${column.dataType}"/></option>
                                        </c:if></c:forEach></c:forEach>
                                    </select>
                                </label>
                                <label data-wf-mapping-literal-field>Valor fijo
                                    <input name="mappingLiteralValue" data-wf-mapping-literal maxlength="1000" placeholder="Ej. Pendiente" value="<c:out value='${mapping.literalValue}'/>">
                                </label>
                                <button class="tiny danger workflow-remove-mapping" type="button" data-wf-remove-mapping>Quitar</button>
                            </div>
                        </c:forEach>
                    </div>
                    <p class="workflow-example">Ejemplo de venta: número, total y método de pago → columnas de registro. Para una devolución usa su número, importe, método y número de venta original. Los métodos se guardan como CASH, CARD, TRANSFER u OTHER; si el destino es una lista, usa esas opciones.</p>
                </div>
            </section>

            <div class="workflow-submit">
                <p>Las acciones creadas por un flujo no activan otros flujos. Los datos anteriores no se modifican al crear, editar o pausar una regla.</p>
                <button type="submit" class="primary"><c:out value="${empty editWorkflow ? 'Crear flujo' : 'Guardar cambios'}"/></button>
            </div>
        </form>
        </c:otherwise></c:choose>
    </section>
</main>

<template id="workflow-mapping-template">
    <div class="workflow-mapping" data-wf-mapping>
        <label>Columna de destino
            <select name="mappingTargetColumnId" data-wf-mapping-target required>
                <option value="">Selecciona una columna</option>
                <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${not column.systemId && not column.readOnly}">
                    <option value="${column.id}" data-wf-module="${table.id}" data-wf-type="${column.dataType}" data-wf-options="<c:out value='${column.optionsText}'/>"><c:out value="${column.name}"/> · <c:out value="${column.dataType}"/></option>
                </c:if></c:forEach></c:forEach>
            </select>
        </label>
        <label>Tomar valor de
            <select name="mappingSourceType" data-wf-mapping-type required>
                <option value="">Selecciona el origen</option>
                <option value="SOURCE_COLUMN">Columna de la fila nueva</option>
                <option value="SALE_ID">Número de venta</option>
                <option value="SALE_TOTAL">Total de venta</option>
                <option value="SALE_PAYMENT">Método de pago (código)</option>
                <option value="RETURN_ID">Número de devolución</option>
                <option value="RETURN_TOTAL">Importe devuelto</option>
                <option value="RETURN_METHOD">Método de reembolso (código)</option>
                <option value="LITERAL">Valor fijo</option>
            </select>
        </label>
        <label data-wf-mapping-source-field>Columna de la fila nueva
            <select name="mappingSourceColumnId" data-wf-mapping-source><option value="">Selecciona una columna</option>
                <c:forEach items="${workflowTables}" var="table"><c:forEach items="${workflowColumns[table.id]}" var="column"><c:if test="${not column.systemId && not column.readOnly}">
                    <option value="${column.id}" data-wf-module="${table.id}" data-wf-type="${column.dataType}"><c:out value="${column.name}"/> · <c:out value="${column.dataType}"/></option>
                </c:if></c:forEach></c:forEach>
            </select>
        </label>
        <label data-wf-mapping-literal-field>Valor fijo
            <input name="mappingLiteralValue" data-wf-mapping-literal maxlength="1000" placeholder="Ej. Pendiente">
        </label>
        <button class="tiny danger workflow-remove-mapping" type="button" data-wf-remove-mapping>Quitar</button>
    </div>
</template>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/workflows.js"></script>
</body>
</html>
