package net.unicon.lti.controller.lti;

import java.util.LinkedHashMap;
import java.time.Instant;
import net.unicon.lti.service.app.LaunchStore;
import net.unicon.lti.model.ToolLink;
import net.unicon.lti.model.LaunchRecord;
import net.unicon.lti.utils.lti.LtiLinkUtils;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import net.unicon.lti.service.lti.NoticeService;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import net.unicon.lti.exceptions.ConnectionException;
import net.unicon.lti.exceptions.DataServiceException;
import net.unicon.lti.model.LtiLinkEntity;
import net.unicon.lti.model.PlatformDeployment;
import net.unicon.lti.model.lti.dto.NonceState;
import net.unicon.lti.repository.LtiContextRepository;
import net.unicon.lti.repository.LtiLinkRepository;
import net.unicon.lti.service.app.APIJWTService;
import net.unicon.lti.service.lti.LTIDataService;
import net.unicon.lti.service.lti.LTIJWTService;
import net.unicon.lti.utils.LtiStrings;
import net.unicon.lti.utils.TextConstants;
import net.unicon.lti.utils.lti.LTI3Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * This LTI 3 redirect controller will retrieve the LTI3 requests and redirect them to the right page.
 * Everything that arrives here is filtered first by the LTI3OAuthProviderProcessingFilter
 */
@Controller
@RequestMapping("/lti3")
@RequiredArgsConstructor
public class LTI3Controller {

    static final Logger log = LoggerFactory.getLogger(LTI3Controller.class);

    private final LTIJWTService ltijwtService;

    private final APIJWTService apiJWTService;

    private final LtiLinkRepository ltiLinkRepository;

    private final LTIDataService ltiDataService;

    private final LtiContextRepository ltiContextRepository;

    private final NoticeService noticeService;

    private final LaunchStore launchStore;

    @RequestMapping(value = {"", "/"}, method = {RequestMethod.GET, RequestMethod.POST})
    public String lti3(HttpServletRequest req, Model model) throws DataServiceException, ConnectionException {
        //There is a filter LTI3OAuthProviderProcessingFilter.java that is executed before this.
        //Some security checks are performed there.

        //We need to pass to the model the LTI3 request, the state and the nonce and link, and a JWT with the hash of all of them.
        String state = req.getParameter("state");
        String id_token = req.getParameter("id_token");


        NonceState nonceState = ltiDataService.getRepos().nonceStateRepository.findByStateHash(state);
        if (nonceState != null) {
            Jws<Claims> stateClaims = ltijwtService.validateState(nonceState.getState());
            String nonce = stateClaims.getBody().getId();
            String tohash = id_token + state + nonce;
            String expected_hash = Hashing.sha256()
                    .hashString(tohash, StandardCharsets.UTF_8)
                    .toString();
            model.addAttribute("expected_state", state);
            model.addAttribute("expected_nonce", nonce);
            try {
                String token = ltijwtService.generateStateNonceTokenJWT(expected_hash);
                model.addAttribute("token", token);
            } catch (GeneralSecurityException | IOException e) {
                log.error("Error generating state nonce token JWT", e);
                model.addAttribute(TextConstants.ERROR, "Error generating state nonce token JWT");
                return TextConstants.LTI3ERROR;
            }
            model.addAttribute("id_token", id_token);
            model.addAttribute("ltiStorageTarget", nonceState.getLtiStorageTarget());


            //We need to get the PlatformDeployment based on the iss, clientId and ltiDeploymentId
            String iss = stateClaims.getBody().get("original_iss", String.class);
            String clientId = stateClaims.getBody().get("clientId", String.class);
            String ltiDeploymentId = stateClaims.getBody().get("ltiDeploymentId", String.class);

            if (ltiDeploymentId == null) { //If we did not receive the deployment id in the oidc part we will need to trust the on in the id_Token
                Jws<Claims> idtokenClains = ltijwtService.validateJWT(id_token,clientId);
                ltiDeploymentId = idtokenClains.getPayload().get("https://purl.imsglobal.org/spec/lti/claim/deployment_id").toString();
            }

            List<PlatformDeployment> platformDeployment = ltiDataService.getRepos().platformDeploymentRepository.findByIssAndClientIdAndDeploymentId(iss, clientId, ltiDeploymentId);
            if (platformDeployment.isEmpty()) {
                // not registered yet (see LTI3Request); every deployment of a platform shares its OIDC endpoint
                platformDeployment = ltiDataService.getRepos().platformDeploymentRepository.findByIssAndClientId(iss, clientId);
            }
            if (!platformDeployment.isEmpty()) {
                model.addAttribute("oidc_authorization_uri", platformDeployment.get(0).getOidcEndpoint());
            } else {
                log.error("Error getting PlatformDeployment");
                model.addAttribute(TextConstants.ERROR, "Error getting oidc_authorization_uri from the PlatformDeployment");
                return TextConstants.LTI3ERROR;
            }
        }else{
            model.addAttribute(TextConstants.ERROR, "State was not as expected");
            return TextConstants.LTI3ERROR;
        }
        return "nonceStateCheck";
    }

