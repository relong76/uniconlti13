package net.unicon.lti.model;

/**
 * What happened to one notice in a batch the platform sent to the notice handler.
 *
 * @param noticeId the notice's id, when it could be read
 * @param status   accepted (recorded, or already recorded), ignored (a notice type this tool doesn't handle) or rejected
 * @param detail   why a notice was ignored or rejected
 */
public record NoticeResult(String noticeId, String status, String detail) {

    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_IGNORED = "ignored";
    public static final String STATUS_REJECTED = "rejected";

    public static NoticeResult accepted(String noticeId) {
        return new NoticeResult(noticeId, STATUS_ACCEPTED, null);
    }

    public static NoticeResult ignored(String noticeId, String detail) {
        return new NoticeResult(noticeId, STATUS_IGNORED, detail);
    }

    public static NoticeResult rejected(String noticeId, String detail) {
        return new NoticeResult(noticeId, STATUS_REJECTED, detail);
    }

}
