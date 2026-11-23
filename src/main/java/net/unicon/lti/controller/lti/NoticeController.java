package net.unicon.lti.controller.lti;

import lombok.RequiredArgsConstructor;
import net.unicon.lti.model.NoticeResult;
import net.unicon.lti.service.lti.NoticeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The notice handler platforms send LTI Platform Notification Service notices to. It has to be
 * public: the platform doesn't authenticate, and each notice is a JWT signed with the platform's
 * keys instead, which NoticeService checks.
 */
@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    // the platform posts a batch: {"notices": [{"jwt": "..."}, ...]}
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<NoticeResult>>> receive(@RequestBody Map<String, Object> body) {
        if (!(body.get("notices") instanceof List<?> notices)) {
            return ResponseEntity.badRequest().build();
        }

        List<NoticeResult> results = notices.stream()
            .map(notice -> notice instanceof Map<?, ?> map ? Objects.toString(map.get("jwt"), null) : null)
            .map(noticeService::processNotice)
            .toList();

        return ResponseEntity.ok(Map.of("notices", results));
    }

}
