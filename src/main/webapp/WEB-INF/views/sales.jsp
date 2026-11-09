<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Ventas completas | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/sales.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp"><jsp:param name="activeNav" value="sales"/></jsp:include>
<main class="container sales-page">
    <jsp:include page="fragments/flash.jsp"/>
    <section class="hero sales-hero">
        <div>
            <span class="eyebrow">OPERACIONES DEL NEGOCIO</span>
            <h1>Ventas completas</h1>
            <p>Registra varios productos y descuenta sus existencias al guardar la venta.</p>
        </div>
        <c:if test="${canConfigureSales}"><div class="sales-hero-actions">
            <c:if test="${not empty salesConfiguration}"><a class="secondary" href="${pageContext.request.contextPath}/app/product-assets">Códigos y fotos</a></c:if>
            <button type="button" class="secondary" data-open="sales-config">Configurar productos</button>
        </div></c:if>
    </section>

    <c:choose>
        <c:when test="${empty salesConfiguration}">
            <section class="empty sales-empty">
                <span>▦</span><h2>Configura el origen de productos</h2>
                <p>Elige una tabla y sus columnas de nombre, existencias y precio antes de registrar ventas.</p>
                <c:choose>
                    <c:when test="${canConfigureSales}"><button type="button" class="primary" data-open="sales-config">Configurar ventas</button></c:when>
                    <c:otherwise><p>Pide al propietario o gerente que configure las ventas.</p></c:otherwise>
                </c:choose>
            </section>
        </c:when>
        <c:otherwise>
            <section class="sales-source">
                <div><strong>Productos: <c:out value="${salesSource.name}"/></strong>
                    <p>Los importes y existencias se comprueban al guardar. El total se calcula con las cantidades y precios de esta venta.</p>
                </div>
                <c:if test="${not empty salesSource && canViewSalesSource}"><a class="secondary" href="${pageContext.request.contextPath}/app/modules/${salesSource.id}">Abrir productos</a></c:if>
            </section>
            <c:choose>
                <c:when test="${not canViewSalesSource}">
                    <section class="sales-notice"><strong>Sin acceso al catálogo.</strong> Necesitas permiso para ver la tabla de productos.</section>
                </c:when>
                <c:when test="${not canSell}">
                    <section class="sales-notice"><strong>Sin permiso para vender.</strong> Pide acceso para ajustar las existencias de la tabla vinculada.</section>
                </c:when>
                <c:when test="${empty products}">
                    <section class="sales-notice"><strong>Sin productos disponibles.</strong> Agrega registros a la tabla de productos para preparar una venta.</section>
                </c:when>
                <c:otherwise>
                    <section class="sales-card" aria-labelledby="new-sale-title">
                        <div class="sales-card-heading">
                            <div><h2 id="new-sale-title">Nueva venta</h2><p>Agrega los artículos que compró el cliente. <c:out value="${canOverridePrice ? 'Puedes cambiar el precio unitario de esta venta.' : 'El precio se toma de la tabla de productos.'}"/></p></div>
                            <span class="sales-step">1. Productos</span>
                        </div>
                        <form method="post" action="${pageContext.request.contextPath}/app/sales" data-sales-form data-photo-base="${pageContext.request.contextPath}/app/product-assets?photo=">
                            <input type="hidden" name="action" value="create">
                            <input type="hidden" name="operationToken" value="${operationToken}">
                            <div class="sales-barcode" aria-label="Buscar producto por código">
                                <div><strong>Buscar por código de barras</strong><p>Escribe o escanea el código. Si el producto ya está en la venta, se aumenta su cantidad en una unidad.</p></div>
                                <div class="sales-barcode-controls">
                                    <input type="search" data-barcode-input aria-label="Código de barras" placeholder="Código de barras o lector USB" autocomplete="off">
                                    <button type="button" class="secondary" data-barcode-find>Buscar</button>
                                    <button type="button" class="secondary" data-barcode-camera hidden>Usar cámara</button>
                                </div>
                                <p class="sales-barcode-status" data-barcode-status role="status" aria-live="polite"></p>
                                <div class="sales-camera" data-barcode-camera-panel hidden>
                                    <video data-barcode-video autoplay muted playsinline aria-label="Vista de la cámara para leer el código"></video>
                                    <button type="button" class="secondary" data-barcode-stop>Detener cámara</button>
                                </div>
                            </div>
                            <div class="sale-lines" data-sale-lines>
                                <div class="sale-line" data-sale-line>
                                    <label class="sale-product-field">Producto
                                        <select name="productId" data-sale-product required>
                                            <option value="">Selecciona un producto</option>
                                            <c:forEach items="${products}" var="product">
                                                <option value="${product.rowId}" data-price="${product.price}" data-stock="${product.stock}" data-barcode="<c:out value='${product.barcode}'/>" data-photo="${product.hasPhoto}"><c:out value="${product.label}"/><c:if test="${not empty product.barcode}"> · <c:out value="${product.barcode}"/></c:if></option>
                                            </c:forEach>
                                        </select>
                                        <small data-sale-stock>Selecciona un producto para ver sus existencias.</small>
                                        <span class="sales-product-preview" data-sale-product-preview hidden><img data-sale-product-img alt=""><span>Foto del producto</span></span>
                                    </label>
                                    <label>Cantidad<input type="number" name="quantity" data-sale-quantity min="0.0001" step="0.0001" required value="1"></label>
                                    <label>Precio unitario<input type="number" name="unitPrice" data-sale-price min="0" step="0.0001" required ${canOverridePrice ? '' : 'readonly'}></label>
                                    <div class="sale-line-total"><span>Importe</span><strong data-sale-line-total>0.00</strong></div>
                                    <button type="button" class="sale-remove" data-sale-remove aria-label="Quitar este producto">Quitar</button>
                                </div>
                            </div>
                            <button type="button" class="secondary sale-add" data-sale-add>+ Agregar otro producto</button>

                            <div class="sales-payment">
                                <div><span class="sales-step">2. Pago</span><h3>Datos de la venta</h3></div>
                                <label>Forma de pago
                                    <select name="paymentMethod" required>
                                        <option value="CASH">Efectivo</option>
                                        <option value="CARD">Tarjeta</option>
                                        <option value="TRANSFER">Transferencia</option>
                                        <option value="OTHER">Otro</option>
                                    </select>
                                </label>
                                <label>Nota (opcional)<input name="note" maxlength="255" placeholder="Referencia o comentario interno"></label>
                            </div>
                            <div class="sales-checkout">
                                <div><span>Total estimado</span><strong data-sale-total>0.00</strong><small>En la moneda que usa tu negocio. El cobro se registra, pero no se procesa aquí.</small></div>
                                <button type="submit" class="primary" data-sale-submit>Guardar venta y descontar existencias</button>
                            </div>
                            <p class="sales-help">Si algún artículo no tiene existencias suficientes, no se guardará ninguna parte de la venta. Si se interrumpe la conexión al guardar, revisa el historial antes de intentarlo otra vez.</p>
                        </form>
                    </section>
                </c:otherwise>
            </c:choose>
        </c:otherwise>
    </c:choose>

    <section class="sales-history" aria-labelledby="sales-history-title">
        <div class="section-title"><h2 id="sales-history-title">Ventas recientes</h2><p>El historial de ventas completas es independiente de las tablas creadas manualmente, como [DEMO] Ventas; sus reportes no incluyen estas operaciones automáticamente.</p></div>
        <c:choose>
            <c:when test="${empty recentSales}"><div class="sales-notice">Todavía no hay ventas completas registradas.</div></c:when>
            <c:otherwise>
                <div class="table-wrap sales-history-table"><table>
                    <thead><tr><th>Venta</th><th>Fecha</th><th>Tabla de origen</th><th>Artículos</th><th>Forma de pago</th><th>Total</th><th>Detalle</th></tr></thead>
                    <tbody><c:forEach items="${recentSales}" var="sale"><tr>
                        <td><strong>#<c:out value="${sale.id}"/></strong></td>
                        <td><c:out value="${fn:replace(sale.createdAt, 'T', ' ')}"/></td>
                        <td><c:out value="${sale.sourceModuleName}"/></td>
                        <td><c:out value="${sale.itemCount}"/></td>
                        <td><c:out value="${sale.paymentMethod == 'CASH' ? 'Efectivo' : sale.paymentMethod == 'CARD' ? 'Tarjeta' : sale.paymentMethod == 'TRANSFER' ? 'Transferencia' : 'Otro'}"/></td>
                        <td><c:out value="${sale.total}"/></td>
                        <td><a href="${pageContext.request.contextPath}/app/sales?saleId=${sale.id}#sale-detail">Ver venta</a></td>
                    </tr></c:forEach></tbody>
                </table></div>
            </c:otherwise>
        </c:choose>
    </section>

    <c:if test="${not empty selectedSale}">
        <section id="sale-detail" class="sales-card sales-detail" aria-labelledby="sale-detail-title">
            <div class="sales-card-heading"><div><span class="eyebrow">VENTA GUARDADA</span><h2 id="sale-detail-title">Venta #<c:out value="${selectedSale.summary.id}"/></h2><p><c:out value="${fn:replace(selectedSale.summary.createdAt, 'T', ' ')}"/></p></div><strong class="sales-detail-total"><c:out value="${selectedSale.summary.total}"/></strong></div>
            <div class="sales-detail-meta"><span>Tabla: <strong><c:out value="${selectedSale.summary.sourceModuleName}"/></strong></span><span>Registró: <strong><c:out value="${selectedSale.summary.createdByName}"/></strong></span><span>Pago: <strong><c:out value="${selectedSale.summary.paymentMethod == 'CASH' ? 'Efectivo' : selectedSale.summary.paymentMethod == 'CARD' ? 'Tarjeta' : selectedSale.summary.paymentMethod == 'TRANSFER' ? 'Transferencia' : 'Otro'}"/></strong></span></div>
            <c:if test="${not empty selectedSale.summary.note}"><p class="sales-detail-note"><strong>Nota:</strong> <c:out value="${selectedSale.summary.note}"/></p></c:if>
            <div class="sales-return-totals" aria-label="Resumen después de devoluciones">
                <div><span>Venta original</span><strong><c:out value="${selectedSale.summary.total}"/></strong></div>
                <div><span>Devuelto</span><strong><c:out value="${selectedSale.returnedTotal}"/></strong></div>
                <div><span>Neto registrado</span><strong><c:out value="${selectedSale.netTotal}"/></strong></div>
            </div>
            <div class="table-wrap"><table><thead><tr><th>Producto</th><th>Vendido</th><th>Devuelto</th><th>Disponible para devolver</th><th>Precio unitario</th><th>Importe original</th></tr></thead>
                <tbody><c:forEach items="${selectedSale.items}" var="item"><tr><td><c:out value="${item.productLabel}"/></td><td><c:out value="${item.quantity}"/></td><td><c:out value="${item.returnedQuantity}"/></td><td><c:out value="${item.returnableQuantity}"/></td><td><c:out value="${item.unitPrice}"/></td><td><c:out value="${item.lineTotal}"/></td></tr></c:forEach></tbody>
            </table></div>

            <c:if test="${canReturnSale}">
                <section class="sales-return-section" aria-labelledby="sales-return-title">
                    <h3 id="sales-return-title">Registrar devolución</h3>
                    <p>Elige cantidades de la venta original. MiGestor repone existencias y registra el importe con el precio vendido; no envía dinero ni solicita un reembolso al banco.</p>
                    <c:choose>
                        <c:when test="${not hasReturnableItems}"><div class="sales-notice">No hay productos disponibles para devolver o fueron eliminados de la tabla original.</div></c:when>
                        <c:otherwise>
                            <form method="post" action="${pageContext.request.contextPath}/app/sales" data-return-form>
                                <input type="hidden" name="action" value="return">
                                <input type="hidden" name="saleId" value="${selectedSale.summary.id}">
                                <input type="hidden" name="saleReturnCsrfToken" value="${saleReturnCsrfToken}">
                                <input type="hidden" name="returnOperationToken" value="${returnOperationToken}">
                                <div class="sales-return-lines">
                                    <c:forEach items="${selectedSale.items}" var="item">
                                        <c:if test="${item.returnableQuantity > 0}">
                                            <div class="sales-return-line">
                                                <div><strong><c:out value="${item.productLabel}"/></strong><small>Vendido: <c:out value="${item.quantity}"/> · Devuelto: <c:out value="${item.returnedQuantity}"/> · Queda: <c:out value="${item.returnableQuantity}"/></small></div>
                                                <c:choose>
                                                    <c:when test="${empty item.productRowId}"><span class="sales-return-unavailable">Producto eliminado: no se puede reponer</span></c:when>
                                                    <c:otherwise><label>Cantidad a devolver
                                                        <input type="hidden" name="saleItemId" value="${item.id}">
                                                        <input type="number" name="returnQuantity" min="0" max="${item.returnableQuantity}" step="0.0001" value="0" required data-return-quantity>
                                                    </label></c:otherwise>
                                                </c:choose>
                                            </div>
                                        </c:if>
                                    </c:forEach>
                                </div>
                                <div class="sales-return-fields">
                                    <label>Método de reembolso registrado
                                        <select name="refundMethod" required>
                                            <option value="CASH">Efectivo</option>
                                            <option value="CARD">Tarjeta</option>
                                            <option value="TRANSFER">Transferencia</option>
                                            <option value="OTHER">Otro</option>
                                        </select>
                                    </label>
                                    <label>Motivo o nota (opcional)<input name="returnNote" maxlength="255" placeholder="Ej. Artículo devuelto en mostrador"></label>
                                </div>
                                <div class="sales-return-actions"><small>El importe exacto se calcula en el servidor según el total original y las devoluciones anteriores.</small><button type="submit" class="primary" data-return-submit>Guardar devolución y reponer existencias</button></div>
                            </form>
                        </c:otherwise>
                    </c:choose>
                </section>
            </c:if>

            <section class="sales-return-section" aria-labelledby="sales-return-history-title">
                <h3 id="sales-return-history-title">Historial de devoluciones</h3>
                <c:choose>
                    <c:when test="${empty selectedSale.returns}"><p>Todavía no hay devoluciones para esta venta.</p></c:when>
                    <c:otherwise><div class="sales-return-history">
                        <c:forEach items="${selectedSale.returns}" var="entry">
                            <article class="sales-return-entry">
                                <div><strong>Devolución #<c:out value="${entry.id}"/></strong><span><c:out value="${fn:replace(entry.createdAt, 'T', ' ')}"/></span><strong><c:out value="${entry.total}"/></strong></div>
                                <p>Registró: <c:out value="${entry.createdByName}"/> · Método: <c:out value="${entry.refundMethod == 'CASH' ? 'Efectivo' : entry.refundMethod == 'CARD' ? 'Tarjeta' : entry.refundMethod == 'TRANSFER' ? 'Transferencia' : 'Otro'}"/></p>
                                <c:if test="${not empty entry.note}"><p>Nota: <c:out value="${entry.note}"/></p></c:if>
                                <ul><c:forEach items="${entry.items}" var="returnedItem"><li><c:out value="${returnedItem.productLabel}"/> · <c:out value="${returnedItem.quantity}"/> · <c:out value="${returnedItem.lineTotal}"/></li></c:forEach></ul>
                            </article>
                        </c:forEach>
                    </div></c:otherwise>
                </c:choose>
            </section>
        </section>
    </c:if>
