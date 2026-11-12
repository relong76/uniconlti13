package net.unicon.lti.service.lti;

import net.unicon.lti.model.NoticeResult;

import java.util.Map;

/**
 * LTI Platform Notification Service support: registering this tool's notice handler with a
 * platform, and processing the notices the platform then sends to it.
 */
public interface NoticeService {

    /**
     * The URL of this tool's notice handler, the one registered with platforms.
     */
    String handlerUrl();

    /**
     * Registers the notice handler for course copy notices with the deployment's Platform
     * Notification Service, if the launch offered one that sends them. Runs in the background
     * so it never slows down the launch, and does nothing when the handler is already registered.
     *
     * @param keyId    the platform deployment the launch came from
     * @param pnsClaim the launch's platformnotificationservice claim
     */
    void registerContextCopyHandler(long keyId, Map<String, Object> pnsClaim);

    /**
     * Validates one notice JWT from the platform and records it when it's a course copy notice.
     */
    NoticeResult processNotice(String jwt);

}
