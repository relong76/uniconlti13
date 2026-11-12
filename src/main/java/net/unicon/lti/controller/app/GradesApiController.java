package net.unicon.lti.controller.app;

import jakarta.servlet.http.HttpServletRequest;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.ags.LineItem;
import net.unicon.lti.model.ags.Result;
import net.unicon.lti.model.ags.Results;
import net.unicon.lti.model.ags.Score;
import net.unicon.lti.model.oauth2.LTIToken;
import net.unicon.lti.repository.LtiContextRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.lti.AdvantageAGSService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assignment and Grade Services for the caller's course.
 *
 * <p>AGS line item ids are full URLs. The API addresses a line item by its "key": the last path segment of
 * its id, or the segment before the last when the id has a query string (Moodle ends ids in
 * "/{id}/lineitem?type_id=..."). Each line item in a response carries its key; the app URL-encodes it
 * into the path.</p>
 */
@RestController
@RequestMapping(value = "/api/grades/lineitems", produces = MediaType.APPLICATION_JSON_VALUE)
public class GradesApiController extends AdvantageApiSupport {

    static final Set<String> ACTIVITY_PROGRESS = Set.of("Initialized", "Started", "InProgress", "Submitted", "Completed");
    static final Set<String> GRADING_PROGRESS = Set.of("FullyGraded", "Pending", "PendingManual", "Failed", "NotReady");

    private final AdvantageAGSService agsService;

