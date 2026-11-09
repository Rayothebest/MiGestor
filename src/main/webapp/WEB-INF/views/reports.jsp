<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Reportes | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/module-types.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/report_charts.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp">
    <jsp:param name="activeNav" value="reports"/>
</jsp:include>

<main class="container">
    <jsp:include page="fragments/flash.jsp"/>

    <section class="hero">
        <div>
            <span class="eyebrow">REPORTES</span>
            <h1>Entiende los datos de tu negocio</h1>
            <p>Consulta totales y distribuciones de cada tabla. Puedes guardar la configuración que uses con frecuencia.</p>
        </div>
        <c:if test="${not empty selectedModule}">
            <button type="button" class="primary" data-open="save-report">Guardar reporte</button>
        </c:if>
    </section>

    <form class="management-toolbar" method="get" action="${pageContext.request.contextPath}/app/reports">
        <div class="toolbar-main">
            <label style="min-width:280px;flex:1 1 420px">Tabla para analizar
                <select class="toolbar-select" name="moduleId" required>
                    <option value="">Selecciona una tabla</option>
                    <c:forEach items="${modules}" var="module">
                        <c:if test="${module.table}">
                            <option value="${module.id}" ${not empty selectedModule && selectedModule.id == module.id ? 'selected' : ''}>
                                <c:out value="${module.name}"/>
                            </option>
                        </c:if>
                    </c:forEach>
                </select>
            </label>
            <div class="toolbar-group">
                <button class="primary" type="submit">Generar reporte</button>
            </div>
        </div>
    </form>

    <c:choose>
        <c:when test="${empty modules}">
            <section class="empty">
                <span>▦</span>
                <h2>Primero crea una tabla</h2>
                <p>Los reportes se generan con las filas y columnas de tus tablas de datos.</p>
                <a class="primary" href="${pageContext.request.contextPath}/app/dashboard">Ir al panel principal</a>
            </section>
        </c:when>
        <c:when test="${empty selectedModule}">
            <section class="empty">
                <span>▥</span>
                <h2>Selecciona una tabla</h2>
                <p>Elige arriba la información que quieres resumir.</p>
            </section>
        </c:when>
        <c:otherwise>
            <section class="section-title">
                <span class="module-type-chip table">TABLA DE DATOS</span>
                <h2><c:out value="${selectedModule.name}"/></h2>
                <p><c:out value="${empty selectedModule.description ? 'Resumen actual de sus registros.' : selectedModule.description}"/></p>
            </section>

            <section class="report-grid" aria-label="Resumen general">
                <article class="report-card">
                    <span class="report-caption">Registros totales</span>
                    <strong class="report-value"><c:out value="${rowCount}"/></strong>
                    <span class="report-caption">Filas disponibles en esta tabla</span>
                </article>
                <c:forEach items="${numericSummaries}" var="summary">
                    <article class="report-card">
                        <span class="report-caption"><c:out value="${summary.name}"/></span>
                        <strong class="report-value"><c:out value="${summary.total}"/></strong>
                        <span class="report-caption">Total · Promedio <c:out value="${summary.average}"/></span>
                        <div class="alert-rule-meta">
                            <span>Mín. <strong><c:out value="${summary.min}"/></strong></span>
                            <span>Máx. <strong><c:out value="${summary.max}"/></strong></span>
                        </div>
                    </article>
                </c:forEach>
            </section>

            <c:if test="${empty numericSummaries}">
                <p class="inline-notice">Esta tabla todavía no tiene columnas numéricas con valores para resumir.</p>
            </c:if>

            <section class="configuration-card" style="margin-top:20px">
                <div class="section-title">
                    <h2>Distribución de registros</h2>
                    <p>Valores agrupados a partir de la información disponible.</p>
                </div>
                <c:choose>
                    <c:when test="${not empty groupSummaries}">
                        <div class="report-bars">
                            <c:forEach items="${groupSummaries}" var="group">
                                <div class="report-bar-row">
                                    <span class="report-bar-label" title="${fn:escapeXml(group.label)}"><c:out value="${empty group.label ? 'Sin valor' : group.label}"/></span>
                                    <span class="report-bar-track" aria-hidden="true">
                                        <span class="report-bar-fill" style="--bar-value: ${group.percent}%"></span>
                                    </span>
                                    <span class="report-bar-value"><c:out value="${group.count}"/> (<c:out value="${group.percent}"/>%)</span>
                                </div>
                            </c:forEach>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="empty">
                            <span>▥</span>
                            <h3>No hay valores para agrupar</h3>
                            <p>Agrega registros a la tabla para ver su distribución.</p>
                        </div>
                    </c:otherwise>
                </c:choose>
            </section>
        </c:otherwise>
    </c:choose>

    <section class="section-title" style="margin-top:38px">
        <h2>Reportes guardados</h2>
        <p>Configuraciones disponibles para volver a consultar.</p>
    </section>
    <section class="report-grid">
        <c:forEach items="${savedReports}" var="report">
            <article class="report-card ${report.reportType == 'CHART' ? 'has-chart' : ''}">
                <span class="status-badge active">
                    <c:choose>
                        <c:when test="${report.reportType == 'SUMMARY'}">RESUMEN</c:when>
                        <c:when test="${report.reportType == 'CHART'}">GRÁFICA</c:when>
                        <c:otherwise>TABLA</c:otherwise>
                    </c:choose>
                </span>
                <h3><c:out value="${report.name}"/></h3>
                <c:forEach items="${modules}" var="module">
                    <c:if test="${module.id == report.moduleId}">
                        <p class="report-caption">Tabla: <strong><c:out value="${module.name}"/></strong></p>
                    </c:if>
                </c:forEach>
                <p class="report-caption">Cálculo:
                    <c:choose>
                        <c:when test="${report.aggregateFunction == 'SUM'}">Suma</c:when>
                        <c:when test="${report.aggregateFunction == 'AVG'}">Promedio</c:when>
                        <c:when test="${report.aggregateFunction == 'MIN'}">Mínimo</c:when>
                        <c:when test="${report.aggregateFunction == 'MAX'}">Máximo</c:when>
                        <c:otherwise>Conteo de registros</c:otherwise>
                    </c:choose>
                </p>
                <c:if test="${report.reportType == 'CHART'}">
                    <c:set var="chart" value="${charts[report.id]}"/>
                    <c:choose>
                        <c:when test="${not empty chart.points}">
                            <div class="report-visual" data-report-chart data-chart-type="${chart.chartType}">
                                <p class="report-visual-subtitle">Agrupado por <strong><c:out value="${chart.groupName}"/></strong></p>
                                <div class="report-visual-plot" data-chart-plot role="img" aria-label="Gráfica del reporte ${fn:escapeXml(report.name)}"></div>
                                <c:if test="${not empty chart.note}"><p class="report-visual-note"><c:out value="${chart.note}"/></p></c:if>
                                <details class="report-visual-data">
                                    <summary>Ver cifras de la gráfica</summary>
                                    <div class="report-visual-table-wrap"><table>
                                        <thead><tr><th scope="col"><c:out value="${chart.groupName}"/></th><th scope="col">Valor</th></tr></thead>
                                        <tbody><c:forEach items="${chart.points}" var="point">
                                            <tr data-chart-point data-label="<c:out value='${point.label}'/>" data-value="${point.value}"><th scope="row"><c:out value="${point.label}"/></th><td><c:out value="${point.value}"/></td></tr>
                                        </c:forEach></tbody>
                                    </table></div>
                                </details>
                            </div>
                        </c:when>
                        <c:otherwise><p class="inline-notice">No hay datos compatibles para dibujar esta gráfica. <c:out value="${chart.note}"/></p></c:otherwise>
                    </c:choose>
                </c:if>
                <form method="post" action="${pageContext.request.contextPath}/app/reports">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="reportId" value="${report.id}">
                    <button class="tiny danger" type="submit" data-confirm="¿Eliminar este reporte guardado?">Eliminar</button>
                </form>
            </article>
        </c:forEach>
        <c:if test="${empty savedReports}">
            <div class="empty">
                <span>▥</span>
                <h3>No hay reportes guardados</h3>
                <p>Selecciona una tabla y guarda una configuración para encontrarla aquí.</p>
            </div>
        </c:if>
    </section>
