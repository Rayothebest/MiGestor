package com.gestor.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@WebFilter("/*")
public class DatabaseErrorFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException exception) {
            if (!isDatabaseUnavailable(exception)) throw exception;

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if (httpResponse.isCommitted()) throw exception;

            httpResponse.resetBuffer();
            httpResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            httpRequest.getRequestDispatcher("/WEB-INF/views/service-unavailable.jsp")
                    .forward(httpRequest, httpResponse);
        }
    }

    private boolean isDatabaseUnavailable(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException) return true;
            if (cause instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if (state != null && state.startsWith("08")) return true;
            }
            if (cause.getClass().getName().contains("CommunicationsException")) return true;
        }
        return false;
    }
}
