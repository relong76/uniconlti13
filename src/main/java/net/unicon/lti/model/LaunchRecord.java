package net.unicon.lti.model;

import java.time.Instant;
import java.util.Map;

/**
 * A completed LTI launch, kept so the app the launch hands off to can show it and act on it.
 *
 * @param idToken    the platform's signed id_token, which a deep linking response is built from
 * @param clientId   the client id the launch was for
 * @param claims     the id_token's validated claims
 * @param content    the demo content the launch asked for, or null if it named none
 * @param launchedAt when the launch completed
 */
public record LaunchRecord(String idToken, String clientId, Map<String, Object> claims, Content content, Instant launchedAt) {

    /**
     * A tool link the launch named with ?link=, as found in the course.
     */
    public record Content(String toolLinkId, String title, String description, Boolean assignment, Float maxGrade) { }

}
