package com.gestor.util;

import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

public final class DownloadUtil {
    private DownloadUtil() {}

    public static void prepare(HttpServletResponse response, String contentType, String fileName) {
        String safeAsciiName = asciiFileName(fileName);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.reset();
        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + safeAsciiName + "\"; filename*=UTF-8''" + encoded);
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    public static String slug(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "tabla" : normalized.substring(0, Math.min(normalized.length(), 60));
    }

    private static String asciiFileName(String value) {
        String normalized = Normalizer.normalize(value == null ? "descarga" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalized.isBlank()) return "descarga";
        return normalized.substring(0, Math.min(normalized.length(), 120));
    }
}
