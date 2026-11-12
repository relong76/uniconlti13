package net.unicon.lti.service.lti.impl;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.ContextCopyNotice;
import net.unicon.lti.model.NoticeResult;
import net.unicon.lti.model.NoticeSubscription;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.model.oauth2.LTIToken;
import net.unicon.lti.repository.ContextCopyNoticeRepository;
import net.unicon.lti.repository.NoticeSubscriptionRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.lti.AdvantageConnectorHelper;
import net.unicon.lti.service.lti.LTIJWTService;
import net.unicon.lti.service.lti.NoticeService;
import net.unicon.lti.utils.LtiStrings;
import net.unicon.lti.utils.TextConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    public static final String HANDLER_PATH = "/notices";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LTIJWTService ltijwtService;

    private final AdvantageConnectorHelper advantageConnectorHelper;

    private final PlatformDeploymentRepository platformDeploymentRepository;

    private final NoticeSubscriptionRepository noticeSubscriptionRepository;

    private final ContextCopyNoticeRepository contextCopyNoticeRepository;

    @Value("${domain.url}")
    String domainUrl;

    public void setDomainUrl(String domainUrl) {
        this.domainUrl = domainUrl;
    }

    @Override
    public String handlerUrl() {
        return StringUtils.removeEnd(domainUrl, "/") + HANDLER_PATH;
    }

    @Async
    @Override
    public void registerContextCopyHandler(long keyId, Map<String, Object> pnsClaim) {
        if (!offersContextCopyNotices(pnsClaim)) {
            return;
        }

        String pnsUrl = String.valueOf(pnsClaim.get(LtiStrings.LTI_PNS_URL));
        String handlerUrl = handlerUrl();
        NoticeSubscription subscription = noticeSubscriptionRepository
            .findByKeyIdAndNoticeType(keyId, LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY)
            .orElse(null);

        if (subscription != null && pnsUrl.equals(subscription.getPnsUrl()) && handlerUrl.equals(subscription.getHandlerUrl())) {
            // already registered with this platform, at this handler URL
            return;
        }

        PlatformDeployment platformDeployment = platformDeploymentRepository.findById(keyId).orElse(null);

        if (platformDeployment == null) {
            return;
        }

        try {
            LTIToken token = advantageConnectorHelper.getToken(platformDeployment, LtiStrings.LTI_PNS_SCOPE);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, TextConstants.BEARER + token.getAccess_token());
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of(
                "notice_type", LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY,
                "handler", handlerUrl
            );

            ResponseEntity<String> response = advantageConnectorHelper.createRestTemplate()
                .exchange(pnsUrl, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Registering the course copy notice handler for platform deployment ID: [{}] returned status: [{}]", keyId, response.getStatusCode().value());
                return;
            }

            if (subscription == null) {
                subscription = new NoticeSubscription();
                subscription.setKeyId(keyId);
                subscription.setNoticeType(LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY);
            }

            subscription.setPnsUrl(pnsUrl);
            subscription.setHandlerUrl(handlerUrl);
            noticeSubscriptionRepository.save(subscription);
            log.info("Registered the course copy notice handler: [{}] for platform deployment ID: [{}]", handlerUrl, keyId);
        } catch (ConnectionException | RestClientException e) {
            log.warn("Couldn't register the course copy notice handler for platform deployment ID: [{}]", keyId, e);
        }
    }

    // the launch offers course copy notices when its claim names a notice service URL, lists
    // LtiContextCopyNotice as a supported type, and (if it lists scopes) grants the noticehandlers scope
    static boolean offersContextCopyNotices(Map<String, Object> pnsClaim) {
        if (pnsClaim == null || StringUtils.isBlank(Objects.toString(pnsClaim.get(LtiStrings.LTI_PNS_URL), null))) {
            return false;
        }

        if (!(pnsClaim.get(LtiStrings.LTI_PNS_NOTICE_TYPES) instanceof Collection<?> noticeTypes) || !noticeTypes.contains(LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY)) {
            return false;
        }

        Object scope = pnsClaim.get("scope");

        return !(scope instanceof Collection<?> scopes) || scopes.contains(LtiStrings.LTI_PNS_SCOPE);
    }

    @Override
    public NoticeResult processNotice(String jwt) {
        if (StringUtils.isBlank(jwt)) {
            return NoticeResult.rejected(null, "The notice has no JWT.");
        }

        // the signature can only be checked against the platform's keys, so read the issuer and
        // client id from the unverified payload first to find which platform it claims to be
        String clientId;
        String issuer;

        try {
            Map<String, Object> unverified = unverifiedPayload(jwt);
            issuer = Objects.toString(unverified.get("iss"), null);
            clientId = audience(unverified.get("aud"));
        } catch (IllegalArgumentException e) {
            return NoticeResult.rejected(null, "The notice JWT can't be read.");
        }

        if (issuer == null || clientId == null || platformDeploymentRepository.findByIssAndClientId(issuer, clientId).isEmpty()) {
            return NoticeResult.rejected(null, "The notice is from a platform this tool isn't registered with.");
        }

        Claims claims;

        try {
            // checks the signature against the platform's JWKS, and the expiry
            claims = ltijwtService.validateJWT(jwt, clientId).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected a notice from issuer: [{}] and client ID: [{}]: {}", issuer, clientId, e.getMessage());
            return NoticeResult.rejected(null, "The notice JWT isn't valid.");
        }

        Map<?, ?> notice = claims.get(LtiStrings.LTI_NOTICE, Map.class);
        String noticeId = notice != null ? Objects.toString(notice.get("id"), null) : null;
        String noticeType = notice != null ? Objects.toString(notice.get("type"), null) : null;

        if (noticeId == null || noticeType == null) {
            return NoticeResult.rejected(noticeId, "The notice has no notice claim.");
        }

        // a notice can come from a deployment this tool hasn't seen: copying a course that has the
        // tool installed in it gives the new course its own installation, and Canvas sends the copy
        // notice from that one. The signature already proved it's from a registered platform, so
        // fall back to that platform's registration when the deployment itself isn't registered.
        String deploymentId = claims.get(LtiStrings.LTI_DEPLOYMENT_ID, String.class);
        PlatformDeployment platformDeployment = platformDeploymentRepository
            .findByIssAndClientIdAndDeploymentId(claims.getIssuer(), clientId, deploymentId)
            .stream()
            .findFirst()
            .orElseGet(() -> platformDeploymentRepository.findByIssAndClientId(claims.getIssuer(), clientId).get(0));

        if (!LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY.equals(noticeType)) {
            return NoticeResult.ignored(noticeId, "This tool doesn't handle " + noticeType + ".");
        }

        Map<?, ?> context = claims.get(LtiStrings.LTI_CONTEXT, Map.class);
        String contextKey = context != null ? Objects.toString(context.get("id"), null) : null;
        log.info("A course copy notification was received from [{}] from LMS URL : [{}]", contextKey, claims.getIssuer());

        if (contextCopyNoticeRepository.existsByNoticeId(noticeId)) {
            // a redelivery; it was recorded the first time
            return NoticeResult.accepted(noticeId);
        }

        if (contextKey == null) {
            return NoticeResult.rejected(noticeId, "The course copy notice has no context claim.");
        }

        List<?> originContexts = claims.get(LtiStrings.LTI_ORIGIN_CONTEXTS, List.class);

        ContextCopyNotice contextCopyNotice = new ContextCopyNotice();
        contextCopyNotice.setNoticeId(noticeId);
        contextCopyNotice.setKeyId(platformDeployment.getKeyId());
        contextCopyNotice.setDeploymentId(deploymentId);
        contextCopyNotice.setNoticeTimestamp(Objects.toString(notice.get("timestamp"), null));
        contextCopyNotice.setContextKey(contextKey);
        contextCopyNotice.setContextTitle(Objects.toString(context.get("title"), null));
        contextCopyNotice.setOriginContextKeys(
            originContexts == null ? null : originContexts.stream().map(String::valueOf).collect(Collectors.joining(","))
        );
        contextCopyNoticeRepository.save(contextCopyNotice);
        log.info("Recorded course copy notice ID: [{}]: content from context(s): [{}] was copied into context: [{}]", noticeId, contextCopyNotice.getOriginContextKeys(), contextKey);

        return NoticeResult.accepted(noticeId);
    }

    private static Map<String, Object> unverifiedPayload(String jwt) {
        String[] parts = jwt.split("\\.");

        if (parts.length < 2) {
            throw new IllegalArgumentException("not a JWT");
        }

        try {
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            return OBJECT_MAPPER.readValue(new String(json, StandardCharsets.UTF_8), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("not a JWT", e);
        }
    }

    // aud is a string, or an array holding the client id
    private static String audience(Object aud) {
        if (aud instanceof Collection<?> audiences) {
            return audiences.size() == 1 ? String.valueOf(audiences.iterator().next()) : null;
        }

        return aud != null ? aud.toString() : null;
    }

}
