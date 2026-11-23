package net.unicon.lti.service.lti;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.ContextCopyNotice;
import net.unicon.lti.model.NoticeResult;
import net.unicon.lti.model.NoticeSubscription;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.model.oauth2.LTIToken;
import net.unicon.lti.repository.ContextCopyNoticeRepository;
import net.unicon.lti.repository.NoticeSubscriptionRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.lti.impl.NoticeServiceImpl;
import net.unicon.lti.utils.LtiStrings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NoticeServiceTest {

    private static final String ISS = "https://canvas.instructure.com";
    private static final String CLIENT_ID = "202570000000000231";
    private static final String DEPLOYMENT_ID = "283:577dbefa717b021bcead9da1f3d64ab5ebc95c51";
    private static final String PNS_URL = "https://canvas.example.edu/api/lti/notice-handlers/283";
    private static final long KEY_ID = 7L;

    @InjectMocks
    NoticeServiceImpl noticeService;

    @Mock
    LTIJWTService ltijwtService;

    @Mock
    AdvantageConnectorHelper advantageConnectorHelper;

    @Mock
    PlatformDeploymentRepository platformDeploymentRepository;

    @Mock
    NoticeSubscriptionRepository noticeSubscriptionRepository;

    @Mock
    ContextCopyNoticeRepository contextCopyNoticeRepository;

    @Mock
    RestTemplate restTemplate;

    PlatformDeployment platformDeployment;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        noticeService.setDomainUrl("https://tool.example.ts.net/");

        platformDeployment = new PlatformDeployment();
        platformDeployment.setKeyId(KEY_ID);
        platformDeployment.setIss(ISS);
        platformDeployment.setClientId(CLIENT_ID);
        platformDeployment.setDeploymentId(DEPLOYMENT_ID);

        when(platformDeploymentRepository.findByIssAndClientId(ISS, CLIENT_ID)).thenReturn(List.of(platformDeployment));
        when(platformDeploymentRepository.findByIssAndClientIdAndDeploymentId(ISS, CLIENT_ID, DEPLOYMENT_ID)).thenReturn(List.of(platformDeployment));
        when(platformDeploymentRepository.findById(KEY_ID)).thenReturn(Optional.of(platformDeployment));
        when(advantageConnectorHelper.createRestTemplate()).thenReturn(restTemplate);
    }

    // handler registration

    @Test
    public void testHandlerUrlDropsTrailingSlash() {
        assertEquals("https://tool.example.ts.net/notices", noticeService.handlerUrl());
    }

    @Test
    public void testRegisterContextCopyHandler() throws ConnectionException {
        LTIToken token = new LTIToken();
        token.setAccess_token("pns-token");
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(KEY_ID, LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY)).thenReturn(Optional.empty());
        when(advantageConnectorHelper.getToken(platformDeployment, LtiStrings.LTI_PNS_SCOPE)).thenReturn(token);
        when(restTemplate.exchange(eq(PNS_URL), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class))).thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        noticeService.registerContextCopyHandler(KEY_ID, pnsClaim());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, String>>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(PNS_URL), eq(HttpMethod.PUT), request.capture(), eq(String.class));
        assertEquals("Bearer pns-token", request.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(Map.of("notice_type", "LtiContextCopyNotice", "handler", "https://tool.example.ts.net/notices"), request.getValue().getBody());

        ArgumentCaptor<NoticeSubscription> saved = ArgumentCaptor.forClass(NoticeSubscription.class);
        verify(noticeSubscriptionRepository).save(saved.capture());
        assertEquals(KEY_ID, saved.getValue().getKeyId());
        assertEquals(PNS_URL, saved.getValue().getPnsUrl());
        assertEquals("https://tool.example.ts.net/notices", saved.getValue().getHandlerUrl());
    }

    @Test
    public void testRegisterContextCopyHandlerSkipsWhenAlreadyRegistered() throws ConnectionException {
        NoticeSubscription subscription = new NoticeSubscription();
        subscription.setPnsUrl(PNS_URL);
        subscription.setHandlerUrl("https://tool.example.ts.net/notices");
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(KEY_ID, LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY)).thenReturn(Optional.of(subscription));

        noticeService.registerContextCopyHandler(KEY_ID, pnsClaim());

        verify(advantageConnectorHelper, never()).getToken(any(), anyString());
        verify(noticeSubscriptionRepository, never()).save(any());
    }

    @Test
    public void testRegisterContextCopyHandlerReRegistersWhenHandlerUrlChanged() throws ConnectionException {
        NoticeSubscription subscription = new NoticeSubscription();
        subscription.setPnsUrl(PNS_URL);
        subscription.setHandlerUrl("https://old-host.example.com/notices");
        LTIToken token = new LTIToken();
        token.setAccess_token("pns-token");
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(KEY_ID, LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY)).thenReturn(Optional.of(subscription));
        when(advantageConnectorHelper.getToken(platformDeployment, LtiStrings.LTI_PNS_SCOPE)).thenReturn(token);
        when(restTemplate.exchange(eq(PNS_URL), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class))).thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        noticeService.registerContextCopyHandler(KEY_ID, pnsClaim());

        verify(noticeSubscriptionRepository).save(subscription);
        assertEquals("https://tool.example.ts.net/notices", subscription.getHandlerUrl());
    }

    @Test
    public void testRegisterContextCopyHandlerDoesNotSaveOnError() throws ConnectionException {
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(anyLong(), anyString())).thenReturn(Optional.empty());
        when(advantageConnectorHelper.getToken(platformDeployment, LtiStrings.LTI_PNS_SCOPE)).thenThrow(new ConnectionException("no token"));

        noticeService.registerContextCopyHandler(KEY_ID, pnsClaim());

        verify(noticeSubscriptionRepository, never()).save(any());
    }

    @Test
    public void testRegisterContextCopyHandlerDoesNotSaveOnRejectedRegistration() throws ConnectionException {
        LTIToken token = new LTIToken();
        token.setAccess_token("pns-token");
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(anyLong(), anyString())).thenReturn(Optional.empty());
        when(advantageConnectorHelper.getToken(platformDeployment, LtiStrings.LTI_PNS_SCOPE)).thenReturn(token);
        when(restTemplate.exchange(eq(PNS_URL), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class))).thenReturn(new ResponseEntity<>("{}", HttpStatus.FORBIDDEN));

        noticeService.registerContextCopyHandler(KEY_ID, pnsClaim());

        verify(noticeSubscriptionRepository, never()).save(any());
    }

    @Test
    public void testRegisterContextCopyHandlerSkipsWhenNotOffered() throws ConnectionException {
        Map<String, Object> claim = pnsClaim();
        claim.put(LtiStrings.LTI_PNS_NOTICE_TYPES, List.of("LtiHelloWorldNotice"));

        noticeService.registerContextCopyHandler(KEY_ID, claim);

        verify(advantageConnectorHelper, never()).getToken(any(), anyString());
    }

    @Test
    public void testRegisterContextCopyHandlerSkipsWithoutNoticeHandlersScope() throws ConnectionException {
        Map<String, Object> claim = pnsClaim();
        claim.put("scope", List.of("https://purl.imsglobal.org/spec/lti-ags/scope/score"));

        noticeService.registerContextCopyHandler(KEY_ID, claim);

        verify(advantageConnectorHelper, never()).getToken(any(), anyString());
    }

    // notice processing

    @Test
    public void testProcessContextCopyNotice() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Jws<Claims> validated = jws(noticeClaims("notice-1", "LtiContextCopyNotice", DEPLOYMENT_ID));
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);
        when(contextCopyNoticeRepository.existsByNoticeId("notice-1")).thenReturn(false);

        NoticeResult result = noticeService.processNotice(jwt);

        assertEquals(NoticeResult.accepted("notice-1"), result);
        ArgumentCaptor<ContextCopyNotice> saved = ArgumentCaptor.forClass(ContextCopyNotice.class);
        verify(contextCopyNoticeRepository).save(saved.capture());
        assertEquals("notice-1", saved.getValue().getNoticeId());
        assertEquals(KEY_ID, saved.getValue().getKeyId());
        assertEquals("new-course-ctx", saved.getValue().getContextKey());
        assertEquals("Biology 101 (Spring)", saved.getValue().getContextTitle());
        assertEquals("old-course-ctx", saved.getValue().getOriginContextKeys());
        assertEquals("2026-11-12T17:00:00Z", saved.getValue().getNoticeTimestamp());
        assertEquals(DEPLOYMENT_ID, saved.getValue().getDeploymentId());
    }

    @Test
    public void testProcessNoticeAcceptsArrayAudience() {
        String jwt = unsignedJwt(ISS, List.of(CLIENT_ID));
        Jws<Claims> validated = jws(noticeClaims("notice-1", "LtiContextCopyNotice", DEPLOYMENT_ID));
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        assertEquals(NoticeResult.STATUS_ACCEPTED, noticeService.processNotice(jwt).status());
    }

    @Test
    public void testProcessNoticeRedeliveryIsNotRecordedTwice() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Jws<Claims> validated = jws(noticeClaims("notice-1", "LtiContextCopyNotice", DEPLOYMENT_ID));
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);
        when(contextCopyNoticeRepository.existsByNoticeId("notice-1")).thenReturn(true);

        assertEquals(NoticeResult.accepted("notice-1"), noticeService.processNotice(jwt));
        verify(contextCopyNoticeRepository, never()).save(any());
    }

    @Test
    public void testProcessNoticeIgnoresOtherNoticeTypes() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Jws<Claims> validated = jws(noticeClaims("notice-2", "LtiHelloWorldNotice", DEPLOYMENT_ID));
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        NoticeResult result = noticeService.processNotice(jwt);

        assertEquals(NoticeResult.STATUS_IGNORED, result.status());
        assertEquals("notice-2", result.noticeId());
        verify(contextCopyNoticeRepository, never()).save(any());
    }

    @Test
    public void testProcessNoticeRejectsInvalidSignature() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenThrow(new SignatureException("bad signature"));

        assertEquals(NoticeResult.STATUS_REJECTED, noticeService.processNotice(jwt).status());
        verify(contextCopyNoticeRepository, never()).save(any());
    }

    @Test
    public void testProcessNoticeRejectsUnknownPlatform() {
        NoticeResult result = noticeService.processNotice(unsignedJwt("https://someone-else.example.com", CLIENT_ID));

        assertEquals(NoticeResult.STATUS_REJECTED, result.status());
        verify(ltijwtService, never()).validateJWT(anyString(), anyString());
    }

    @Test
    public void testProcessNoticeFromUnregisteredDeploymentUsesThePlatformRegistration() {
        // copying a course with the tool installed in it gives the new course its own installation
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Jws<Claims> validated = jws(noticeClaims("notice-1", "LtiContextCopyNotice", "999:copied-course-installation"));
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        assertEquals(NoticeResult.accepted("notice-1"), noticeService.processNotice(jwt));

        ArgumentCaptor<ContextCopyNotice> saved = ArgumentCaptor.forClass(ContextCopyNotice.class);
        verify(contextCopyNoticeRepository).save(saved.capture());
        assertEquals(KEY_ID, saved.getValue().getKeyId());
        assertEquals("999:copied-course-installation", saved.getValue().getDeploymentId());
    }

    @Test
    public void testProcessNoticeFromRegisteredDeploymentUsesThatDeployment() {
        PlatformDeployment otherDeployment = new PlatformDeployment();
        otherDeployment.setKeyId(8L);
        when(platformDeploymentRepository.findByIssAndClientId(ISS, CLIENT_ID)).thenReturn(List.of(platformDeployment, otherDeployment));
        when(platformDeploymentRepository.findByIssAndClientIdAndDeploymentId(ISS, CLIENT_ID, "284:second")).thenReturn(List.of(otherDeployment));
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Jws<Claims> validated = jws(noticeClaims("notice-1", "LtiContextCopyNotice", "284:second"));
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        assertEquals(NoticeResult.accepted("notice-1"), noticeService.processNotice(jwt));

        ArgumentCaptor<ContextCopyNotice> saved = ArgumentCaptor.forClass(ContextCopyNotice.class);
        verify(contextCopyNoticeRepository).save(saved.capture());
        assertEquals(8L, saved.getValue().getKeyId());
        assertEquals("284:second", saved.getValue().getDeploymentId());
    }

    @Test
    public void testProcessNoticeRejectsUnreadableJwt() {
        NoticeResult result = noticeService.processNotice("not-a-jwt");

        assertEquals(NoticeResult.STATUS_REJECTED, result.status());
        assertNull(result.noticeId());
    }

    @Test
    public void testProcessNoticeRejectsMissingJwt() {
        assertEquals(NoticeResult.STATUS_REJECTED, noticeService.processNotice(null).status());
    }

    @Test
    public void testProcessNoticeRejectsMissingContext() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Map<String, Object> claims = noticeClaims("notice-1", "LtiContextCopyNotice", DEPLOYMENT_ID);
        claims.remove(LtiStrings.LTI_CONTEXT);
        Jws<Claims> validated = jws(claims);
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        assertEquals(NoticeResult.STATUS_REJECTED, noticeService.processNotice(jwt).status());
        verify(contextCopyNoticeRepository, never()).save(any());
    }

    @Test
    public void testRegisterContextCopyHandlerReRegistersWhenNoticeServiceUrlChanged() throws ConnectionException {
        NoticeSubscription subscription = new NoticeSubscription();
        subscription.setPnsUrl("https://canvas.example.edu/api/lti/notice-handlers/old");
        subscription.setHandlerUrl("https://tool.example.ts.net/notices");
        LTIToken token = new LTIToken();
        token.setAccess_token("pns-token");
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(KEY_ID, LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY)).thenReturn(Optional.of(subscription));
        when(advantageConnectorHelper.getToken(platformDeployment, LtiStrings.LTI_PNS_SCOPE)).thenReturn(token);
        when(restTemplate.exchange(eq(PNS_URL), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class))).thenReturn(new ResponseEntity<>("{}", HttpStatus.NO_CONTENT));

        noticeService.registerContextCopyHandler(KEY_ID, pnsClaim());

        verify(noticeSubscriptionRepository).save(subscription);
        assertEquals(PNS_URL, subscription.getPnsUrl());
    }

    @Test
    public void testRegisterContextCopyHandlerWithoutScopeListStillRegisters() throws ConnectionException {
        Map<String, Object> claim = pnsClaim();
        claim.remove("scope");
        LTIToken token = new LTIToken();
        token.setAccess_token("pns-token");
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(anyLong(), anyString())).thenReturn(Optional.empty());
        when(advantageConnectorHelper.getToken(platformDeployment, LtiStrings.LTI_PNS_SCOPE)).thenReturn(token);
        when(restTemplate.exchange(eq(PNS_URL), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class))).thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        noticeService.registerContextCopyHandler(KEY_ID, claim);

        verify(noticeSubscriptionRepository).save(any(NoticeSubscription.class));
    }

    @Test
    public void testRegisterContextCopyHandlerSkipsWithoutClaim() throws ConnectionException {
        noticeService.registerContextCopyHandler(KEY_ID, null);

        verify(noticeSubscriptionRepository, never()).findByKeyIdAndNoticeType(anyLong(), anyString());
        verify(advantageConnectorHelper, never()).getToken(any(), anyString());
    }

    @Test
    public void testRegisterContextCopyHandlerSkipsWithoutNoticeServiceUrl() throws ConnectionException {
        Map<String, Object> blankUrl = pnsClaim();
        blankUrl.put(LtiStrings.LTI_PNS_URL, " ");
        Map<String, Object> noUrl = pnsClaim();
        noUrl.remove(LtiStrings.LTI_PNS_URL);

        noticeService.registerContextCopyHandler(KEY_ID, blankUrl);
        noticeService.registerContextCopyHandler(KEY_ID, noUrl);

        verify(advantageConnectorHelper, never()).getToken(any(), anyString());
    }

    @Test
    public void testRegisterContextCopyHandlerSkipsWhenNoticeTypesIsNotAList() throws ConnectionException {
        Map<String, Object> claim = pnsClaim();
        claim.put(LtiStrings.LTI_PNS_NOTICE_TYPES, LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY);

        noticeService.registerContextCopyHandler(KEY_ID, claim);

        verify(advantageConnectorHelper, never()).getToken(any(), anyString());
    }

    @Test
    public void testRegisterContextCopyHandlerSkipsWhenTheDeploymentIsGone() throws ConnectionException {
        when(noticeSubscriptionRepository.findByKeyIdAndNoticeType(anyLong(), anyString())).thenReturn(Optional.empty());
        when(platformDeploymentRepository.findById(KEY_ID)).thenReturn(Optional.empty());

        noticeService.registerContextCopyHandler(KEY_ID, pnsClaim());

        verify(advantageConnectorHelper, never()).getToken(any(), anyString());
        verify(noticeSubscriptionRepository, never()).save(any());
    }

    @Test
    public void testProcessNoticeRecordsCopyWithoutOriginContexts() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Map<String, Object> claims = noticeClaims("notice-3", "LtiContextCopyNotice", DEPLOYMENT_ID);
        claims.remove(LtiStrings.LTI_ORIGIN_CONTEXTS);
        Jws<Claims> validated = jws(claims);
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        assertEquals(NoticeResult.accepted("notice-3"), noticeService.processNotice(jwt));

        ArgumentCaptor<ContextCopyNotice> saved = ArgumentCaptor.forClass(ContextCopyNotice.class);
        verify(contextCopyNoticeRepository).save(saved.capture());
        assertNull(saved.getValue().getOriginContextKeys());
    }

    @Test
    public void testProcessNoticeRejectsMissingNoticeClaim() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Map<String, Object> claims = noticeClaims("notice-1", "LtiContextCopyNotice", DEPLOYMENT_ID);
        claims.remove(LtiStrings.LTI_NOTICE);
        Jws<Claims> validated = jws(claims);
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        NoticeResult result = noticeService.processNotice(jwt);

        assertEquals(NoticeResult.STATUS_REJECTED, result.status());
        assertNull(result.noticeId());
    }

    @Test
    public void testProcessNoticeRejectsNoticeWithoutAType() {
        String jwt = unsignedJwt(ISS, CLIENT_ID);
        Map<String, Object> claims = noticeClaims("notice-1", "LtiContextCopyNotice", DEPLOYMENT_ID);
        claims.put(LtiStrings.LTI_NOTICE, Map.of("id", "notice-1"));
        Jws<Claims> validated = jws(claims);
        when(ltijwtService.validateJWT(jwt, CLIENT_ID)).thenReturn(validated);

        assertEquals(NoticeResult.rejected("notice-1", "The notice has no notice claim."), noticeService.processNotice(jwt));
    }

    @Test
    public void testProcessNoticeRejectsMoreThanOneAudience() {
        NoticeResult result = noticeService.processNotice(jwtWithPayload("{\"iss\":\"" + ISS + "\",\"aud\":[\"" + CLIENT_ID + "\",\"other-client\"]}"));

        assertEquals(NoticeResult.STATUS_REJECTED, result.status());
        verify(ltijwtService, never()).validateJWT(anyString(), anyString());
    }

    @Test
    public void testProcessNoticeRejectsMissingAudienceOrIssuer() {
        assertEquals(NoticeResult.STATUS_REJECTED, noticeService.processNotice(jwtWithPayload("{\"iss\":\"" + ISS + "\"}")).status());
        assertEquals(NoticeResult.STATUS_REJECTED, noticeService.processNotice(jwtWithPayload("{\"aud\":\"" + CLIENT_ID + "\"}")).status());
        verify(ltijwtService, never()).validateJWT(anyString(), anyString());
    }

    @Test
    public void testProcessNoticeRejectsPayloadThatIsNotJson() {
        assertEquals(NoticeResult.STATUS_REJECTED, noticeService.processNotice(jwtWithPayload("not json")).status());
        assertEquals(NoticeResult.STATUS_REJECTED, noticeService.processNotice("header.%%%.signature").status());
        verify(ltijwtService, never()).validateJWT(anyString(), anyString());
    }

    private static Map<String, Object> pnsClaim() {
        Map<String, Object> claim = new HashMap<>();
        claim.put(LtiStrings.LTI_PNS_URL, PNS_URL);
        claim.put(LtiStrings.LTI_PNS_NOTICE_TYPES, List.of("LtiHelloWorldNotice", "LtiContextCopyNotice"));
        claim.put("scope", List.of(LtiStrings.LTI_PNS_SCOPE));
        return claim;
    }

    private static Map<String, Object> noticeClaims(String noticeId, String noticeType, String deploymentId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISS);
        claims.put("aud", CLIENT_ID);
        claims.put(LtiStrings.LTI_DEPLOYMENT_ID, deploymentId);
        claims.put(LtiStrings.LTI_NOTICE, Map.of("id", noticeId, "type", noticeType, "timestamp", "2026-11-12T17:00:00Z"));
        claims.put(LtiStrings.LTI_CONTEXT, Map.of("id", "new-course-ctx", "title", "Biology 101 (Spring)"));
        claims.put(LtiStrings.LTI_ORIGIN_CONTEXTS, List.of("old-course-ctx"));
        return claims;
    }

    @SuppressWarnings("unchecked")
    private static Jws<Claims> jws(Map<String, Object> claims) {
        Jws<Claims> jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(Jwts.claims().add(claims).build());
        return jws;
    }

    // the service only reads the unverified payload to find the platform; the signature check is mocked
    private static String unsignedJwt(String iss, Object aud) {
        String audJson = aud instanceof List<?> list ? "[\"" + list.get(0) + "\"]" : "\"" + aud + "\"";
        String payload = "{\"iss\":\"" + iss + "\",\"aud\":" + audJson + "}";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
            + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    private static String jwtWithPayload(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
            + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

}
