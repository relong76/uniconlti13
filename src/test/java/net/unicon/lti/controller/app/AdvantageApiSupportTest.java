package net.unicon.lti.controller.app;

import io.jsonwebtoken.Jwts;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.LtiContextEntity;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.repository.LtiContextRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.app.ApiSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AdvantageApiSupportTest {

    @Mock
    private ApiSessions apiSessions;

    @Mock
    private PlatformDeploymentRepository platformDeploymentRepository;

    @Mock
    private LtiContextRepository ltiContextRepository;

    private AdvantageApiSupport support;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final PlatformDeployment deployment = new PlatformDeployment();
    private final LtiContextEntity context = new LtiContextEntity();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        support = new AdvantageApiSupport(apiSessions, platformDeploymentRepository, ltiContextRepository) { };
    }

    private void session(Object deploymentId, Object contextKey) {
        var claims = Jwts.claims();
        if (deploymentId != null) {
            claims.add("platformDeploymentId", deploymentId);
        }
        if (contextKey != null) {
            claims.add("contextKey", contextKey);
        }
        when(apiSessions.require(request)).thenReturn(claims.build());
    }

    private static void assertNotFound(Runnable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals(AdvantageApiSupport.COURSE_NOT_FOUND, e.getReason());
    }

    @Test
    public void testFindsTheCourse() {
        session(7, "ctx-key");
        when(platformDeploymentRepository.findById(7L)).thenReturn(Optional.of(deployment));
        when(ltiContextRepository.findByContextKeyAndPlatformDeployment("ctx-key", deployment)).thenReturn(context);

        AdvantageApiSupport.Course course = support.course(request);

        assertSame(deployment, course.deployment());
        assertSame(context, course.context());
    }

    @Test
    public void testMissingClaims() {
        session(null, "ctx-key");
        assertNotFound(() -> support.course(request));

        session("7", null);
        assertNotFound(() -> support.course(request));

        verifyNoInteractions(platformDeploymentRepository, ltiContextRepository);
    }

    @Test
    public void testDeploymentIdIsNotANumber() {
        session("seven", "ctx-key");
        assertNotFound(() -> support.course(request));
        verifyNoInteractions(platformDeploymentRepository);
    }

    @Test
    public void testDeploymentNotFound() {
        session("7", "ctx-key");
        when(platformDeploymentRepository.findById(7L)).thenReturn(Optional.empty());
        assertNotFound(() -> support.course(request));
    }

    @Test
    public void testContextNotFound() {
        session("7", "ctx-key");
        when(platformDeploymentRepository.findById(7L)).thenReturn(Optional.of(deployment));
        assertNotFound(() -> support.course(request));
    }

    @Test
    public void testPassesOnTheApiSessionError() {
        when(apiSessions.require(request)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing API token"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> support.course(request));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    public void testStatusErrorsAsJson() {
        ResponseEntity<Map<String, Object>> response = support.handleStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a label."));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("status", 400, "message", "Enter a label."), response.getBody());

        response = support.handleStatus(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals(Map.of("status", 403, "message", "The request failed."), response.getBody());
    }

    @Test
    public void testConnectionErrorsAsJson() {
        ResponseEntity<Map<String, Object>> response = support.handleConnection(new ConnectionException("Can't get the AGS\nException : 401 Unauthorized\n"));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(Map.of("status", 502, "message", "The platform didn't complete the request. Can't get the AGS Exception : 401 Unauthorized"), response.getBody());

        response = support.handleConnection(new ConnectionException(null));
        assertEquals("The platform didn't complete the request.", response.getBody().get("message"));
    }

}
