package net.unicon.lti.controller.app;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.unicon.lti.model.LaunchRecord;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.app.LaunchStore;
import net.unicon.lti.utils.LtiStrings;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The launch the app was opened from, grouped for display.
 */
@RestController
@RequestMapping(value = "/api/launch", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LaunchApiController {

    private final ApiSessions apiSessions;
    private final LaunchStore launchStore;

    @GetMapping
    public Map<String, Object> launch(HttpServletRequest request) {
        Claims session = apiSessions.require(request);
        LaunchRecord launch = launchStore.get(session.get("nonce", String.class))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This launch has expired. Launch the tool again from your course."));

        return describe(launch);
    }

    static Map<String, Object> describe(LaunchRecord launch) {
        Map<String, Object> claims = launch.claims();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("messageType", claims.get(LtiStrings.LTI_MESSAGE_TYPE));
        view.put("version", claims.get(LtiStrings.LTI_VERSION));
        view.put("issuer", claims.get("iss"));
        view.put("clientId", launch.clientId());
        view.put("deploymentId", claims.get(LtiStrings.LTI_DEPLOYMENT_ID));
        view.put("targetLinkUri", claims.get(LtiStrings.LTI_TARGET_LINK_URI));
        view.put("launchedAt", launch.launchedAt().toString());

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", claims.get("sub"));
        user.put("name", claims.get(LtiStrings.LTI_NAME));
        user.put("givenName", claims.get(LtiStrings.LTI_GIVEN_NAME));
        user.put("familyName", claims.get(LtiStrings.LTI_FAMILY_NAME));
        user.put("email", claims.get(LtiStrings.LTI_EMAIL));
        user.put("picture", claims.get(LtiStrings.LTI_PICTURE));
        user.put("locale", claims.get(LtiStrings.LTI_LOCALE));
        user.put("roles", claims.getOrDefault(LtiStrings.LTI_ROLES, List.of()));
        view.put("user", user);

        view.put("context", claims.get(LtiStrings.LTI_CONTEXT));
        view.put("resourceLink", claims.get(LtiStrings.LTI_LINK));
        view.put("platform", claims.get(LtiStrings.LTI_PLATFORM));
        view.put("launchPresentation", claims.get(LtiStrings.LTI_LAUNCH_PRESENTATION));
        view.put("custom", claims.get(LtiStrings.LTI_CUSTOM));

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("assignmentsAndGrades", claims.get(LtiStrings.LTI_ENDPOINT));
        services.put("namesAndRoles", claims.get(LtiStrings.LTI_NAMES_ROLE_SERVICE));
        services.put("platformNotifications", claims.get(LtiStrings.LTI_PLATFORM_NOTIFICATION_SERVICE));
        view.put("services", services);

        view.put("deepLinking", claims.get(LtiStrings.DEEP_LINKING_SETTINGS));
        view.put("content", launch.content());
        view.put("claims", claims);

        return view;
    }

}
