package com.erp.audit;

import com.erp.entity.SysOperationLog;
import com.erp.entity.User;
import com.erp.mapper.SysOperationLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Records mutating HTTP calls (POST/PUT/PATCH/DELETE) for authenticated users.
 * GET and other methods are not logged.
 */
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {

    private static final int MAX_PARAMS_LEN = 8000;
    private static final Pattern PASSWORD_JSON = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @Around("within(com.erp.controller..*)")
    public Object aroundController(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return pjp.proceed();
        }
        HttpServletRequest request = attrs.getRequest();
        String method = request.getMethod();
        if (!isMutating(method)) {
            return pjp.proceed();
        }
        String uri = request.getRequestURI();
        if (shouldSkipUri(uri)) {
            return pjp.proceed();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            return pjp.proceed();
        }

        Throwable failure = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            try {
                persistLog(request, method, uri, user, pjp.getArgs(), failure);
            } catch (Exception e) {
                log.warn("Failed to persist operation log: {}", e.getMessage());
            }
        }
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static boolean shouldSkipUri(String uri) {
        if (uri == null) return true;
        String u = uri.toLowerCase(Locale.ROOT);
        return u.contains("/auth/") || u.contains("/actuator/");
    }

    private void persistLog(
            HttpServletRequest request,
            String httpMethod,
            String uri,
            User user,
            Object[] args,
            Throwable failure) {
        SysOperationLog row = new SysOperationLog();
        row.setUserId(user.getId());
        row.setUserName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        row.setUserRole(user.getRole() != null ? user.getRole() : "");
        row.setOperationName(operationLabel(httpMethod) + " " + uri);
        row.setOperationAt(LocalDateTime.now());
        row.setClientIp(resolveClientIp(request));
        row.setRequestUri(uri);
        row.setRequestParams(buildParams(request, args, failure));
        operationLogMapper.insert(row);
    }

    private static String operationLabel(String httpMethod) {
        return switch (httpMethod.toUpperCase(Locale.ROOT)) {
            case "POST" -> "新增";
            case "PUT", "PATCH" -> "修改";
            case "DELETE" -> "删除";
            default -> httpMethod;
        };
    }

    private String buildParams(HttpServletRequest request, Object[] args, Throwable failure) {
        String ct = request.getContentType();
        if (ct != null && ct.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return appendFailure("[multipart/form-data omitted]", failure);
        }

        StringBuilder sb = new StringBuilder();
        String qs = request.getQueryString();
        if (StringUtils.hasText(qs)) {
            sb.append("query=").append(maskPasswordsInText(qs));
        }
        List<String> parts = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                if (arg == null) continue;
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) continue;
                if (arg instanceof Authentication) continue;
                if (arg instanceof BindingResult || arg instanceof SessionStatus) continue;
                if (arg instanceof ModelAndView) continue;
                if (arg instanceof MultipartFile mf) {
                    parts.add("file=" + mf.getOriginalFilename());
                    continue;
                }
                if (arg instanceof MultipartFile[] files) {
                    StringBuilder fb = new StringBuilder("files=[");
                    for (int i = 0; i < files.length; i++) {
                        if (files[i] == null) continue;
                        if (i > 0) fb.append(',');
                        fb.append(files[i].getOriginalFilename());
                    }
                    fb.append(']');
                    parts.add(fb.toString());
                    continue;
                }
                try {
                    parts.add(objectMapper.writeValueAsString(arg));
                } catch (JsonProcessingException e) {
                    parts.add(arg.getClass().getSimpleName() + "@" + Integer.toHexString(arg.hashCode()));
                }
            }
        }
        if (!parts.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("body=");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append(parts.get(i));
            }
        }
        String raw = maskPasswordsInText(sb.toString());
        if (raw.length() > MAX_PARAMS_LEN) {
            raw = raw.substring(0, MAX_PARAMS_LEN) + "…(truncated)";
        }
        return appendFailure(raw, failure);
    }

    private static String appendFailure(String base, Throwable failure) {
        if (failure == null) return base;
        String msg = failure.getMessage();
        if (!StringUtils.hasText(msg)) {
            msg = failure.getClass().getSimpleName();
        }
        if (msg.length() > 500) {
            msg = msg.substring(0, 500) + "…";
        }
        if (!StringUtils.hasText(base)) {
            return "error=" + msg;
        }
        return base + " | error=" + msg;
    }

    private static String maskPasswordsInText(String s) {
        if (!StringUtils.hasText(s)) return s;
        return PASSWORD_JSON.matcher(s).replaceAll("$1\"***\"");
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(real)) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
