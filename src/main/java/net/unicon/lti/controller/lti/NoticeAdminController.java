package net.unicon.lti.controller.lti;

import lombok.RequiredArgsConstructor;
import net.unicon.lti.model.ContextCopyNotice;
import net.unicon.lti.repository.ContextCopyNoticeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists the course copy notices received, newest first. Under /config, so it takes the admin login.
 */
@RestController
@RequestMapping("/config/notices")
@RequiredArgsConstructor
public class NoticeAdminController {

    private final ContextCopyNoticeRepository contextCopyNoticeRepository;

    @GetMapping(value = "/context-copy", produces = MediaType.APPLICATION_JSON_VALUE)
    public Page<ContextCopyNotice> contextCopyNotices(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return contextCopyNoticeRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "contextCopyNoticeId")));
    }

}
