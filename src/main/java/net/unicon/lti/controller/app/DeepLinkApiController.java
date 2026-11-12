package net.unicon.lti.controller.app;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.unicon.lti.model.LaunchRecord;
import net.unicon.lti.model.lti.dto.DeepLinkDTO;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.app.LaunchStore;
import net.unicon.lti.service.lti.DeepLinkService;
import net.unicon.lti.service.lti.LTIJWTService;
import net.unicon.lti.utils.LtiStrings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deep linking for the app: the content an instructor can add to the course, and the signed
 * LtiDeepLinkingResponse the app posts back to the platform.
 */
@RestController
@RequestMapping(value = "/api/deeplink", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DeepLinkApiController {

    static final String EXPIRED = "This launch has expired. Launch the tool again from your course.";

    private static final Logger log = LoggerFactory.getLogger(DeepLinkApiController.class);

    private final ApiSessions apiSessions;
    private final LaunchStore launchStore;
    private final DeepLinkService deepLinkService;
    private final LTIJWTService ltijwtService;

    public record Selection(List<String> selectedIds) { }

    public record DeepLinkResponse(String returnUrl, String jwt) { }

    @GetMapping("/items")
    public List<DeepLinkDTO> items(HttpServletRequest request) {
        deepLinkingLaunch(request);

        return deepLinkService.getDeepLinks();
    }

    @PostMapping(value = "/response", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeepLinkResponse response(HttpServletRequest request, @RequestBody(required = false) Selection selection) {
        LaunchRecord launch = deepLinkingLaunch(request);
        Map<?, ?> settings = settings(launch);
        List<String> selectedIds = selectedIds(selection);

        if (selectedIds.isEmpty()) {
            throw badRequest("Choose at least one item to add to your course.");
        }

        if (selectedIds.size() > 1 && !Boolean.TRUE.equals(settings.get(LtiStrings.DEEP_LINK_ACCEPT_MULTIPLE))) {
            throw badRequest("Your course accepts only one item at a time. Choose one item.");
        }

        Object acceptTypes = settings.get(LtiStrings.DEEP_LINK_ACCEPT_TYPES);
        if (acceptTypes instanceof List<?> types && !types.contains(LtiStrings.DEEP_LINK_LTIRESOURCELINK)) {
            throw badRequest("Your course doesn't accept LTI links from this tool here.");
        }

        Set<String> available = deepLinkService.getDeepLinks().stream()
            .map(DeepLinkDTO::getToolLinkId)
            .collect(Collectors.toSet());
        List<String> unknown = selectedIds.stream().filter(id -> !available.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw badRequest("These items aren't available: " + String.join(", ", unknown));
        }

        Object returnUrl = settings.get(LtiStrings.DEEP_LINK_RETURN_URL);
        if (!(returnUrl instanceof String url) || url.isBlank()) {
            throw badRequest("The platform didn't say where to send the content.");
        }

        try {
            Jws<Claims> idToken = ltijwtService.validateJWT(launch.idToken(), launch.clientId());

            return new DeepLinkResponse(url, deepLinkService.generateDeepLinkJWT(selectedIds, idToken).getJWTString());
        } catch (ExpiredJwtException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Could not validate the stored deep linking id_token again", e);
            throw badRequest("The launch couldn't be verified again. Launch the tool again from your course.");
        } catch (GeneralSecurityException | IOException e) {
            log.error("Could not sign the deep linking response", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The response to your course couldn't be signed.");
        }
    }

    private LaunchRecord deepLinkingLaunch(HttpServletRequest request) {
        Claims session = apiSessions.require(request);
        LaunchRecord launch = launchStore.get(session.get("nonce", String.class))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, EXPIRED));

        if (!LtiStrings.LTI_MESSAGE_TYPE_DEEP_LINKING.equals(launch.claims().get(LtiStrings.LTI_MESSAGE_TYPE))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This launch isn't for choosing content. Add content from your course's deep linking option.");
        }

        return launch;
    }

    private static Map<?, ?> settings(LaunchRecord launch) {
        return launch.claims().get(LtiStrings.DEEP_LINKING_SETTINGS) instanceof Map<?, ?> settings ? settings : Map.of();
    }

    private static List<String> selectedIds(Selection selection) {
        if (selection == null || selection.selectedIds() == null) {
            return List.of();
        }

        return new ArrayList<>(selection.selectedIds().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

}
