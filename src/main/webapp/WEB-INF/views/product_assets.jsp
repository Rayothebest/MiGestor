<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Códigos y fotos | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/management.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product_assets.css">
</head>
<body>
<jsp:include page="fragments/topbar.jsp"><jsp:param name="activeNav" value="productAssets"/></jsp:include>
<main class="container product-assets-page">
    <jsp:include page="fragments/flash.jsp"/>
    <section class="hero assets-hero">
        <div>
            <span class="eyebrow">CATÁLOGO DE VENTAS</span>
            <h1>Códigos y fotos</h1>
            <p>Asigna un código único y una imagen a cada producto de tu catálogo.</p>
        </div>
        <a class="secondary" href="${pageContext.request.contextPath}/app/sales">Ir a Ventas</a>
    </section>

    <c:choose>
        <c:when test="${empty salesConfiguration}">
            <section class="assets-empty" aria-label="Catálogo sin configurar">
                <span class="assets-empty-icon" aria-hidden="true">▦</span>
                <h2>Configura primero los productos de Ventas</h2>
                <p>Elige la tabla y las columnas de nombre, existencias y precio en Ventas completas. Después podrás añadir códigos y fotos a sus filas.</p>
                <a class="primary" href="${pageContext.request.contextPath}/app/sales">Configurar Ventas</a>
            </section>
        </c:when>
        <c:otherwise>
            <section class="assets-intro" aria-label="Tabla configurada">
                <div>
                    <strong>Tabla: <c:out value="${salesConfiguration.moduleName}"/></strong>
                    <p>Los códigos son únicos dentro de esta tabla. La foto se muestra en el catálogo y en Ventas. Puedes cambiar ambos sin modificar el ID ni el inventario.</p>
                </div>
                <a class="secondary" href="${pageContext.request.contextPath}/app/modules/${salesConfiguration.moduleId}">Abrir tabla</a>
            </section>

            <c:choose>
                <c:when test="${empty assetProducts}">
                    <section class="assets-empty" aria-label="Sin productos">
                        <h2>Aún no hay productos</h2>
                        <p>Agrega la primera fila a la tabla configurada para Ventas.</p>
                        <a class="primary" href="${pageContext.request.contextPath}/app/modules/${salesConfiguration.moduleId}">Abrir tabla</a>
                    </section>
                </c:when>
                <c:otherwise>
                    <div class="assets-tools">
                        <div><h2>Productos</h2><p><c:out value="${fn:length(assetProducts)}"/> registros en la tabla de Ventas</p></div>
                        <label>Buscar producto o código
                            <input type="search" placeholder="Nombre, código o ID" autocomplete="off" data-product-search>
                        </label>
                    </div>
                    <p class="assets-help">Admite códigos impresos como EAN, UPC o códigos internos alfanuméricos. Escribe o pega el valor exacto; desde Ventas podrás usarlo para localizar el producto.</p>
                    <div class="asset-grid" data-product-list>
                        <c:forEach items="${assetProducts}" var="product">
                            <article id="product-${product.rowId}" class="asset-card" data-product-card data-search="${fn:escapeXml(product.label)} ${fn:escapeXml(product.barcode)} ${product.rowId}">
                                <div class="asset-photo">
                                    <c:choose>
                                        <c:when test="${product.hasPhoto}">
                                            <img src="${pageContext.request.contextPath}/app/product-assets?photo=${product.rowId}" alt="Foto de ${fn:escapeXml(product.label)}" loading="lazy" data-photo-preview>
                                        </c:when>
                                        <c:otherwise><div class="asset-placeholder" aria-label="Sin foto" data-photo-placeholder><span aria-hidden="true">▧</span><small>Sin foto</small></div></c:otherwise>
                                    </c:choose>
                                </div>
                                <div class="asset-body">
                                    <div class="asset-title"><div><small>Producto #<c:out value="${product.rowId}"/></small><h3><c:out value="${product.label}"/></h3></div><c:if test="${product.hasPhoto}"><span class="asset-photo-badge">Con foto</span></c:if></div>
                                    <form method="post" action="${pageContext.request.contextPath}/app/product-assets" class="asset-code-form" data-asset-form>
                                        <input type="hidden" name="action" value="code">
                                        <input type="hidden" name="rowId" value="${product.rowId}">
                                        <input type="hidden" name="productAssetsCsrfToken" value="${productAssetsCsrfToken}">
                                        <label>Código de barras o interno
                                            <input name="barcode" value="${fn:escapeXml(product.barcode)}" maxlength="64" autocomplete="off" placeholder="Ej. 7501234567890">
                                        </label>
                                        <button type="submit" class="secondary">Guardar código</button>
                                    </form>
                                    <p class="asset-hint">Para quitar el código, deja el campo vacío y guárdalo.</p>
                                    <form method="post" enctype="multipart/form-data" action="${pageContext.request.contextPath}/app/product-assets" class="asset-photo-form" data-photo-form data-asset-form>
                                        <input type="hidden" name="action" value="photo">
                                        <input type="hidden" name="rowId" value="${product.rowId}">
                                        <input type="hidden" name="productAssetsCsrfToken" value="${productAssetsCsrfToken}">
                                        <label>Foto JPEG o PNG (máximo 4 MB)
                                            <input type="file" name="photo" accept="image/jpeg,image/png" required data-photo-input>
                                        </label>
                                        <button type="submit" class="secondary">${product.hasPhoto ? 'Cambiar foto' : 'Guardar foto'}</button>
                                        <p class="asset-file-error" role="alert" hidden data-photo-error></p>
                                    </form>
                                    <c:if test="${product.hasPhoto}">
                                        <form method="post" action="${pageContext.request.contextPath}/app/product-assets" class="asset-remove-form" data-asset-form>
                                            <input type="hidden" name="action" value="removePhoto">
                                            <input type="hidden" name="rowId" value="${product.rowId}">
                                            <input type="hidden" name="productAssetsCsrfToken" value="${productAssetsCsrfToken}">
                                            <button type="submit" class="asset-remove" data-confirm="¿Eliminar la foto de este producto?">Quitar foto</button>
                                        </form>
                                    </c:if>
                                </div>
                            </article>
                        </c:forEach>
                    </div>
                    <p class="assets-no-match" hidden data-no-match>No hay productos que coincidan con esa búsqueda.</p>
                </c:otherwise>
            </c:choose>
        </c:otherwise>
    </c:choose>
    <p class="assets-footnote">Las imágenes se guardan en la base de datos y quedan asociadas a tu negocio. Solo los usuarios con acceso a la tabla pueden verlas.</p>
</main>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/product_assets.js"></script>
</body>
</html>
