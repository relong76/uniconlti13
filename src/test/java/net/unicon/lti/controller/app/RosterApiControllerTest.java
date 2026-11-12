package net.unicon.lti.controller.app;

import io.jsonwebtoken.Jwts;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.LtiContextEntity;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.model.membership.CourseUser;
import net.unicon.lti.model.membership.CourseUsers;
import net.unicon.lti.model.oauth2.LTIToken;
import net.unicon.lti.repository.LtiContextRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.lti.AdvantageMembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class RosterApiControllerTest {

    @Mock
    private ApiSessions apiSessions;

    @Mock
    private PlatformDeploymentRepository platformDeploymentRepository;

    @Mock
    private LtiContextRepository ltiContextRepository;

    @Mock
    private AdvantageMembershipService membershipService;

    private RosterApiController controller;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final PlatformDeployment deployment = new PlatformDeployment();
    private final LtiContextEntity context = new LtiContextEntity();
    private final LTIToken token = new LTIToken("nrps", "Bearer", 3600, "contextmembership.readonly");

    @BeforeEach
    public void setUp() throws ConnectionException {
        MockitoAnnotations.openMocks(this);
        controller = new RosterApiController(apiSessions, platformDeploymentRepository, ltiContextRepository, membershipService);
        context.setContext_memberships_url("https://lms/memberships");
        when(apiSessions.require(request)).thenReturn(Jwts.claims().add("platformDeploymentId", "1").add("contextKey", "ctx").build());
        when(platformDeploymentRepository.findById(1L)).thenReturn(Optional.of(deployment));
        when(ltiContextRepository.findByContextKeyAndPlatformDeployment("ctx", deployment)).thenReturn(context);
        when(membershipService.getToken(deployment)).thenReturn(token);
    }

    @Test
    public void testListsTheMembers() throws ConnectionException {
        CourseUser ada = new CourseUser();
        ada.setUserId("user-1");
        ada.setName("Ada Lovelace");
        ada.setGivenName("Ada");
        ada.setFamilyName("Lovelace");
        ada.setEmail("ada@example.edu");
        ada.setPicture("https://lms/ada.png");
        ada.setRoles(List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"));
        ada.setStatus("Inactive");
        CourseUsers members = new CourseUsers();
        members.getCourseUserList().add(ada);
        when(membershipService.callMembershipService(token, context)).thenReturn(members);

        List<Map<String, Object>> roster = controller.roster(request);

        assertEquals(1, roster.size());
        Map<String, Object> member = roster.getFirst();
        assertEquals("user-1", member.get("userId"));
        assertEquals("Ada Lovelace", member.get("name"));
        assertEquals("Ada", member.get("givenName"));
        assertEquals("Lovelace", member.get("familyName"));
        assertEquals("ada@example.edu", member.get("email"));
        assertEquals("https://lms/ada.png", member.get("picture"));
        assertEquals(List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"), member.get("roles"));
        assertEquals("Inactive", member.get("status"));
    }

    @Test
    public void testFillsInSparseMembers() {
        CourseUser named = new CourseUser();
        named.setGivenName("Grace");
        named.setFamilyName("Hopper");
        Map<String, Object> view = RosterApiController.describe(named);
        assertEquals("Grace Hopper", view.get("name"));
        assertEquals(List.of(), view.get("roles"));
        assertEquals("Active", view.get("status"));

        CourseUser given = new CourseUser();
        given.setName(" ");
        given.setGivenName("Grace");
        assertEquals("Grace", RosterApiController.describe(given).get("name"));

        CourseUser family = new CourseUser();
        family.setFamilyName("Hopper");
        assertEquals("Hopper", RosterApiController.describe(family).get("name"));

        assertNull(RosterApiController.describe(new CourseUser()).get("name"));
    }

    @Test
    public void testCourseWithoutARoster() {
        context.setContext_memberships_url(null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.roster(request));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("This course doesn't offer Names and Role Provisioning Services to the tool.", e.getReason());
        verifyNoInteractions(membershipService);
    }

    @Test
    public void testPassesOnConnectionErrors() throws ConnectionException {
        when(membershipService.callMembershipService(token, context)).thenThrow(new ConnectionException("Can't get the membership"));
        assertThrows(ConnectionException.class, () -> controller.roster(request));
    }

}
