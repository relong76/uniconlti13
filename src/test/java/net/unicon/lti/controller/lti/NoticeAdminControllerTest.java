package net.unicon.lti.controller.lti;

import net.unicon.lti.model.ContextCopyNotice;
import net.unicon.lti.repository.ContextCopyNoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NoticeAdminControllerTest {

    @InjectMocks
    private NoticeAdminController noticeAdminController;

    @Mock
    private ContextCopyNoticeRepository contextCopyNoticeRepository;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testListsNewestFirstWithTheRequestedPage() {
        Page<ContextCopyNotice> page = new PageImpl<>(List.of(new ContextCopyNotice()));
        when(contextCopyNoticeRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<ContextCopyNotice> result = noticeAdminController.contextCopyNotices(2, 5);

        assertSame(page, result);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(contextCopyNoticeRepository).findAll(pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "contextCopyNoticeId"), pageable.getValue().getSort());
    }

}
