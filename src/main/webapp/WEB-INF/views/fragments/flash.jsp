<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:if test="${not empty sessionScope.flashMessage}">
  <div class="flash ${sessionScope.flashType}"><c:out value="${sessionScope.flashMessage}"/></div>
  <c:remove var="flashMessage" scope="session"/><c:remove var="flashType" scope="session"/>
</c:if>
