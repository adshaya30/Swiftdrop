package com.swiftdrop.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.AntPathMatcher;

/** Matches "METHOD /path/**" rules of SecurityRules against a request. */
final class RulePatterns {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private RulePatterns() {
    }

    static boolean matches(List<String> rules, HttpServletRequest request) {
        String method = request.getMethod();
        String path = pathOf(request);
        for (String rule : rules) {
            int space = rule.indexOf(' ');
            String ruleMethod = space < 0 ? "*" : rule.substring(0, space).trim();
            String rulePath = space < 0 ? rule.trim() : rule.substring(space + 1).trim();
            if (("*".equals(ruleMethod) || ruleMethod.equalsIgnoreCase(method)) && MATCHER.match(rulePath, path)) {
                return true;
            }
        }
        return false;
    }

    static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
