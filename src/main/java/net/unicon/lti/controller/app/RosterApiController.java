package net.unicon.lti.controller.app;

import jakarta.servlet.http.HttpServletRequest;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.model.membership.CourseUser;
import net.unicon.lti.model.membership.CourseUsers;
import net.unicon.lti.repository.LtiContextRepository;
import net.unicon.lti.repository.PlatformDeploymentRepository;
import net.unicon.lti.service.app.ApiSessions;
import net.unicon.lti.service.lti.AdvantageMembershipService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Names and Role Provisioning Services: the members of the caller's course.
 */
@RestController
@RequestMapping(value = "/api/roster", produces = MediaType.APPLICATION_JSON_VALUE)
public class RosterApiController extends AdvantageApiSupport {

    private final AdvantageMembershipService membershipService;

    public RosterApiController(ApiSessions apiSessions, PlatformDeploymentRepository platformDeploymentRepository,
                               LtiContextRepository ltiContextRepository, AdvantageMembershipService membershipService) {
        super(apiSessions, platformDeploymentRepository, ltiContextRepository);
        this.membershipService = membershipService;
    }

    @GetMapping
    public List<Map<String, Object>> roster(HttpServletRequest request) throws ConnectionException {
        Course course = course(request);

        if (StringUtils.isBlank(course.context().getContext_memberships_url())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This course doesn't offer Names and Role Provisioning Services to the tool.");
        }

        CourseUsers members = membershipService.callMembershipService(membershipService.getToken(course.deployment()), course.context());

        return members.getCourseUserList().stream().map(RosterApiController::describe).toList();
    }

    static Map<String, Object> describe(CourseUser member) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", member.getUserId());
        view.put("name", StringUtils.isNotBlank(member.getName())
            ? member.getName()
            : StringUtils.trimToNull(Objects.toString(member.getGivenName(), "") + " " + Objects.toString(member.getFamilyName(), "")));
        view.put("givenName", member.getGivenName());
        view.put("familyName", member.getFamilyName());
        view.put("email", member.getEmail());
        view.put("picture", member.getPicture());
        view.put("roles", member.getRoles() != null ? member.getRoles() : List.of());
        view.put("status", member.getStatus() != null ? member.getStatus() : "Active");
        return view;
    }

}
