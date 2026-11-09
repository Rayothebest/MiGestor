<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Caja y gastos | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/cash.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp"><jsp:param name="activeNav" value="cash"/></jsp:include>
<main class="container cash-page">
    <jsp:include page="fragments/flash.jsp"/>

    <section class="hero cash-hero">
        <div>
            <span class="eyebrow">OPERACIONES DEL NEGOCIO</span>
            <h1>Caja y gastos</h1>
            <p>Abre un turno, registra ingresos o gastos y compara el efectivo contado al cerrar.</p>
        </div>
    </section>

    <c:choose>
        <c:when test="${not empty cashSummary.activeSession}">
            <section id="cash-current" class="cash-status is-open" aria-label="Turno de caja abierto">
                <div><strong>Turno #<c:out value="${cashSummary.activeSession.id}"/> abierto</strong>
                    <p>Desde <c:out value="${fn:replace(cashSummary.activeSession.openedAt, 'T', ' ')}"/> · Abrió: <c:out value="${cashSummary.activeSession.openedByName}"/></p>
                    <c:if test="${not empty cashSummary.activeSession.note}"><p><c:out value="${cashSummary.activeSession.note}"/></p></c:if>
                </div>
                <span class="cash-badge">ACTIVO</span>
            </section>

            <section class="cash-metrics" aria-label="Resumen del turno actual">
                <div class="cash-metric"><span>Fondo inicial</span><strong><c:out value="${cashSummary.activeSession.openingCash}"/></strong></div>
                <div class="cash-metric"><span>Ventas en efectivo</span><strong><c:out value="${cashSummary.cashSales}"/></strong></div>
                <div class="cash-metric"><span>Devoluciones en efectivo</span><strong><c:out value="${cashSummary.cashReturns}"/></strong></div>
                <div class="cash-metric is-main"><span>Efectivo esperado</span><strong><c:out value="${cashSummary.expectedCash}"/></strong><small>Incluye ingresos y gastos manuales en efectivo.</small></div>
            </section>

            <div class="cash-layout">
                <section class="cash-card" aria-labelledby="cash-breakdown-title">
                    <div class="cash-card-head"><h2 id="cash-breakdown-title">Cómo se calcula el efectivo</h2><p>Solo los movimientos de este turno con método efectivo cambian el saldo esperado.</p></div>
                    <div class="cash-breakdown">
                        <div><span>Fondo inicial</span><strong><c:out value="${cashSummary.activeSession.openingCash}"/></strong></div>
                        <div><span>+ Ventas en efectivo</span><strong><c:out value="${cashSummary.cashSales}"/></strong></div>
                        <div><span>− Devoluciones en efectivo</span><strong><c:out value="${cashSummary.cashReturns}"/></strong></div>
                        <div><span>+ Ingresos manuales en efectivo</span><strong><c:out value="${cashSummary.manualCashIncome}"/></strong></div>
                        <div><span>− Gastos manuales en efectivo</span><strong><c:out value="${cashSummary.manualCashExpense}"/></strong></div>
                        <div><span>Efectivo esperado</span><strong><c:out value="${cashSummary.expectedCash}"/></strong></div>
                    </div>
                </section>
                <section class="cash-card" aria-labelledby="cash-other-methods-title">
                    <div class="cash-card-head"><h2 id="cash-other-methods-title">Otros métodos</h2><p>Se conservan en el historial, pero no se suman al efectivo del cajón.</p></div>
                    <div class="cash-methods">
                        <div><span>Ventas sin efectivo</span><strong><c:out value="${cashSummary.nonCashSales}"/></strong></div>
                        <div><span>Devoluciones sin efectivo</span><strong><c:out value="${cashSummary.nonCashReturns}"/></strong></div>
                        <div><span>Movimientos en este turno</span><strong><c:out value="${cashSummary.movementCount}"/></strong></div>
                    </div>
                </section>
            </div>
        </c:when>
        <c:otherwise>
            <section id="cash-current" class="cash-status is-closed" aria-label="Sin turno de caja">
                <div><strong>No hay un turno abierto</strong><p>Abre uno para calcular el efectivo esperado y guardar un arqueo al cerrarlo.</p></div>
                <span class="cash-badge">SIN TURNO</span>
            </section>
            <section id="cash-open" class="cash-card" aria-labelledby="cash-open-title">
                <div class="cash-card-head"><h2 id="cash-open-title">Abrir turno</h2><p>Cuenta el efectivo disponible al empezar. Solo puede haber un turno abierto por negocio.</p></div>
                <form method="post" action="${pageContext.request.contextPath}/app/cash" class="cash-form" data-cash-form>
                    <input type="hidden" name="action" value="open">
                    <input type="hidden" name="cashCsrfToken" value="${cashCsrfToken}">
                    <input type="hidden" name="operationToken" value="${openToken}">
                    <div class="cash-form-grid">
                        <label>Fondo inicial en efectivo
                            <input type="number" name="openingCash" min="0" max="99999999999999999.99" step="0.01" value="0.00" required>
                        </label>
                        <label>Nota (opcional)
                            <input name="note" maxlength="255" placeholder="Ej. Turno de mañana">
                        </label>
                    </div>
                    <button type="submit" class="primary">Abrir turno de caja</button>
                </form>
            </section>
        </c:otherwise>
    </c:choose>

    <div class="cash-layout">
        <section id="cash-movement-form" class="cash-card" aria-labelledby="cash-movement-form-title">
            <div class="cash-card-head"><h2 id="cash-movement-form-title">Registrar ingreso o gasto</h2>
                <p>Para operaciones manuales como un aporte a caja, pago de servicios o compra de insumos. Las ventas y devoluciones completas aparecen automáticamente.</p></div>
            <c:if test="${empty cashSummary.activeSession}">
                <p class="cash-notice"><strong>Fuera de turno:</strong> puedes guardar el movimiento para su historial, pero no entrará en un cierre posterior.</p>
            </c:if>
            <form method="post" action="${pageContext.request.contextPath}/app/cash" class="cash-form" data-cash-form data-movement-form>
                <input type="hidden" name="action" value="movement">
                <input type="hidden" name="cashCsrfToken" value="${cashCsrfToken}">
                <input type="hidden" name="operationToken" value="${movementToken}">
                <div class="cash-form-grid">
                    <label>Tipo
                        <select name="kind" required data-movement-kind>
                            <option value="EXPENSE">Gasto</option>
                            <option value="INCOME">Ingreso</option>
                        </select>
                    </label>
                    <label>Método
                        <select name="method" required>
                            <option value="CASH">Efectivo</option>
                            <option value="CARD">Tarjeta</option>
                            <option value="TRANSFER">Transferencia</option>
                            <option value="OTHER">Otro</option>
                        </select>
                    </label>
                    <label>Categoría
                        <input name="category" maxlength="80" required placeholder="Ej. Servicios, insumos, aporte">
                    </label>
                    <label>Importe
                        <input type="number" name="amount" min="0.01" max="99999999999999999.99" step="0.01" required placeholder="0.00">
                    </label>
                    <label class="wide">Nota (opcional)
                        <textarea name="note" maxlength="255" placeholder="Motivo o referencia para consultar después"></textarea>
                    </label>
                </div>
                <p class="cash-form-help">El método se registra como dato interno. MiGestor no cobra ni envía dinero.</p>
                <button type="submit" class="primary" data-movement-submit>Guardar gasto</button>
            </form>
        </section>

        <c:choose>
            <c:when test="${not empty cashSummary.activeSession}">
                <section id="cash-close" class="cash-card cash-close" aria-labelledby="cash-close-title">
                    <div class="cash-card-head"><h2 id="cash-close-title">Cerrar y arquear turno</h2><p>Cuenta el efectivo real. El cierre guarda una diferencia frente al efectivo esperado.</p></div>
                    <form method="post" action="${pageContext.request.contextPath}/app/cash" class="cash-form" data-cash-form>
                        <input type="hidden" name="action" value="close">
                        <input type="hidden" name="cashCsrfToken" value="${cashCsrfToken}">
                        <input type="hidden" name="operationToken" value="${closeToken}">
                        <label>Efectivo contado
                            <input type="number" name="countedCash" min="0" max="99999999999999999.99" step="0.01" required placeholder="0.00" data-counted-cash>
                        </label>
                        <div class="cash-count-preview" data-count-preview data-expected-cash="${cashSummary.expectedCash}">
                            <span>Diferencia estimada</span><strong data-count-difference>—</strong>
                        </div>
                        <label>Nota del cierre (opcional)
                            <textarea name="note" maxlength="255" placeholder="Ej. Entrega de efectivo, aclaración de diferencia"></textarea>
                        </label>
                        <p class="cash-form-help">El cierre no modifica ventas, devoluciones ni gastos anteriores. La diferencia se calcula nuevamente al guardar.</p>
                        <button type="submit" class="primary" data-confirm="¿Cerrar este turno con el efectivo contado indicado? El arqueo quedará guardado en el historial.">Cerrar turno</button>
                    </form>
                </section>
            </c:when>
            <c:otherwise>
                <section class="cash-card" aria-labelledby="cash-no-turn-title"><h2 id="cash-no-turn-title">Sobre los turnos</h2>
                    <p>Las ventas y devoluciones, incluso las hechas sin turno abierto, siempre quedan en el historial. Solo los movimientos vinculados a un turno se incluyen en su cierre.</p>
                    <p><strong>Movimientos fuera de turno:</strong> <c:out value="${cashSummary.outsideSessionCount}"/></p>
                </section>
            </c:otherwise>
        </c:choose>
    </div>

    <section id="cash-movements" class="cash-card" aria-labelledby="cash-movements-title">
        <div class="cash-card-head"><h2 id="cash-movements-title">Movimientos recientes</h2>
            <p>Ventas, devoluciones, ingresos y gastos del negocio. Los movimientos fuera de turno se identifican aquí y no cambian el arqueo.</p></div>
        <c:choose>
            <c:when test="${empty cashMovements}"><div class="cash-empty">Todavía no hay movimientos de caja.</div></c:when>
            <c:otherwise>
                <div class="table-wrap cash-table"><table>
                    <thead><tr><th>Fecha</th><th>Tipo</th><th>Concepto</th><th>Método</th><th>Importe</th><th>Turno</th><th>Registró</th></tr></thead>
                    <tbody><c:forEach items="${cashMovements}" var="entry">
                        <tr>
                            <td><c:out value="${fn:replace(entry.createdAt, 'T', ' ')}"/></td>
                            <td><span class="cash-source ${entry.kind == 'INCOME' || entry.kind == 'EXPENSE' ? 'manual' : ''}">
                                <c:out value="${entry.kind == 'SALE' ? 'Venta' : entry.kind == 'RETURN' ? 'Devolución' : entry.kind == 'INCOME' ? 'Ingreso' : 'Gasto'}"/>
                            </span></td>
                            <td><strong><c:out value="${entry.category}"/></strong>
                                <c:if test="${not empty entry.note}"><br><small><c:out value="${entry.note}"/></small></c:if>
                                <c:if test="${not empty entry.saleId}"><br><a href="${pageContext.request.contextPath}/app/sales?saleId=${entry.saleId}#sale-detail">Venta #<c:out value="${entry.saleId}"/></a></c:if>
                                <c:if test="${not empty entry.returnId}"><br><small>Devolución #<c:out value="${entry.returnId}"/></small></c:if>
                            </td>
                            <td><c:out value="${entry.method == 'CASH' ? 'Efectivo' : entry.method == 'CARD' ? 'Tarjeta' : entry.method == 'TRANSFER' ? 'Transferencia' : 'Otro'}"/></td>
                            <td class="cash-amount ${entry.kind == 'SALE' || entry.kind == 'INCOME' ? 'in' : 'out'}"><c:out value="${entry.kind == 'SALE' || entry.kind == 'INCOME' ? '+' : '−'}"/><c:out value="${entry.amount}"/></td>
                            <td><c:choose><c:when test="${empty entry.sessionId}">Fuera de turno</c:when><c:otherwise>#<c:out value="${entry.sessionId}"/></c:otherwise></c:choose></td>
                            <td><c:out value="${entry.createdByName}"/></td>
                        </tr>
                    </c:forEach></tbody>
                </table></div>
            </c:otherwise>
        </c:choose>
    </section>

    <section id="cash-closings" class="cash-card" aria-labelledby="cash-closings-title">
        <div class="cash-card-head"><h2 id="cash-closings-title">Cierres recientes</h2><p>Cada arqueo conserva el efectivo esperado y el contado al terminar el turno.</p></div>
        <c:choose>
            <c:when test="${empty cashClosings}"><div class="cash-empty">Aún no hay cierres de caja.</div></c:when>
            <c:otherwise><div class="cash-close-list">
                <c:forEach items="${cashClosings}" var="closing">
                    <article class="cash-close-entry">
                        <div><strong>Turno #<c:out value="${closing.id}"/></strong><small>Abierto <c:out value="${fn:replace(closing.openedAt, 'T', ' ')}"/> · Cerrado <c:out value="${fn:replace(closing.closedAt, 'T', ' ')}"/></small></div>
                        <div><span>Esperado: <strong><c:out value="${closing.expectedCash}"/></strong></span><span>Contado: <strong><c:out value="${closing.countedCash}"/></strong></span><span>Diferencia: <strong><c:out value="${closing.difference}"/></strong></span></div>
                        <small>Abrió: <c:out value="${closing.openedByName}"/> · Cerró: <c:out value="${closing.closedByName}"/></small>
                        <c:if test="${not empty closing.note}"><p>Nota: <c:out value="${closing.note}"/></p></c:if>
                    </article>
                </c:forEach>
            </div></c:otherwise>
        </c:choose>
    </section>
    <p class="cash-footnote">Los importes se muestran en la moneda que usa tu negocio. Caja es un registro interno; no realiza pagos, cobros bancarios ni facturación.</p>
</main>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/cash.js"></script>
</body>
</html>