</main>

<c:if test="${not empty selectedModule}">
    <dialog id="save-report">
        <form method="post" action="${pageContext.request.contextPath}/app/reports">
            <input type="hidden" name="action" value="create">
            <input type="hidden" name="moduleId" value="${selectedModule.id}">
            <div class="dialog-head">
                <h2>Guardar reporte</h2>
                <button type="button" class="icon-btn" data-close aria-label="Cerrar">×</button>
            </div>
            <label>Nombre
                <input name="name" required maxlength="120" placeholder="Ej. Resumen mensual de inventario">
            </label>
            <label>Presentación
                <select name="reportType">
                    <option value="SUMMARY">Resumen</option>
                    <option value="TABLE">Tabla</option>
                    <option value="CHART">Gráfica</option>
                </select>
            </label>
            <label>Cálculo
                <select name="aggregateFunction">
                    <option value="COUNT">Contar registros</option>
                    <option value="SUM">Sumar</option>
                    <option value="AVG">Promedio</option>
                    <option value="MIN">Mínimo</option>
                    <option value="MAX">Máximo</option>
                </select>
            </label>
            <label>Columna numérica (opcional)
                <select name="valueColumnId">
                    <option value="">Ninguna</option>
                    <c:forEach items="${columns}" var="column">
                        <c:if test="${column.dataType == 'NUMBER' || column.dataType == 'FORMULA'}">
                            <option value="${column.id}"><c:out value="${column.name}"/></option>
                        </c:if>
                    </c:forEach>
                </select>
            </label>
            <label>Agrupar por (opcional; se elige automáticamente si lo dejas vacío)
                <select name="groupByColumnId">
                    <option value="">Sin agrupación</option>
                    <c:forEach items="${columns}" var="column">
                        <c:if test="${not column.systemId}">
                            <option value="${column.id}"><c:out value="${column.name}"/></option>
                        </c:if>
                    </c:forEach>
                </select>
            </label>
            <label>Tipo de gráfica
                <select name="chartType">
                    <option value="BAR">Barras</option>
                    <option value="PIE">Circular (valores no negativos)</option>
                    <option value="LINE">Línea (requiere agrupar por fecha)</option>
                </select>
            </label>
            <div class="actions">
                <button type="button" class="secondary" data-close>Cancelar</button>
                <button type="submit" class="primary">Guardar reporte</button>
            </div>
        </form>
    </dialog>
</c:if>

<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/report_charts.js"></script>
</body>
</html>
