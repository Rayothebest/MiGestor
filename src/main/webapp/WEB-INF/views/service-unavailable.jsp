<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>MySQL no está disponible | MiGestor</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/service.css">
</head>
<body class="auth-page">
<main class="auth-card service-card">
    <a class="brand" href="${pageContext.request.contextPath}/">MiGestor</a>
    <div class="service-icon">!</div>
    <h1>MySQL no está iniciado</h1>
    <p class="muted">Inicia <strong>MySQL</strong> desde el panel de XAMPP y después vuelve a intentarlo.</p>
    <button type="button" class="primary" onclick="window.location.reload()">Volver a intentar</button>
</main>
</body>
</html>
