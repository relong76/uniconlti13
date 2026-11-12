package net.unicon.lti.controller.app;

import io.jsonwebtoken.Jwts;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.LtiContextEntity;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.model.ags.LineItem;
import net.unicon.lti.model.ags.LineItems;
import net.unicon.lti.model.ags.Result;
import net.unicon.lti.model.ags.Results;
import net.unicon.lti.model.ags.Score;
import net.unicon.lti.model.oauth2.LTIToken;
import net.unicon.lti.repository.LtiContextRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.lti.AdvantageAGSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class GradesApiControllerTest {

    private static final String LINEITEMS = "https://lms/api/lti/courses/1/line_items";

    @Mock
    private ApiSessions apiSessions;

    @Mock
    private PlatformDeploymentRepository platformDeploymentRepository;

    @Mock
    private LtiContextRepository ltiContextRepository;

    @Mock
    private AdvantageAGSService agsService;

    private GradesApiController controller;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final PlatformDeployment deployment = new PlatformDeployment();
    private final LtiContextEntity context = new LtiContextEntity();
    private final LTIToken lineItemsToken = new LTIToken("li", "Bearer", 3600, "lineitem");
    private final LTIToken resultsToken = new LTIToken("res", "Bearer", 3600, "result");
    private final LTIToken scoresToken = new LTIToken("sc", "Bearer", 3600, "score");

    @BeforeEach
    public void setUp() throws ConnectionException {
        MockitoAnnotations.openMocks(this);
        controller = new GradesApiController(apiSessions, platformDeploymentRepository, ltiContextRepository, agsService);
        context.setLineitems(LINEITEMS);
        when(apiSessions.require(request)).thenReturn(Jwts.claims().add("platformDeploymentId", 1).add("contextKey", "ctx").build());
        when(platformDeploymentRepository.findById(1L)).thenReturn(Optional.of(deployment));
        when(ltiContextRepository.findByContextKeyAndPlatformDeployment("ctx", deployment)).thenReturn(context);
        when(agsService.getToken("lineitems", deployment)).thenReturn(lineItemsToken);
        when(agsService.getToken("results", deployment)).thenReturn(resultsToken);
        when(agsService.getToken("scores", deployment)).thenReturn(scoresToken);
    }

    private static LineItem lineItem(String id, String label) {
        LineItem lineItem = new LineItem();
        lineItem.setId(id);
        lineItem.setLabel(label);
        lineItem.setScoreMaximum("10");
        return lineItem;
    }

    private static Result result(String userId, String score) {
        Result result = new Result();
        result.setId("r-" + userId);
        result.setUserId(userId);
        result.setResultScore(score);
        result.setResultMaximum("10");
        result.setComment("Nice");
        return result;
    }

    private static Results results(Result... list) {
        Results results = new Results();
        results.getResultList().addAll(List.of(list));
        return results;
    }

    private static Score score() {
        Score score = new Score();
        score.setUserId("user-1");
        score.setScoreGiven("8");
        score.setActivityProgress("Completed");
        score.setGradingProgress("FullyGraded");
        return score;
    }

    private static void assertStatus(HttpStatus status, String message, Runnable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(status, e.getStatusCode());
        assertEquals(message, e.getReason());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListsLineItemsWithResults() throws ConnectionException {
        LineItem withResults = lineItem(LINEITEMS + "/5", "Quiz");
        withResults.setTag("quiz");
        withResults.setResourceId("res-1");
        withResults.setResourceLinkId("rl-1");
        withResults.setStartDateTime("2026-11-01T00:00:00Z");
        withResults.setEndDateTime("2026-11-30T00:00:00Z");
        withResults.setResults(results(result("user-1", "8")));
        LineItems lineItems = new LineItems();
        lineItems.getLineItemList().addAll(List.of(withResults, lineItem(LINEITEMS + "/6", "Essay")));
        when(agsService.getLineItems(lineItemsToken, context, true, resultsToken)).thenReturn(lineItems);

        List<Map<String, Object>> list = controller.list(request);

        assertEquals(2, list.size());
        Map<String, Object> first = list.getFirst();
        assertEquals("5", first.get("key"));
        assertEquals(LINEITEMS + "/5", first.get("id"));
        assertEquals("Quiz", first.get("label"));
        assertEquals("10", first.get("scoreMaximum"));
        assertEquals("quiz", first.get("tag"));
        assertEquals("res-1", first.get("resourceId"));
        assertEquals("rl-1", first.get("resourceLinkId"));
        assertEquals("2026-11-01T00:00:00Z", first.get("startDateTime"));
        assertEquals("2026-11-30T00:00:00Z", first.get("endDateTime"));
        List<Map<String, Object>> firstResults = (List<Map<String, Object>>) first.get("results");
        assertEquals(Map.of("id", "r-user-1", "userId", "user-1", "resultScore", "8", "resultMaximum", "10", "comment", "Nice"), firstResults.getFirst());
        assertEquals(List.of(), list.get(1).get("results"));
    }

    @Test
    public void testCourseWithoutGrades() {
        context.setLineitems(" ");
        assertStatus(HttpStatus.NOT_FOUND, "This course doesn't offer Assignment and Grade Services to the tool.", () -> {
            try {
                controller.list(request);
            } catch (ConnectionException e) {
                throw new IllegalStateException(e);
            }
        });
        verifyNoInteractions(agsService);
    }

    @Test
    public void testListPassesOnConnectionErrors() throws ConnectionException {
        when(agsService.getLineItems(lineItemsToken, context, true, resultsToken)).thenThrow(new ConnectionException("Can't get the AGS"));
        assertThrows(ConnectionException.class, () -> controller.list(request));
    }

    @Test
    public void testCreatesALineItem() throws ConnectionException {
        LineItem body = lineItem("ignored", "Quiz");
        body.setResults(new Results());
        LineItems created = new LineItems();
        created.getLineItemList().add(lineItem(LINEITEMS + "/9", "Quiz"));
        when(agsService.postLineItem(lineItemsToken, context, body)).thenReturn(created);

        ResponseEntity<Map<String, Object>> response = controller.create(request, body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("9", response.getBody().get("key"));
        assertNull(body.getId());
        assertNull(body.getResults());
        verify(agsService).cleanLineItem(body);
    }

    @Test
    public void testCreateWithoutAnAnswer() throws ConnectionException {
        LineItem body = lineItem(null, "Quiz");
        when(agsService.postLineItem(lineItemsToken, context, body)).thenReturn(new LineItems());
        assertStatus(HttpStatus.BAD_GATEWAY, "The platform didn't return the new line item.", () -> create(body));

        LineItems withNull = new LineItems();
        withNull.getLineItemList().add(null);
        when(agsService.postLineItem(lineItemsToken, context, body)).thenReturn(withNull);
        assertStatus(HttpStatus.BAD_GATEWAY, "The platform didn't return the new line item.", () -> create(body));
    }

    private void create(LineItem body) {
        try {
            controller.create(request, body);
        } catch (ConnectionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testValidatesLineItems() {
        assertStatus(HttpStatus.BAD_REQUEST, "Enter a label.", () -> GradesApiController.validate(lineItem(null, " ")));

        LineItem lineItem = lineItem(null, "Quiz");
        for (String max : new String[]{null, "", "zero", "0", "-1", "NaN", "Infinity"}) {
            lineItem.setScoreMaximum(max);
            assertStatus(HttpStatus.BAD_REQUEST, "Enter a maximum score greater than 0.", () -> GradesApiController.validate(lineItem));
        }

        lineItem.setScoreMaximum(" 12.5 ");
        GradesApiController.validate(lineItem);
    }

    @Test
    public void testCreateRejectsAnInvalidLineItem() {
        assertStatus(HttpStatus.BAD_REQUEST, "Enter a label.", () -> create(lineItem(null, null)));
        verifyNoInteractions(agsService);
    }

    @Test
    public void testGetsALineItem() throws ConnectionException {
        LineItem lineItem = lineItem(LINEITEMS + "/5", "Quiz");
        when(agsService.getLineItem(lineItemsToken, resultsToken, context, "5")).thenReturn(lineItem);

        Map<String, Object> view = controller.get(request, "5");

        assertEquals("Quiz", view.get("label"));
        assertEquals(List.of(), view.get("results"));
    }

    @Test
    public void testUpdatesALineItem() throws ConnectionException {
        LineItem body = lineItem(null, "Quiz 2");
        body.setResults(new Results());
        LineItem updated = lineItem(LINEITEMS + "/5", "Quiz 2");
        when(agsService.putLineItem(lineItemsToken, context, body)).thenReturn(updated);

        Map<String, Object> view = controller.update(request, "5", body);

        assertEquals("5", body.getId());
        assertNull(body.getResults());
        assertEquals(LINEITEMS + "/5", view.get("id"));
        verify(agsService).cleanLineItem(body);
    }

    @Test
    public void testUpdateWithoutABody() throws ConnectionException {
        LineItem body = lineItem(null, "Quiz 2");
        when(agsService.putLineItem(lineItemsToken, context, body)).thenReturn(null);

        Map<String, Object> view = controller.update(request, "5", body);

        assertEquals("5", view.get("key"));
        assertEquals("Quiz 2", view.get("label"));
    }

    @Test
    public void testDeletesALineItem() throws ConnectionException {
        ResponseEntity<Void> response = controller.delete(request, "5");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(agsService).deleteLineItem(lineItemsToken, context, "5");
    }

    @Test
    public void testPostsAScore() throws ConnectionException {
        LineItem lineItem = lineItem(LINEITEMS + "/5", "Quiz");
        when(agsService.getLineItem(lineItemsToken, resultsToken, context, "5")).thenReturn(lineItem);
        when(agsService.postScore(eq(scoresToken), eq(resultsToken), eq(context), eq(LINEITEMS + "/5"), any(Score.class)))
            .thenReturn(results(result("user-1", "8")));
        Score score = score();
        Instant before = Instant.now();

        Map<String, Object> view = controller.postScore(request, "5", score);

        assertEquals(1, ((List<?>) view.get("results")).size());
        assertEquals("10", score.getScoreMaximum());
        assertEquals("", score.getComment());
        assertNotNull(score.getTimestamp());
        assertEquals(false, Instant.parse(score.getTimestamp()).isBefore(before));
    }

    @Test
    public void testPostsAScoreAsGiven() throws ConnectionException {
        LineItem lineItem = lineItem(LINEITEMS + "/5", "Quiz");
        when(agsService.getLineItem(lineItemsToken, resultsToken, context, "5")).thenReturn(lineItem);
        when(agsService.postScore(any(), any(), any(), anyString(), any())).thenReturn(new Results());
        Score score = score();
        score.setScoreMaximum("20");
        score.setComment("Good");
        score.setTimestamp("2026-11-12T17:00:00Z");

        controller.postScore(request, "5", score);

        ArgumentCaptor<Score> sent = ArgumentCaptor.forClass(Score.class);
        verify(agsService).postScore(eq(scoresToken), eq(resultsToken), eq(context), eq(LINEITEMS + "/5"), sent.capture());
        assertSame(score, sent.getValue());
        assertEquals("20", score.getScoreMaximum());
        assertEquals("Good", score.getComment());
        assertEquals("2026-11-12T17:00:00Z", score.getTimestamp());
    }

    @Test
    public void testPostsProgressWithoutAScore() throws ConnectionException {
        when(agsService.getLineItem(lineItemsToken, resultsToken, context, "5")).thenReturn(lineItem(LINEITEMS + "/5", "Quiz"));
        when(agsService.postScore(any(), any(), any(), anyString(), any())).thenReturn(new Results());
        Score score = score();
        score.setScoreGiven(" ");
        score.setScoreMaximum("10");
        score.setActivityProgress("Started");
        score.setGradingProgress("NotReady");

        controller.postScore(request, "5", score);

        assertNull(score.getScoreGiven());
        assertNull(score.getScoreMaximum());
    }

    @Test
    public void testValidatesScores() {
        Score score = score();
        score.setUserId(" ");
        assertStatus(HttpStatus.BAD_REQUEST, "Choose a user to score.", () -> GradesApiController.validate(score));

        Score activity = score();
        activity.setActivityProgress("Done");
        assertStatus(HttpStatus.BAD_REQUEST, "Choose an activity progress value.", () -> GradesApiController.validate(activity));

        Score noActivity = score();
        noActivity.setActivityProgress(null);
        assertStatus(HttpStatus.BAD_REQUEST, "Choose an activity progress value.", () -> GradesApiController.validate(noActivity));

        Score badGrading = score();
        badGrading.setGradingProgress("Graded");
        assertStatus(HttpStatus.BAD_REQUEST, "Choose a grading progress value.", () -> GradesApiController.validate(badGrading));

        Score grading = score();
        grading.setGradingProgress(null);
        assertStatus(HttpStatus.BAD_REQUEST, "Choose a grading progress value.", () -> GradesApiController.validate(grading));

        Score given = score();
        given.setScoreGiven("eight");
        assertStatus(HttpStatus.BAD_REQUEST, "Enter the score as a number.", () -> GradesApiController.validate(given));

        Score maximum = score();
        maximum.setScoreMaximum("0");
        assertStatus(HttpStatus.BAD_REQUEST, "Enter a maximum score greater than 0.", () -> GradesApiController.validate(maximum));

        Score progressOnly = score();
        progressOnly.setScoreGiven(null);
        progressOnly.setScoreMaximum(" ");
        GradesApiController.validate(progressOnly);
    }

    @Test
    public void testPostScoreRejectsAnInvalidScore() {
        Score score = score();
        score.setUserId(null);
        assertStatus(HttpStatus.BAD_REQUEST, "Choose a user to score.", () -> {
            try {
                controller.postScore(request, "5", score);
            } catch (ConnectionException e) {
                throw new IllegalStateException(e);
            }
        });
        verifyNoInteractions(agsService);
    }

    @Test
    public void testKeys() {
        assertNull(GradesApiController.key(null));
        assertEquals("5", GradesApiController.key("https://lms/api/lti/courses/1/line_items/5"));
        assertEquals("5", GradesApiController.key("https://moodle/mod/lti/services.php/2/lineitems/5/lineitem?type_id=1"));
        assertEquals("abc?x=1", GradesApiController.key("abc?x=1"));
        assertEquals("abc", GradesApiController.key("abc"));
    }

    @Test
    public void testDescribesALineItemWithoutResults() {
        Map<String, Object> view = GradesApiController.describe(new LineItem());

        assertNull(view.get("key"));
        assertEquals(List.of(), view.get("results"));
        assertEquals(new ArrayList<>(List.of("key", "id", "label", "scoreMaximum", "tag", "resourceId", "resourceLinkId",
            "startDateTime", "endDateTime", "results")), new ArrayList<>(view.keySet()));
    }

}
