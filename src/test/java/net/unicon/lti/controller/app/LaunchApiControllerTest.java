package net.unicon.lti.controller.app;

import io.jsonwebtoken.Jwts;
import net.unicon.lti.model.LaunchRecord;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.app.LaunchStore;
import net.unicon.lti.utils.LtiStrings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class LaunchApiControllerTest {

    @Mock
    private ApiSessions apiSessions;

    @Mock
    private LaunchStore launchStore;

    private LaunchApiController controller;
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new LaunchApiController(apiSessions, launchStore);
        when(apiSessions.require(request)).thenReturn(Jwts.claims().add("nonce", "launch-nonce").build());
    }

    private static Map<String, Object> claims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://canvas.instructure.com");
        claims.put("sub", "user-1");
        claims.put(LtiStrings.LTI_MESSAGE_TYPE, "LtiResourceLinkRequest");
        claims.put(LtiStrings.LTI_VERSION, "1.3.0");
        claims.put(LtiStrings.LTI_DEPLOYMENT_ID, "284:abc");
        claims.put(LtiStrings.LTI_TARGET_LINK_URI, "https://tool.example.com/lti3");
        claims.put(LtiStrings.LTI_NAME, "Ada Lovelace");
        claims.put(LtiStrings.LTI_GIVEN_NAME, "Ada");
        claims.put(LtiStrings.LTI_FAMILY_NAME, "Lovelace");
        claims.put(LtiStrings.LTI_EMAIL, "ada@example.edu");
        claims.put(LtiStrings.LTI_PICTURE, "https://lms/ada.png");
        claims.put(LtiStrings.LTI_LOCALE, "en");
        claims.put(LtiStrings.LTI_ROLES, List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"));
        claims.put(LtiStrings.LTI_CONTEXT, Map.of("id", "ctx-1", "title", "Biology 101"));
        claims.put(LtiStrings.LTI_LINK, Map.of("id", "rl-1"));
        claims.put(LtiStrings.LTI_PLATFORM, Map.of("name", "Canvas"));
        claims.put(LtiStrings.LTI_LAUNCH_PRESENTATION, Map.of("document_target", "iframe"));
        claims.put(LtiStrings.LTI_CUSTOM, Map.of("canvas_course_id", "409"));
        claims.put(LtiStrings.LTI_ENDPOINT, Map.of("lineitems", "https://lms/lineitems"));
        claims.put(LtiStrings.LTI_NAMES_ROLE_SERVICE, Map.of("context_memberships_url", "https://lms/memberships"));
        claims.put(LtiStrings.LTI_PLATFORM_NOTIFICATION_SERVICE, Map.of("platform_notification_service_url", "https://lms/notices"));
        claims.put(LtiStrings.DEEP_LINKING_SETTINGS, Map.of("deep_link_return_url", "https://lms/return"));
        return claims;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDescribesTheLaunch() {
        LaunchRecord.Content content = new LaunchRecord.Content("1234", "Quiz", "A quiz", true, 10f);
        Instant launchedAt = Instant.parse("2026-11-12T17:00:00Z");
        Map<String, Object> claims = claims();
        when(launchStore.get("launch-nonce")).thenReturn(Optional.of(new LaunchRecord("id-token", "client-1", claims, content, launchedAt)));

        Map<String, Object> view = controller.launch(request);

        assertEquals("LtiResourceLinkRequest", view.get("messageType"));
        assertEquals("1.3.0", view.get("version"));
        assertEquals("https://canvas.instructure.com", view.get("issuer"));
        assertEquals("client-1", view.get("clientId"));
        assertEquals("284:abc", view.get("deploymentId"));
        assertEquals("https://tool.example.com/lti3", view.get("targetLinkUri"));
        assertEquals("2026-11-12T17:00:00Z", view.get("launchedAt"));
        Map<String, Object> user = (Map<String, Object>) view.get("user");
        assertEquals("user-1", user.get("id"));
        assertEquals("Ada Lovelace", user.get("name"));
        assertEquals("Ada", user.get("givenName"));
        assertEquals("Lovelace", user.get("familyName"));
        assertEquals("ada@example.edu", user.get("email"));
        assertEquals("https://lms/ada.png", user.get("picture"));
        assertEquals("en", user.get("locale"));
        assertEquals(List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"), user.get("roles"));
        assertEquals(Map.of("id", "ctx-1", "title", "Biology 101"), view.get("context"));
        assertEquals(Map.of("id", "rl-1"), view.get("resourceLink"));
        assertEquals(Map.of("name", "Canvas"), view.get("platform"));
        assertEquals(Map.of("document_target", "iframe"), view.get("launchPresentation"));
        assertEquals(Map.of("canvas_course_id", "409"), view.get("custom"));
        Map<String, Object> services = (Map<String, Object>) view.get("services");
        assertEquals(Map.of("lineitems", "https://lms/lineitems"), services.get("assignmentsAndGrades"));
        assertEquals(Map.of("context_memberships_url", "https://lms/memberships"), services.get("namesAndRoles"));
        assertEquals(Map.of("platform_notification_service_url", "https://lms/notices"), services.get("platformNotifications"));
        assertEquals(Map.of("deep_link_return_url", "https://lms/return"), view.get("deepLinking"));
        assertSame(content, view.get("content"));
        assertSame(claims, view.get("claims"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultsMissingRolesToAnEmptyList() {
        Map<String, Object> claims = claims();
        claims.remove(LtiStrings.LTI_ROLES);
        when(launchStore.get("launch-nonce")).thenReturn(Optional.of(new LaunchRecord("id-token", "client-1", claims, null, Instant.now())));

        Map<String, Object> view = controller.launch(request);

        assertEquals(List.of(), ((Map<String, Object>) view.get("user")).get("roles"));
        assertNull(view.get("content"));
    }

    @Test
    public void testAnExpiredLaunchIsNotFound() {
        when(launchStore.get("launch-nonce")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> controller.launch(request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("This launch has expired. Launch the tool again from your course.", exception.getReason());
    }

}
