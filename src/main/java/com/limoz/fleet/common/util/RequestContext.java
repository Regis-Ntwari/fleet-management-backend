package com.limoz.fleet.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class RequestContext {

    private RequestContext() {}

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.ofNullable(attrs.getRequest());
        }
        return Optional.empty();
    }

    public static Optional<String> clientIp() {
        return currentRequest().map(RequestContext::clientIp);
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static Optional<String> userAgent() {
        return currentRequest().map(r -> r.getHeader("User-Agent"));
    }
}
