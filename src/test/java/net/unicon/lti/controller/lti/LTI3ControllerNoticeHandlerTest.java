package net.unicon.lti.controller.lti;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.repository.AllRepositories;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.lti.LTIDataService;
import net.unicon.lti.service.lti.NoticeService;
import net.unicon.lti.utils.LtiStrings;
import net.unicon.lti.utils.lti.LTI3Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The launch hook that hands the platformnotificationservice claim to NoticeService.
 */
public class LTI3ControllerNoticeHandlerTest {

    private static final String ISS = "https://canvas.instructure.com";
    private static final String CLIENT_ID = "202570000000000231";
    private static final String DEPLOYMENT_ID = "283:577dbefa717b021bcead9da1f3d64ab5ebc95c51";

    @InjectMocks
    private LTI3Controller lti3Controller;

    @Mock
    private LTIDataService ltiDataService;

    @Mock
    private NoticeService noticeService;

    @Mock
    private PlatformDeploymentRepository platformDeploymentRepository;

    @Mock
    private LTI3Request lti3Request;

    private final Map<String, Object> pnsClaim = Map.of(
        LtiStrings.LTI_PNS_URL, "https://canvas.example.edu/api/lti/notice-handlers/283",
        LtiStrings.LTI_PNS_NOTICE_TYPES, List.of(LtiStrings.LTI_NOTICE_TYPE_CONTEXT_COPY)
    );

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        AllRepositories repos = mock(AllRepositories.class);
        ReflectionTestUtils.setField(repos, "platformDeploymentRepository", platformDeploymentRepository);
        when(ltiDataService.getRepos()).thenReturn(repos);
        when(lti3Request.getIss()).thenReturn(ISS);
        when(lti3Request.getAud()).thenReturn(CLIENT_ID);
        when(lti3Request.getLtiDeploymentId()).thenReturn(DEPLOYMENT_ID);
    }

    @Test
    public void testRegistersHandlerForTheLaunchDeployment() {
        launchClaims(Map.of(LtiStrings.LTI_PLATFORM_NOTIFICATION_SERVICE, pnsClaim));
        PlatformDeployment platformDeployment = new PlatformDeployment();
        platformDeployment.setKeyId(7L);
        when(platformDeploymentRepository.findByIssAndClientIdAndDeploymentId(ISS, CLIENT_ID, DEPLOYMENT_ID)).thenReturn(List.of(platformDeployment));

        lti3Controller.registerNoticeHandlers(lti3Request);

        verify(noticeService).registerContextCopyHandler(7L, pnsClaim);
    }

    @Test
    public void testSkipsLaunchWithoutNotificationServiceClaim() {
        launchClaims(Map.of("iss", ISS));

        lti3Controller.registerNoticeHandlers(lti3Request);

        verify(noticeService, never()).registerContextCopyHandler(anyLong(), any());
        verify(platformDeploymentRepository, never()).findByIssAndClientIdAndDeploymentId(any(), any(), any());
    }

    @Test
    public void testSkipsWhenTheDeploymentIsNotFoundExactlyOnce() {
        launchClaims(Map.of(LtiStrings.LTI_PLATFORM_NOTIFICATION_SERVICE, pnsClaim));
        when(platformDeploymentRepository.findByIssAndClientIdAndDeploymentId(ISS, CLIENT_ID, DEPLOYMENT_ID)).thenReturn(List.of());

        lti3Controller.registerNoticeHandlers(lti3Request);

        verify(noticeService, never()).registerContextCopyHandler(anyLong(), any());
    }

    @Test
    public void testNeverFailsTheLaunch() {
        launchClaims(Map.of(LtiStrings.LTI_PLATFORM_NOTIFICATION_SERVICE, pnsClaim));
        PlatformDeployment platformDeployment = new PlatformDeployment();
        when(platformDeploymentRepository.findByIssAndClientIdAndDeploymentId(ISS, CLIENT_ID, DEPLOYMENT_ID)).thenReturn(List.of(platformDeployment));
        doThrow(new IllegalStateException("task executor rejected the task")).when(noticeService).registerContextCopyHandler(anyLong(), any());

        assertDoesNotThrow(() -> lti3Controller.registerNoticeHandlers(lti3Request));
    }

    @SuppressWarnings("unchecked")
    private void launchClaims(Map<String, Object> claims) {
        Jws<Claims> jws = mock(Jws.class);
        Claims payload = Jwts.claims().add(claims).build();
        when(jws.getPayload()).thenReturn(payload);
        when(lti3Request.getJws()).thenReturn(jws);
    }

}
