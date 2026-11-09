package com.gestor.util;

import jakarta.servlet.http.HttpServletRequest;

public final class WebUtil {
    private WebUtil() {}
    public static long businessId(HttpServletRequest req) { return (Long) req.getSession().getAttribute("businessId"); }
    public static long userId(HttpServletRequest req) { return (Long) req.getSession().getAttribute("userId"); }
    public static String userRole(HttpServletRequest req) { return String.valueOf(req.getSession().getAttribute("userRole")); }
    public static long longParam(HttpServletRequest req, String name) {
        try { return Long.parseLong(req.getParameter(name)); }
        catch (Exception e) { return -1; }
    }
    public static String clean(String value) { return value == null ? "" : value.trim(); }
    public static void flash(HttpServletRequest req, String type, String message) {
        req.getSession().setAttribute("flashType", type);
        req.getSession().setAttribute("flashMessage", message);
    }
}
