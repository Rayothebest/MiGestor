<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Alertas | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/module-types.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/scheduled_alerts.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp">
    <jsp:param name="activeNav" value="alerts"/>
</jsp:include>

<main class="container">
    <jsp:include page="fragments/flash.jsp"/>

    <section class="hero">
        <div>
            <span class="eyebrow">ALERTAS</span>
            <h1>Detecta registros que requieren atención</h1>
            <p>Define condiciones sobre columnas numéricas, fechas o valores y programa avisos dentro de MiGestor.</p>
        </div>
        <c:if test="${canManageAlerts && not empty alertOptions}">
            <button type="button" class="primary" data-open="new-alert">+ Nueva alerta</button>
        </c:if>
    </section>

    <section class="summary-grid" aria-label="Resumen de alertas">
        <article class="report-card">
            <span class="report-caption">Reglas configuradas</span>
            <strong class="report-value"><c:out value="${fn:length(rules)}"/></strong>
        </article>
        <article class="report-card">
            <span class="report-caption">Coincidencias actuales</span>
            <strong class="report-value"><c:out value="${fn:length(matches)}"/></strong>
        </article>
        <article class="report-card">
            <span class="report-caption">Avisos sin revisar</span>
            <strong class="report-value"><c:out value="${unreadNotificationCount}"/></strong>
            <span class="report-caption">Las reglas programadas se revisan mientras el servidor esté encendido.</span>
        </article>
    </section>

    <section id="avisos" class="scheduled-inbox" aria-labelledby="scheduled-inbox-title">
        <div class="section-title">
            <h2 id="scheduled-inbox-title">Avisos automáticos</h2>
            <p>Una coincidencia genera un aviso. Se actualiza sin duplicarse hasta que deja de cumplir la condición.</p>
        </div>
        <c:choose>
            <c:when test="${not empty notifications}">
                <div class="scheduled-notifications">
                    <c:forEach items="${notifications}" var="notification">
                        <article class="scheduled-notification ${notification.acknowledged ? 'is-read' : ''}">
                            <div class="scheduled-notification-head">
                                <div><strong><c:out value="${notification.ruleName}"/></strong>
                                    <span><c:out value="${notification.moduleName}"/> · Registro #<c:out value="${notification.rowId}"/></span></div>
                                <span class="status-badge ${notification.acknowledged ? 'inactive' : 'active'}"><c:out value="${notification.acknowledged ? 'VISTO' : 'NUEVO'}"/></span>
                            </div>
                            <p><c:out value="${notification.message}"/></p>
                            <div class="scheduled-notification-foot">
                                <small>Detectado: <c:out value="${notification.firstSeenText}"/> · Revisado: <c:out value="${notification.lastSeenText}"/></small>
                                <div class="actions">
                                    <a class="secondary" href="${pageContext.request.contextPath}/app/modules/${notification.moduleId}">Abrir tabla</a>
                                    <c:if test="${canManageAlerts && not notification.acknowledged}">
                                        <form method="post" action="${pageContext.request.contextPath}/app/alerts">
                                            <input type="hidden" name="alertCsrfToken" value="${alertCsrfToken}">
                                            <input type="hidden" name="action" value="acknowledge">
                                            <input type="hidden" name="notificationId" value="${notification.id}">
                                            <button type="submit" class="secondary">Marcar visto</button>
                                        </form>
                                    </c:if>
                                </div>
                            </div>
                        </article>
                    </c:forEach>
                </div>
            </c:when>
            <c:otherwise>
                <div class="scheduled-empty">Aún no hay avisos programados activos. Configura la frecuencia de una regla para empezar.</div>
            </c:otherwise>
        </c:choose>
    </section>

    <c:if test="${not empty matches}">
        <section class="alert-card warning" style="margin-top:20px">
            <div class="alert-card-head">
                <div>
                    <h2>Hay registros que coinciden con tus reglas</h2>
                    <p class="alert-description"><c:out value="${fn:length(matches)}"/> coincidencia(s) requieren revisión en las tablas correspondientes.</p>
                </div>
                <span class="alert-count"><c:out value="${fn:length(matches)}"/> activas</span>
            </div>
        </section>
    </c:if>

    <section class="section-title" style="margin-top:32px">
        <h2>Reglas</h2>
        <p>Puedes pausar una regla sin eliminar su configuración.</p>
    </section>

    <section class="alert-grid">
        <c:forEach items="${rules}" var="rule">
            <c:set var="schedule" value="${schedules[rule.id]}"/>
            <article class="alert-card ${rule.active ? 'warning' : ''}">
                <div class="alert-card-head">
                    <span class="status-badge ${rule.active ? 'active' : 'inactive'}"><c:out value="${rule.active ? 'ACTIVA' : 'PAUSADA'}"/></span>
                    <span class="report-caption">
                        <c:choose>
                            <c:when test="${rule.ruleType == 'NUMBER'}">Número</c:when>
                            <c:when test="${rule.ruleType == 'DATE'}">Fecha</c:when>
                            <c:otherwise>Valor</c:otherwise>
                        </c:choose>
                    </span>
                </div>
                <h3><c:out value="${rule.name}"/></h3>
                <p class="alert-description">
                    <c:forEach items="${alertOptions}" var="option">
                        <c:if test="${option.moduleId == rule.moduleId && option.columnId == rule.columnId}">
                            <strong><c:out value="${option.moduleName}"/></strong> · <c:out value="${option.columnName}"/>
                        </c:if>
                    </c:forEach>
                </p>
                <p class="alert-description">Condición: <strong>
                    <c:choose>
                        <c:when test="${rule.conditionOperator == 'LT'}">menor que</c:when>
                        <c:when test="${rule.conditionOperator == 'LTE'}">menor o igual que</c:when>
                        <c:when test="${rule.conditionOperator == 'GT'}">mayor que</c:when>
                        <c:when test="${rule.conditionOperator == 'GTE'}">mayor o igual que</c:when>
                        <c:when test="${rule.conditionOperator == 'EQ'}">igual a</c:when>
                        <c:when test="${rule.conditionOperator == 'NE'}">distinto de</c:when>
                        <c:when test="${rule.conditionOperator == 'BEFORE'}">fecha anterior a</c:when>
                        <c:when test="${rule.conditionOperator == 'AFTER'}">fecha posterior a</c:when>
                        <c:otherwise>contiene</c:otherwise>
                    </c:choose>
                    <c:out value="${rule.comparisonValue}"/>
                </strong></p>
                <c:if test="${not empty rule.messageTemplate}">
                    <p class="inline-notice"><c:out value="${rule.messageTemplate}"/></p>
                </c:if>
                <div class="scheduled-rule-state">
                    <strong>Revisión programada:</strong>
                    <c:choose>
                        <c:when test="${empty schedule}">Desactivada</c:when>
                        <c:when test="${schedule.intervalMinutes == 15}">Cada 15 minutos</c:when>
                        <c:when test="${schedule.intervalMinutes == 60}">Cada hora</c:when>
                        <c:otherwise>Una vez al día</c:otherwise>
                    </c:choose>
                    <c:if test="${not empty schedule}"><small>Última revisión: <c:out value="${schedule.lastRunText}"/></small></c:if>
                </div>
                <c:if test="${canManageAlerts}">
                    <form method="post" action="${pageContext.request.contextPath}/app/alerts" class="scheduled-rule-form">
                        <input type="hidden" name="alertCsrfToken" value="${alertCsrfToken}">
                        <input type="hidden" name="action" value="schedule">
                        <input type="hidden" name="ruleId" value="${rule.id}">
                        <label>Frecuencia de avisos
                            <select name="intervalMinutes">
                                <option value="0" ${empty schedule ? 'selected' : ''}>Sin programación</option>
                                <option value="15" ${not empty schedule && schedule.intervalMinutes == 15 ? 'selected' : ''}>Cada 15 minutos</option>
                                <option value="60" ${not empty schedule && schedule.intervalMinutes == 60 ? 'selected' : ''}>Cada hora</option>
                                <option value="1440" ${not empty schedule && schedule.intervalMinutes == 1440 ? 'selected' : ''}>Una vez al día</option>
                            </select>
                        </label>
                        <button type="submit" class="secondary">Guardar frecuencia</button>
                    </form>
                </c:if>
                <c:if test="${canManageAlerts}"><div class="actions" style="margin-top:15px">
                    <c:if test="${not empty schedule && rule.active}">
                        <form method="post" action="${pageContext.request.contextPath}/app/alerts">
                            <input type="hidden" name="alertCsrfToken" value="${alertCsrfToken}">
                            <input type="hidden" name="action" value="runNow">
                            <input type="hidden" name="ruleId" value="${rule.id}">
                            <button class="secondary" type="submit">Comprobar ahora</button>
                        </form>
                    </c:if>
                    <form method="post" action="${pageContext.request.contextPath}/app/alerts">
                        <input type="hidden" name="alertCsrfToken" value="${alertCsrfToken}">
                        <input type="hidden" name="action" value="toggle">
                        <input type="hidden" name="ruleId" value="${rule.id}">
                        <button class="secondary" type="submit"><c:out value="${rule.active ? 'Pausar' : 'Activar'}"/></button>
                    </form>
                    <form method="post" action="${pageContext.request.contextPath}/app/alerts">
                        <input type="hidden" name="alertCsrfToken" value="${alertCsrfToken}">
                        <input type="hidden" name="action" value="delete">
                        <input type="hidden" name="ruleId" value="${rule.id}">
                        <button class="tiny danger" type="submit" data-confirm="¿Eliminar esta regla de alerta?">Eliminar</button>
                    </form>
                </div></c:if>
            </article>
        </c:forEach>

        <c:if test="${empty rules}">
            <div class="empty">
                <span>!</span>
                <h3>No hay reglas de alerta</h3>
                <p>Configura una condición para localizar existencias bajas, fechas próximas u otros valores importantes.</p>
                <c:if test="${empty alertOptions}">
                    <a class="primary" href="${pageContext.request.contextPath}/app/dashboard">Configurar una tabla</a>
                </c:if>
            </div>
        </c:if>
    </section>
