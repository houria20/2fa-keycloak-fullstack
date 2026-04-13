package com.example.twofa.filter;

import com.example.twofa.service.ActionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filter that enforces step-up (2FA) authentication on sensitive endpoints.
 *
 * <p>For requests matching {@code STEP_UP_PATTERNS}:
 * <ol>
 *   <li>If the JWT contains strong-auth proof (AMR=otp or ACR=gold) <em>and</em> a valid
 *       one-time action token is provided in the {@code X-Action-Token} header, the request
 *       is allowed through.</li>
 *   <li>Otherwise a {@code 403} response is returned with a fresh action token that the
 *       frontend must pass back after the user completes OTP in Keycloak.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StepUpAuthFilter extends OncePerRequestFilter {

    private final ActionTokenService actionTokenService;
    private final ObjectMapper objectMapper;

    @Value("${app.security.required-acr:gold}")
    private String requiredAcr;

    @Value("${app.security.required-amr:otp}")
    private String requiredAmr;

    private static final List<String> STEP_UP_PATTERNS = List.of("/api/cards/**");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!requiresStepUp(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String userId = jwt.getSubject();
        String actionKey = buildActionKey(request);

        if (hasStrongAuthentication(jwt)) {
            String actionToken = request.getHeader("X-Action-Token");
            if (actionTokenService.validateAndConsumeToken(actionToken, userId, actionKey)) {
                log.debug("Step-up auth passed for user={}, action={}", userId, actionKey);
                filterChain.doFilter(request, response);
            } else {
                log.warn("Invalid/expired action token for user={}, action={}", userId, actionKey);
                sendStepUpRequired(response, userId, actionKey);
            }
        } else {
            log.debug("No strong auth for user={}, issuing action token for action={}", userId, actionKey);
            sendStepUpRequired(response, userId, actionKey);
        }
    }

    private boolean requiresStepUp(HttpServletRequest request) {
        String path = request.getServletPath();
        return STEP_UP_PATTERNS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    private boolean hasStrongAuthentication(Jwt jwt) {
        // Check ACR (Authentication Context Class Reference)
        String acr = jwt.getClaimAsString("acr");
        if (requiredAcr.equals(acr)) {
            log.debug("Strong auth via acr={}", acr);
            return true;
        }
        // Check AMR (Authentication Methods References)
        List<String> amr = jwt.getClaimAsStringList("amr");
        if (amr != null && amr.contains(requiredAmr)) {
            log.debug("Strong auth via amr={}", amr);
            return true;
        }
        return false;
    }

    private void sendStepUpRequired(HttpServletResponse response, String userId, String action)
            throws IOException {
        String actionToken = actionTokenService.generateActionToken(userId, action);

        Map<String, String> body = new HashMap<>();
        body.put("error", "step_up_required");
        body.put("error_description", "Strong authentication (2FA) is required for this action");
        body.put("action_token", actionToken);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String buildActionKey(HttpServletRequest request) {
        return request.getMethod().toLowerCase() + ":" + request.getServletPath();
    }
}
