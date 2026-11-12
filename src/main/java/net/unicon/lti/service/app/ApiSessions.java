package net.unicon.lti.service.app;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the API token the app sends as "Authorization: Bearer ...". The /api filter has already
 * checked it; this gives a controller its claims (context, deployment, user, roles, launch nonce).
 */
@Component
@RequiredArgsConstructor
public class ApiSessions {

    private final APIJWTService apiJWTService;

    public Claims require(HttpServletRequest request) {
        String token = apiJWTService.extractJwtStringValue(request, false);

        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing API token");
        }

        try {
            return apiJWTService.validateToken(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API token");
        }
    }

}