</main>

<c:if test="${canManageAlerts && not empty alertOptions}">
    <dialog id="new-alert">
        <form method="post" action="${pageContext.request.contextPath}/app/alerts">
            <input type="hidden" name="alertCsrfToken" value="${alertCsrfToken}">
            <input type="hidden" name="action" value="create">
            <div class="dialog-head">
                <h2>Nueva alerta</h2>
                <button type="button" class="icon-btn" data-close aria-label="Cerrar">×</button>
            </div>
            <label>Nombre
                <input name="name" required maxlength="120" placeholder="Ej. Existencias bajas">
            </label>
            <label>Columna a evaluar
                <select name="columnId" required>
                    <option value="">Selecciona una columna</option>
                    <c:forEach items="${alertOptions}" var="option">
                        <option value="${option.columnId}"><c:out value="${option.moduleName}"/> · <c:out value="${option.columnName}"/> (<c:out value="${option.dataType == 'NUMBER' ? 'Número' : option.dataType == 'DATE' ? 'Fecha' : 'Valor'}"/>)</option>
                    </c:forEach>
                </select>
            </label>
            <label>Condición
                <select name="conditionOperator" required>
                    <option value="LT">Menor que</option>
                    <option value="LTE">Menor o igual que</option>
                    <option value="GT">Mayor que</option>
                    <option value="GTE">Mayor o igual que</option>
                    <option value="EQ">Igual a</option>
                    <option value="NE">Distinto de</option>
                    <option value="BEFORE">Fecha anterior a</option>
                    <option value="AFTER">Fecha posterior a</option>
                    <option value="CONTAINS">Contiene</option>
                </select>
            </label>
            <label>Valor de comparación
                <input name="comparisonValue" required maxlength="255" placeholder="Ej. 5 o 2026-12-31">
            </label>
            <label>Mensaje para mostrar (opcional)
                <textarea name="messageTemplate" maxlength="255" placeholder="Ej. Es momento de reponer este artículo"></textarea>
            </label>
            <div class="actions">
                <button type="button" class="secondary" data-close>Cancelar</button>
                <button type="submit" class="primary">Crear alerta</button>
            </div>
        </form>
    </dialog>
</c:if>

<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</body>
</html>
