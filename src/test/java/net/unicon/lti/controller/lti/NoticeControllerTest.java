package net.unicon.lti.controller.lti;

import net.unicon.lti.model.NoticeResult;
import net.unicon.lti.service.lti.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NoticeControllerTest {

    @InjectMocks
    private NoticeController noticeController;

    @Mock
    private NoticeService noticeService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testProcessesEachNoticeInTheBatch() {
        when(noticeService.processNotice("jwt-1")).thenReturn(NoticeResult.accepted("notice-1"));
        when(noticeService.processNotice("jwt-2")).thenReturn(NoticeResult.ignored("notice-2", "This tool doesn't handle LtiHelloWorldNotice."));

        ResponseEntity<Map<String, List<NoticeResult>>> response = noticeController.receive(
            Map.of("notices", List.of(Map.of("jwt", "jwt-1"), Map.of("jwt", "jwt-2")))
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(
            List.of(NoticeResult.accepted("notice-1"), NoticeResult.ignored("notice-2", "This tool doesn't handle LtiHelloWorldNotice.")),
            response.getBody().get("notices")
        );
    }

    @Test
    public void testEntryWithoutAJwtIsPassedOnAsMissing() {
        when(noticeService.processNotice(null)).thenReturn(NoticeResult.rejected(null, "The notice has no JWT."));
        Map<String, Object> noJwt = new HashMap<>();
        noJwt.put("jwt", null);

        ResponseEntity<Map<String, List<NoticeResult>>> response = noticeController.receive(
            Map.of("notices", List.of(Map.of(), noJwt, "not-an-object"))
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().get("notices").size());
        response.getBody().get("notices").forEach(result -> assertEquals(NoticeResult.STATUS_REJECTED, result.status()));
    }

    @Test
    public void testEmptyBatch() {
        ResponseEntity<Map<String, List<NoticeResult>>> response = noticeController.receive(Map.of("notices", List.of()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody().get("notices"));
    }

    @Test
    public void testBodyWithoutNoticesIsABadRequest() {
        ResponseEntity<Map<String, List<NoticeResult>>> response = noticeController.receive(Map.of("something", "else"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(noticeService, never()).processNotice(any());
    }

    @Test
    public void testNoticesThatAreNotAListIsABadRequest() {
        ResponseEntity<Map<String, List<NoticeResult>>> response = noticeController.receive(Map.of("notices", Map.of("jwt", "jwt-1")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(noticeService, never()).processNotice(any());
    }

}
