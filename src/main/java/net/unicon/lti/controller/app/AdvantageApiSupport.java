package net.unicon.lti.controller.app;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.LtiContextEntity;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.repository.LtiContextRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.app.ApiSessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Shared by the API controllers that call LTI Advantage services for the caller's course: finds the
 * deployment and context named in the API token, and answers errors as {"status": ..., "message": ...}.
 */
public abstract class AdvantageApiSupport {

    private static final Logger log = LoggerFactory.getLogger(AdvantageApiSupport.class);

    static final String COURSE_NOT_FOUND = "The course for this launch wasn't found. Launch the tool again from your course.";

    protected final ApiSessions apiSessions;
    protected final PlatformDeploymentRepository platformDeploymentRepository;
    protected final LtiContextRepository ltiContextRepository;

    protected AdvantageApiSupport(ApiSessions apiSessions, PlatformDeploymentRepository platformDeploymentRepository,
                                  LtiContextRepository ltiContextRepository) {
        this.apiSessions = apiSessions;
        this.platformDeploymentRepository = platformDeploymentRepository;
        this.ltiContextRepository = ltiContextRepository;
    }

    public record Course(PlatformDeployment deployment, LtiContextEntity context) { }

    protected Course course(HttpServletRequest request) {
        Claims session = apiSessions.require(request);
        Object deploymentId = session.get("platformDeploymentId");
        Object contextKey = session.get("contextKey");

        if (deploymentId == null || contextKey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND);
        }

        PlatformDeployment deployment;
        try {
            deployment = platformDeploymentRepository.findById(Long.parseLong(deploymentId.toString()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND);
        }

        LtiContextEntity context = ltiContextRepository.findByContextKeyAndPlatformDeployment(contextKey.toString(), deployment);

        if (context == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND);
        }

        return new Course(deployment, context);
    }

    static Map<String, Object> error(HttpStatusCode status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        return body;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "The request failed.";
        return ResponseEntity.status(e.getStatusCode()).body(error(e.getStatusCode(), message));
    }

    @ExceptionHandler(ConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnection(ConnectionException e) {
        log.warn("LTI Advantage call failed: {}", e.getMessage());
        String detail = Objects.toString(e.getMessage(), "").replaceAll("\\s+", " ").strip();
        String message = ("The platform didn't complete the request. " + detail).strip();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(HttpStatus.BAD_GATEWAY, message));
    }

}