    @RequestMapping(value = "/stateNonceChecked", method = {RequestMethod.GET, RequestMethod.POST})
    public String lti3checked(HttpServletRequest req, Model model) throws DataServiceException, ConnectionException {
        //There is a filter LTI3OAuthProviderProcessingFilterStateNonceChecked.java that is executed before this.
        //Some security checks, the processing of the LTIRequest as an object
        //and some database updates are performed there.

        //We validate the hash.
        String state = req.getParameter("state");
        String nonce = req.getParameter("nonce");
        String token = req.getParameter("token");
        String id_token = req.getParameter("id_token");
        String expected_state = req.getParameter("expected_state");
        String expected_nonce = req.getParameter("expected_nonce");
        String cookies = req.getParameter("cookies");


        String tohash = id_token + expected_state + expected_nonce;
        String expected_hash = Hashing.sha256()
                .hashString(tohash, StandardCharsets.UTF_8)
                .toString();

        Jws<Claims> claims_token = ltijwtService.validateNonceState(token);

        if (!claims_token.getBody().get("expected_hash").equals(expected_hash)){
            log.error("Hashes don't match");
            model.addAttribute(TextConstants.ERROR, "Token hashes don't match");
            return TextConstants.LTI3ERROR;
        }

        if (!state.equals(expected_state)){
            log.error("States don't match");
            model.addAttribute(TextConstants.ERROR, "State retrieved and the expected state don't match");
            return TextConstants.LTI3ERROR;
        }

        if (!nonce.equals(expected_nonce)){
            log.error("Nonces don't match");
            model.addAttribute(TextConstants.ERROR, "Nonce retrieved and the expected nonce don't match");
            return TextConstants.LTI3ERROR;
        }

        //We will use this link to find the content to display.
        NonceState nonceState = ltiDataService.getRepos().nonceStateRepository.findByStateHash(state);

        try {
            Jws<Claims> claims = ltijwtService.validateState(nonceState.getState());
            LTI3Request lti3Request = LTI3Request.getInstance(null);
            // This is just an extra check that we have added, but it is not necessary.
            // Checking that the clientId in the state (if sent in OIDC initiation request) matches the one coming with the ltiRequest.
            String clientIdFromState = claims.getBody().get("clientId") != null ? claims.getBody().get("clientId").toString() : null;
            if (clientIdFromState != null && !clientIdFromState.equals(lti3Request.getAud())) {
                model.addAttribute(TextConstants.ERROR, "Invalid Client Id");
                return TextConstants.LTI3ERROR;
            }
            // This is just an extra check that we have added, but it is not necessary.
            // Checking that the deploymentId in the state (if sent in the OIDC initiation request) matches the one coming with the ltiRequest.
            String deploymentIdFromState = claims.getBody().get("ltiDeploymentId") != null ? claims.getBody().get("ltiDeploymentId").toString() : null;
            if (deploymentIdFromState != null && !deploymentIdFromState.equals(lti3Request.getLtiDeploymentId())) {
                model.addAttribute(TextConstants.ERROR, "Invalid Deployment Id");
                return TextConstants.LTI3ERROR;
            }
            registerNoticeHandlers(lti3Request);
            // hand the launch to the app: it trades this one-time token for an API token, and reads
            // the launch back with it (see LaunchApiController)
            launchStore.put(lti3Request.getNonce(), new LaunchRecord(
                id_token,
                lti3Request.getAud(),
                new LinkedHashMap<>(lti3Request.getJws().getPayload()),
                requestedContent(lti3Request),
                Instant.now()
            ));
            ltiDataService.getRepos().nonceStateRepository.deleteById(expected_nonce);
            String oneTimeToken = apiJWTService.buildJwt(true, lti3Request);

            return "redirect:/app/?token=" + oneTimeToken;
        } catch (SignatureException ex) {
            model.addAttribute(TextConstants.ERROR, ex.getMessage());
            return TextConstants.LTI3ERROR;
        } catch (GeneralSecurityException e) {
            model.addAttribute(TextConstants.ERROR, e.getMessage());
            return TextConstants.LTI3ERROR;
        } catch (IOException e) {
            model.addAttribute(TextConstants.ERROR, e.getMessage());
            return TextConstants.LTI3ERROR;
        }
    }


    // the demo content the launch named with ?link=, if it's in this course
    LaunchRecord.Content requestedContent(LTI3Request lti3Request) {
        String link = LtiLinkUtils.linkParameter(lti3Request.getLtiTargetLinkUrl());

        if (link == null) {
            return null;
        }

        List<LtiLinkEntity> links = ltiLinkRepository.findByLtiLinkIdAndToolLinkToolLinkIdAndContext(lti3Request.getLtiLinkId(), link, lti3Request.getContext());

        if (links.isEmpty()) {
            return null;
        }

        ToolLink toolLink = links.get(0).getToolLink();

        return new LaunchRecord.Content(toolLink.getToolLinkId(), toolLink.getTitle(), toolLink.getDescription(), toolLink.getIsAssignment(), toolLink.getMaxGrade());
    }

    // if the platform offers course copy notices on this launch, register this tool's handler
    // for them (in the background, and only once per deployment); never fails the launch
    void registerNoticeHandlers(LTI3Request lti3Request) {
        try {
            if (!(lti3Request.getJws().getPayload().get(LtiStrings.LTI_PLATFORM_NOTIFICATION_SERVICE) instanceof Map<?, ?> pnsClaim)) {
                return;
            }

            List<PlatformDeployment> platformDeployments = ltiDataService.getRepos().platformDeploymentRepository
                .findByIssAndClientIdAndDeploymentId(lti3Request.getIss(), lti3Request.getAud(), lti3Request.getLtiDeploymentId());

            if (platformDeployments.size() == 1) {
                @SuppressWarnings("unchecked")
                Map<String, Object> claim = (Map<String, Object>) pnsClaim;
                noticeService.registerContextCopyHandler(platformDeployments.get(0).getKeyId(), claim);
            }
        } catch (RuntimeException e) {
            log.warn("Couldn't start the notice handler registration", e);
        }
    }

}
