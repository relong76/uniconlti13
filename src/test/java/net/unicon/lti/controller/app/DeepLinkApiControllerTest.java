package net.unicon.lti.controller.app;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import net.unicon.lti.model.LaunchRecord;
import net.unicon.lti.model.lti.dto.DeepLinkDTO;
import net.unicon.lti.model.lti.dto.DeepLinkJWTDTO;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.app.LaunchStore;
import net.unicon.lti.service.lti.DeepLinkService;
import net.unicon.lti.service.lti.LTIJWTService;
import net.unicon.lti.utils.LtiStrings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeepLinkApiControllerTest {

    private static final String RETURN_URL = "https://lms/deep_links";

    @Mock
    private ApiSessions apiSessions;

    @Mock
    private LaunchStore launchStore;

    @Mock
    private DeepLinkService deepLinkService;

    @Mock
    private LTIJWTService ltijwtService;

    @Mock
    private Jws<Claims> idToken;

    private DeepLinkApiController controller;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final List<DeepLinkDTO> items = List.of(
        new DeepLinkDTO("quiz", "Quiz", "A quiz", true, 10f),
        new DeepLinkDTO("page", "Page", "A page", false, null)
    );

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new DeepLinkApiController(apiSessions, launchStore, deepLinkService, ltijwtService);
        when(apiSessions.require(request)).thenReturn(Jwts.claims().add("nonce", "launch-nonce").build());
        when(deepLinkService.getDeepLinks()).thenReturn(items);
    }

    private static Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(LtiStrings.DEEP_LINK_RETURN_URL, RETURN_URL);
        settings.put(LtiStrings.DEEP_LINK_ACCEPT_TYPES, List.of("ltiResourceLink", "link"));
        settings.put(LtiStrings.DEEP_LINK_ACCEPT_MULTIPLE, true);
        return settings;
    }

    private void launch(String messageType, Object settings) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(LtiStrings.LTI_MESSAGE_TYPE, messageType);
        if (settings != null) {
            claims.put(LtiStrings.DEEP_LINKING_SETTINGS, settings);
        }
        when(launchStore.get("launch-nonce")).thenReturn(Optional.of(new LaunchRecord("id-token", "client-1", claims, null, Instant.now())));
    }

    private void deepLinkingLaunch(Map<String, Object> settings) {
        launch(LtiStrings.LTI_MESSAGE_TYPE_DEEP_LINKING, settings);
    }

    private static void assertStatus(HttpStatus status, String reason, Executable call) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, call);
        assertEquals(status, exception.getStatusCode());
        assertEquals(reason, exception.getReason());
    }

    private DeepLinkApiController.DeepLinkResponse send(String... ids) {
        return controller.response(request, new DeepLinkApiController.Selection(Arrays.asList(ids)));
    }

    @Test
    public void testItemsListsTheContent() {
        deepLinkingLaunch(settings());

        assertSame(items, controller.items(request));
    }

    @Test
    public void testItemsNeedAnUnexpiredLaunch() {
        when(launchStore.get("launch-nonce")).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, DeepLinkApiController.EXPIRED, () -> controller.items(request));
    }

    @Test
    public void testItemsNeedADeepLinkingLaunch() {
        launch(LtiStrings.LTI_MESSAGE_TYPE_RESOURCE_LINK, null);

        assertStatus(HttpStatus.FORBIDDEN, "This launch isn't for choosing content. Add content from your course's deep linking option.",
            () -> controller.items(request));
        verify(deepLinkService, never()).getDeepLinks();
    }

    @Test
    public void testResponseSignsTheSelection() throws Exception {
        deepLinkingLaunch(settings());
        when(ltijwtService.validateJWT("id-token", "client-1")).thenReturn(idToken);
        when(deepLinkService.generateDeepLinkJWT(List.of("quiz", "page"), idToken)).thenReturn(new DeepLinkJWTDTO(List.of(), "signed"));

        DeepLinkApiController.DeepLinkResponse response = send("quiz", null, "page", "quiz");

        assertEquals(RETURN_URL, response.returnUrl());
        assertEquals("signed", response.jwt());
    }

    @Test
    public void testResponseAllowsOneItemWithoutAcceptMultipleOrAcceptTypes() throws Exception {
        Map<String, Object> settings = settings();
        settings.remove(LtiStrings.DEEP_LINK_ACCEPT_MULTIPLE);
        settings.remove(LtiStrings.DEEP_LINK_ACCEPT_TYPES);
        deepLinkingLaunch(settings);
        when(ltijwtService.validateJWT("id-token", "client-1")).thenReturn(idToken);
        when(deepLinkService.generateDeepLinkJWT(List.of("page"), idToken)).thenReturn(new DeepLinkJWTDTO(List.of(), "signed"));

        assertEquals("signed", send("page").jwt());
    }

    @Test
    public void testResponseNeedsAnUnexpiredLaunch() {
        when(launchStore.get("launch-nonce")).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, DeepLinkApiController.EXPIRED, () -> send("quiz"));
    }

    @Test
    public void testResponseNeedsADeepLinkingLaunch() {
        launch(LtiStrings.LTI_MESSAGE_TYPE_RESOURCE_LINK, settings());

        assertStatus(HttpStatus.FORBIDDEN, "This launch isn't for choosing content. Add content from your course's deep linking option.",
            () -> send("quiz"));
    }

    @Test
    public void testResponseNeedsASelection() {
        deepLinkingLaunch(settings());
        String reason = "Choose at least one item to add to your course.";

        assertStatus(HttpStatus.BAD_REQUEST, reason, () -> controller.response(request, null));
        assertStatus(HttpStatus.BAD_REQUEST, reason, () -> controller.response(request, new DeepLinkApiController.Selection(null)));
        assertStatus(HttpStatus.BAD_REQUEST, reason, () -> send());
    }

    @Test
    public void testResponseRejectsSeveralItemsUnlessAcceptMultiple() {
        Map<String, Object> settings = settings();
        settings.put(LtiStrings.DEEP_LINK_ACCEPT_MULTIPLE, false);
        deepLinkingLaunch(settings);

        assertStatus(HttpStatus.BAD_REQUEST, "Your course accepts only one item at a time. Choose one item.", () -> send("quiz", "page"));
    }

    @Test
    public void testResponseRejectsAPlatformThatDoesNotAcceptLtiLinks() {
        Map<String, Object> settings = settings();
        settings.put(LtiStrings.DEEP_LINK_ACCEPT_TYPES, List.of("file"));
        deepLinkingLaunch(settings);

        assertStatus(HttpStatus.BAD_REQUEST, "Your course doesn't accept LTI links from this tool here.", () -> send("quiz"));
    }

    @Test
    public void testResponseRejectsUnknownItems() {
        deepLinkingLaunch(settings());

        assertStatus(HttpStatus.BAD_REQUEST, "These items aren't available: nope, gone", () -> send("quiz", "nope", "gone"));
    }

    @Test
    public void testResponseNeedsAReturnUrl() {
        Map<String, Object> blank = settings();
        blank.put(LtiStrings.DEEP_LINK_RETURN_URL, " ");
        Map<String, Object> missing = settings();
        missing.remove(LtiStrings.DEEP_LINK_RETURN_URL);
        String reason = "The platform didn't say where to send the content.";

        deepLinkingLaunch(blank);
        assertStatus(HttpStatus.BAD_REQUEST, reason, () -> send("quiz"));

        deepLinkingLaunch(missing);
        assertStatus(HttpStatus.BAD_REQUEST, reason, () -> send("quiz"));
    }

    @Test
    public void testResponseWithoutDeepLinkingSettings() {
        launch(LtiStrings.LTI_MESSAGE_TYPE_DEEP_LINKING, "not a map");

        assertStatus(HttpStatus.BAD_REQUEST, "The platform didn't say where to send the content.", () -> send("quiz"));
    }

    @Test
    public void testResponseWhenTheIdTokenHasExpired() {
        deepLinkingLaunch(settings());
        when(ltijwtService.validateJWT("id-token", "client-1")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertStatus(HttpStatus.NOT_FOUND, DeepLinkApiController.EXPIRED, () -> send("quiz"));
    }

    @Test
    public void testResponseWhenTheIdTokenNoLongerValidates() {
        deepLinkingLaunch(settings());
        String reason = "The launch couldn't be verified again. Launch the tool again from your course.";

        when(ltijwtService.validateJWT("id-token", "client-1"))
            .thenThrow(new SignatureException("bad signature"), new IllegalArgumentException("no key"));
        assertStatus(HttpStatus.BAD_REQUEST, reason, () -> send("quiz"));
        assertStatus(HttpStatus.BAD_REQUEST, reason, () -> send("quiz"));
    }

    @Test
    public void testResponseWhenSigningFails() throws Exception {
        deepLinkingLaunch(settings());
        when(ltijwtService.validateJWT("id-token", "client-1")).thenReturn(idToken);
        String reason = "The response to your course couldn't be signed.";

        when(deepLinkService.generateDeepLinkJWT(anyList(), any()))
            .thenThrow(new GeneralSecurityException("no key"), new IOException("no file"));
        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, reason, () -> send("quiz"));
        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, reason, () -> send("quiz"));
    }

}
