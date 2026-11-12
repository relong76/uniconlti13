package net.unicon.lti.service.app;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApiSessionsTest {

    @Mock
    private APIJWTService apiJWTService;

    private ApiSessions apiSessions;
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        apiSessions = new ApiSessions(apiJWTService);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReturnsTheTokensClaims() {
        Claims claims = Jwts.claims().add("nonce", "n-1").build();
        Jws<Claims> jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(apiJWTService.extractJwtStringValue(request, false)).thenReturn("api-token");
        when(apiJWTService.validateToken("api-token")).thenReturn(jws);

        assertEquals("n-1", apiSessions.require(request).get("nonce"));
    }

    @Test
    public void testRejectsAMissingToken() {
        when(apiJWTService.extractJwtStringValue(request, false)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> apiSessions.require(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    public void testRejectsAnInvalidToken() {
        when(apiJWTService.extractJwtStringValue(request, false)).thenReturn("forged");
        when(apiJWTService.validateToken("forged")).thenThrow(new SignatureException("bad signature"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> apiSessions.require(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

}