    public GradesApiController(ApiSessions apiSessions, PlatformDeploymentRepository platformDeploymentRepository,
                               LtiContextRepository ltiContextRepository, AdvantageAGSService agsService) {
        super(apiSessions, platformDeploymentRepository, ltiContextRepository);
        this.agsService = agsService;
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) throws ConnectionException {
        Course course = gradedCourse(request);
        LTIToken lineItemsToken = agsService.getToken("lineitems", course.deployment());
        LTIToken resultsToken = agsService.getToken("results", course.deployment());

        return agsService.getLineItems(lineItemsToken, course.context(), true, resultsToken).getLineItemList().stream()
            .map(GradesApiController::describe)
            .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request, @RequestBody LineItem lineItem) throws ConnectionException {
        Course course = gradedCourse(request);
        validate(lineItem);
        lineItem.setId(null);
        lineItem.setResults(null);
        agsService.cleanLineItem(lineItem);

        LTIToken lineItemsToken = agsService.getToken("lineitems", course.deployment());
        List<LineItem> created = agsService.postLineItem(lineItemsToken, course.context(), lineItem).getLineItemList();

        if (created.isEmpty() || created.getFirst() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The platform didn't return the new line item.");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(describe(created.getFirst()));
    }

    @GetMapping("/{key}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable("key") String key) throws ConnectionException {
        Course course = gradedCourse(request);
        LTIToken lineItemsToken = agsService.getToken("lineitems", course.deployment());
        LTIToken resultsToken = agsService.getToken("results", course.deployment());

        return describe(agsService.getLineItem(lineItemsToken, resultsToken, course.context(), key));
    }

    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> update(HttpServletRequest request, @PathVariable("key") String key,
                                      @RequestBody LineItem lineItem) throws ConnectionException {
        Course course = gradedCourse(request);
        validate(lineItem);
        lineItem.setResults(null);
        agsService.cleanLineItem(lineItem);
        lineItem.setId(key);

        LTIToken lineItemsToken = agsService.getToken("lineitems", course.deployment());
        LineItem updated = agsService.putLineItem(lineItemsToken, course.context(), lineItem);

        return describe(updated != null ? updated : lineItem);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable("key") String key) throws ConnectionException {
        Course course = gradedCourse(request);
        LTIToken lineItemsToken = agsService.getToken("lineitems", course.deployment());
        agsService.deleteLineItem(lineItemsToken, course.context(), key);

        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{key}/scores", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> postScore(HttpServletRequest request, @PathVariable("key") String key,
                                         @RequestBody Score score) throws ConnectionException {
        Course course = gradedCourse(request);
        validate(score);

        LTIToken lineItemsToken = agsService.getToken("lineitems", course.deployment());
        LTIToken resultsToken = agsService.getToken("results", course.deployment());
        LTIToken scoresToken = agsService.getToken("scores", course.deployment());

        LineItem lineItem = agsService.getLineItem(lineItemsToken, resultsToken, course.context(), key);

        if (StringUtils.isNotBlank(score.getScoreGiven()) && StringUtils.isBlank(score.getScoreMaximum())) {
            score.setScoreMaximum(lineItem.getScoreMaximum());
        }
        if (StringUtils.isBlank(score.getScoreGiven())) {
            score.setScoreGiven(null);
            score.setScoreMaximum(null);
        }
        if (score.getComment() == null) {
            score.setComment("");
        }
        if (StringUtils.isBlank(score.getTimestamp())) {
            score.setTimestamp(Instant.now().toString());
        }

        lineItem.setResults(agsService.postScore(scoresToken, resultsToken, course.context(), lineItem.getId(), score));

        return describe(lineItem);
    }

    private Course gradedCourse(HttpServletRequest request) {
        Course course = course(request);

        if (StringUtils.isBlank(course.context().getLineitems())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This course doesn't offer Assignment and Grade Services to the tool.");
        }

        return course;
    }

    static void validate(LineItem lineItem) {
        if (StringUtils.isBlank(lineItem.getLabel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a label.");
        }
        if (!isPositive(lineItem.getScoreMaximum())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a maximum score greater than 0.");
        }
    }

    static void validate(Score score) {
        if (StringUtils.isBlank(score.getUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a user to score.");
        }
        if (score.getActivityProgress() == null || !ACTIVITY_PROGRESS.contains(score.getActivityProgress())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an activity progress value.");
        }
        if (score.getGradingProgress() == null || !GRADING_PROGRESS.contains(score.getGradingProgress())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a grading progress value.");
        }
        if (StringUtils.isNotBlank(score.getScoreGiven()) && !isNumber(score.getScoreGiven())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the score as a number.");
        }
        if (StringUtils.isNotBlank(score.getScoreMaximum()) && !isPositive(score.getScoreMaximum())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a maximum score greater than 0.");
        }
    }

    private static boolean isNumber(String value) {
        try {
            return Double.isFinite(Double.parseDouble(value.strip()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isPositive(String value) {
        return value != null && isNumber(value) && Double.parseDouble(value.strip()) > 0;
    }

    // the last path segment, or the one before it when the id has a query string
    static String key(String id) {
        if (id == null) {
            return null;
        }

        String[] segments = id.split("/");
        int index = segments.length - (id.contains("?") && segments.length > 1 ? 2 : 1);

        return segments[index];
    }

    static Map<String, Object> describe(LineItem lineItem) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("key", key(lineItem.getId()));
        view.put("id", lineItem.getId());
        view.put("label", lineItem.getLabel());
        view.put("scoreMaximum", lineItem.getScoreMaximum());
        view.put("tag", lineItem.getTag());
        view.put("resourceId", lineItem.getResourceId());
        view.put("resourceLinkId", lineItem.getResourceLinkId());
        view.put("startDateTime", lineItem.getStartDateTime());
        view.put("endDateTime", lineItem.getEndDateTime());

        Results results = lineItem.getResults();
        List<Result> resultList = results != null ? results.getResultList() : List.of();
        view.put("results", resultList.stream().map(GradesApiController::describe).toList());

        return view;
    }

    static Map<String, Object> describe(Result result) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", result.getId());
        view.put("userId", result.getUserId());
        view.put("resultScore", result.getResultScore());
        view.put("resultMaximum", result.getResultMaximum());
        view.put("comment", result.getComment());
        return view;
    }

}
