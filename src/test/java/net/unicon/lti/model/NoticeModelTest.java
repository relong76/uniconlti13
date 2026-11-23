package net.unicon.lti.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NoticeModelTest {

    @Test
    public void testContextCopyNoticeAccessors() {
        ContextCopyNotice notice = new ContextCopyNotice();
        notice.setContextCopyNoticeId(1L);
        notice.setNoticeId("n-1");
        notice.setKeyId(2L);
        notice.setDeploymentId("dep");
        notice.setNoticeTimestamp("2026-01-01T00:00:00Z");
        notice.setContextKey("ctx");
        notice.setContextTitle("Course");
        notice.setOriginContextKeys("a,b");

        assertEquals(1L, notice.getContextCopyNoticeId());
        assertEquals("n-1", notice.getNoticeId());
        assertEquals(2L, notice.getKeyId());
        assertEquals("dep", notice.getDeploymentId());
        assertEquals("2026-01-01T00:00:00Z", notice.getNoticeTimestamp());
        assertEquals("ctx", notice.getContextKey());
        assertEquals("Course", notice.getContextTitle());
        assertEquals("a,b", notice.getOriginContextKeys());
    }

    @Test
    public void testNoticeSubscriptionAccessors() {
        NoticeSubscription subscription = new NoticeSubscription();
        subscription.setSubscriptionId(1L);
        subscription.setKeyId(2L);
        subscription.setNoticeType("LtiContextCopyNotice");
        subscription.setPnsUrl("https://pns");
        subscription.setHandlerUrl("https://handler");

        assertEquals(1L, subscription.getSubscriptionId());
        assertEquals(2L, subscription.getKeyId());
        assertEquals("LtiContextCopyNotice", subscription.getNoticeType());
        assertEquals("https://pns", subscription.getPnsUrl());
        assertEquals("https://handler", subscription.getHandlerUrl());
    }

    @Test
    public void testNoticeResultFactories() {
        NoticeResult accepted = NoticeResult.accepted("n-1");
        assertEquals("n-1", accepted.noticeId());
        assertEquals(NoticeResult.STATUS_ACCEPTED, accepted.status());
        assertNull(accepted.detail());

        NoticeResult ignored = NoticeResult.ignored("n-2", "duplicate");
        assertEquals("n-2", ignored.noticeId());
        assertEquals("ignored", ignored.status());
        assertEquals("duplicate", ignored.detail());

        NoticeResult rejected = NoticeResult.rejected("n-3", "bad");
        assertEquals("n-3", rejected.noticeId());
        assertEquals("rejected", rejected.status());
        assertEquals("bad", rejected.detail());

        assertEquals(new NoticeResult("n-3", "rejected", "bad"), rejected);
    }
}