</main>

<c:if test="${canConfigureSales}">
    <dialog id="sales-config" class="wide-dialog sales-config-dialog">
        <div class="dialog-head"><h2>Configurar ventas completas</h2><button type="button" class="icon-btn" data-close aria-label="Cerrar">×</button></div>
        <c:choose><c:when test="${empty tableModules}"><p>Crea primero una tabla de productos con columnas de nombre, existencias y precio.</p></c:when>
        <c:otherwise><form method="post" action="${pageContext.request.contextPath}/app/sales" data-sales-config>
            <input type="hidden" name="action" value="configure">
            <p class="configuration-help">Elige columnas de una tabla existente. La configuración se aplica a las siguientes ventas; el historial anterior se conserva.</p>
            <label>Tabla de productos<select name="moduleId" data-sales-table required>
                <option value="">Selecciona una tabla</option>
                <c:forEach items="${tableModules}" var="table"><option value="${table.id}" ${salesConfiguration.moduleId == table.id ? 'selected' : ''}><c:out value="${table.name}"/></option></c:forEach>
            </select></label>
            <label>Columna del nombre<select name="displayColumnId" data-sales-column required>
                <option value="">Selecciona una columna de texto</option>
                <c:forEach items="${tableModules}" var="table"><c:forEach items="${columnsByModule[table.id]}" var="column"><c:if test="${column.dataType == 'TEXT' || column.dataType == 'SELECT'}"><option value="${column.id}" data-sales-table-id="${table.id}" ${salesConfiguration.displayColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/></option></c:if></c:forEach></c:forEach>
            </select></label>
            <label>Columna de existencias<select name="stockColumnId" data-sales-column required>
                <option value="">Selecciona una columna numérica</option>
                <c:forEach items="${tableModules}" var="table"><c:forEach items="${columnsByModule[table.id]}" var="column"><c:if test="${column.dataType == 'NUMBER'}"><option value="${column.id}" data-sales-table-id="${table.id}" ${salesConfiguration.stockColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/></option></c:if></c:forEach></c:forEach>
            </select></label>
            <label>Columna del precio<select name="priceColumnId" data-sales-column required>
                <option value="">Selecciona una columna numérica</option>
                <c:forEach items="${tableModules}" var="table"><c:forEach items="${columnsByModule[table.id]}" var="column"><c:if test="${column.dataType == 'NUMBER'}"><option value="${column.id}" data-sales-table-id="${table.id}" ${salesConfiguration.priceColumnId == column.id ? 'selected' : ''}><c:out value="${column.name}"/></option></c:if></c:forEach></c:forEach>
            </select></label>
            <p class="sales-help">Por ejemplo, la tabla [DEMO] Productos e inventario tiene columnas Producto, Existencias y Precio. No se agregan filas a otras tablas de ventas de forma automática.</p>
            <div class="actions"><button type="button" class="secondary" data-close>Cancelar</button><button type="submit" class="primary">Guardar configuración</button></div>
        </form></c:otherwise></c:choose>
    </dialog>
</c:if>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/sales.js"></script>
</body>
</html>
