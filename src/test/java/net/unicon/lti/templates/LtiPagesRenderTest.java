package net.unicon.lti.templates;

import net.unicon.lti.model.lti.dto.LoginInitiationDTO;
import net.unicon.lti.model.lti.dto.PlatformRegistrationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders each server-rendered LTI page with realistic values, so a template error shows up here
 * rather than in the middle of a launch. Set -Dlti.pages.out=<dir> to also write the HTML out.
 */
public class LtiPagesRenderTest {

    private SpringTemplateEngine engine;
    private JakartaServletWebApplication application;
    private final MockServletContext servletContext = new MockServletContext();

    @BeforeEach
    public void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        application = JakartaServletWebApplication.buildApplication(servletContext);
    }

    private String render(String template, Map<String, Object> variables) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        WebContext context = new WebContext(application.buildExchange(request, new MockHttpServletResponse()), Locale.ENGLISH, variables);
        String html = engine.process(template, context);

        String out = System.getProperty("lti.pages.out");
        if (out != null) {
            Files.createDirectories(Path.of(out));
            Files.writeString(Path.of(out, template + ".html"), html.replace("href=\"/css/", "href=\"css/"));
        }

        assertTrue(html.contains("/css/lti-pages.css"), template + " uses the shared stylesheet");
        assertTrue(html.contains("Unicon LTI 1.3 Demo"), template + " has the app bar");
        assertFalse(html.contains("bootstrap"), template + " no longer uses Bootstrap");
        assertFalse(html.contains("cdn."), template + " loads nothing from a CDN");
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("\\sid=\"([^\"]+)\"").matcher(html);
        while (idMatcher.find()) {
            assertTrue(ids.add(idMatcher.group(1)), template + " has the id " + idMatcher.group(1) + " more than once");
        }
        if (template.equals("oidcRedirect") || template.equals("nonceStateCheck")) {
            assertTrue(html.contains("src=\"/js/uuidv4.js\""), template + " uses the local uuidv4 script");
        }
        return html;
    }

    @Test
    public void testOidcRedirect() throws IOException {
        LoginInitiationDTO initiation = new LoginInitiationDTO("https://canvas.instructure.com", "login-hint-1", "https://tool.example.com/lti3",
            "message-hint-1", "202570000000000231", null, "284:abc", "_parent");
        Map<String, Object> variables = new java.util.HashMap<>(Map.of(
            "initiation_dto", initiation,
            "client_id_received", "202570000000000231",
            "deployment_id_received", "284:abc",
            "oidcEndpoint", "https://sso.canvaslms.com/api/lti/authorize_redirect",
            "oidcEndpointComplete", "https://sso.canvaslms.com/api/lti/authorize_redirect?client_id=202570000000000231&state=s",
            "client_id", "202570000000000231",
            "login_hint", "login-hint-1",
            "lti_message_hint", "message-hint-1",
            "nonce", "nonce-1",
            "prompt", "none"));
        variables.putAll(Map.of(
            "redirect_uri", "https://tool.example.com/lti3",
            "response_mode", "form_post",
            "response_type", "id_token",
            "scope", "openid",
            "state", "state-1",
            "oidc_authorization_uri", "https://sso.canvaslms.com/api/lti/authorize_redirect"));

        String html = render("oidcRedirect", variables);

        assertTrue(html.contains("Step 1 of 2"));
        assertTrue(html.contains("id=\"get_button\""));
        assertTrue(html.contains("id=\"post_button\""));
        assertTrue(html.contains("form=\"oidc-form\""));
        assertTrue(html.contains("id=\"oidc-form\" action=\"https://sso.canvaslms.com/api/lti/authorize_redirect\""));
        assertTrue(html.contains("name=\"state\" value=\"state-1\""));
        assertTrue(html.contains("login-hint-1"));
        // the inline script gets its values from the model
        assertTrue(html.contains("const stateHash = \"state-1\""));
    }

    @Test
    public void testNonceStateCheckWithFrameStorage() throws IOException {
        String html = render("nonceStateCheck", Map.of(
            "ltiStorageTarget", "_parent",
            "token", "security-token",
            "id_token", "eyJ.id.token",
            "expected_state", "state-1",
            "expected_nonce", "nonce-1",
            "oidc_authorization_uri", "https://sso.canvaslms.com/api/lti/authorize_redirect"));

        assertTrue(html.contains("Step 2 of 2"));
        assertTrue(html.contains("id=\"pmState\" name=\"state\" type=\"text\""));
        assertTrue(html.contains("id=\"cookies\" name=\"cookies\" type=\"hidden\" value=\"false\""));
        assertTrue(html.contains("form=\"endpoint-form\" id=\"post_button\""));
        assertTrue(html.contains("eyJ.id.token"));
        assertFalse(html.contains("Nothing to check here"));
        assertTrue(html.contains("<div class=\"form-field\"><label for=\"pmState\">"));
    }

    @Test
    public void testNonceStateCheckWithCookies() throws IOException {
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("ltiStorageTarget", null);
        variables.put("token", "security-token");
        variables.put("id_token", "eyJ.id.token");
        variables.put("expected_state", "state-1");
        variables.put("expected_nonce", "nonce-1");
        variables.put("oidc_authorization_uri", "https://sso.canvaslms.com/api/lti/authorize_redirect");

        String html = render("nonceStateCheck", variables);

        assertTrue(html.contains("Nothing to check here"));
        assertTrue(html.contains("id=\"cookies\" name=\"cookies\" type=\"hidden\" value=\"true\""));
        // the cookie flow posts the same fields, kept out of sight
        assertTrue(html.contains("<div class=\"form-field\" hidden=\"hidden\"><label for=\"pmState\">"));
        assertFalse(html.contains("Values to check"));
    }

    @Test
    public void testLaunchError() throws IOException {
        String html = render("lti3Error", Map.of("Error", "State was not as expected"));

        assertTrue(html.contains("The launch didn't work"));
        assertTrue(html.contains("State was not as expected"));
        assertTrue(html.contains("role=\"alert\""));
    }

    @Test
    public void testRegistrationError() throws IOException {
        String html = render("registrationError", Map.of("Error", "Can't get the platform configuration"));

        assertTrue(html.contains("Registration didn't work"));
        assertTrue(html.contains("Can&#39;t get the platform configuration"));
    }

    @Test
    public void testRegistrationRedirect() throws IOException {
        PlatformRegistrationDTO platform = new PlatformRegistrationDTO();
        platform.setIssuer("https://canvas.instructure.com");
        platform.setAuthorization_endpoint("https://sso.canvaslms.com/api/lti/authorize_redirect");
        platform.setJwks_uri("https://sso.canvaslms.com/api/lti/security/jwks");
        platform.setScopes_supported(List.of("openid"));

        String html = render("registrationRedirect", Map.of(
            "openid_configuration", "https://canvas.instructure.com/api/lti/security/openid-configuration",
            "registration_token", "registration-token",
            "platform_configuration", platform,
            "tool_configuration", "{\"client_name\":\"Unicon LTI 1.3 Demo\"}",
            "own_redirect_post_endpoint", "/registration/registration"));

        assertTrue(html.contains("Register the tool"));
        assertTrue(html.contains("action=\"/registration/registration\""));
        assertTrue(html.contains("https://sso.canvaslms.com/api/lti/security/jwks"));
        assertTrue(html.contains("Unicon LTI 1.3 Demo&quot;}"));
    }

    @Test
    public void testRegistrationConfirmation() throws IOException {
        String html = render("registrationConfirmationDemo", Map.of("registration_confirmation", "{\"client_id\":\"10000000000001\"}", "issuer", "https://canvas.instructure.com"));

        assertTrue(html.contains("function confirmRegistration()"));
        assertTrue(html.contains("onclick=\"confirmRegistration();\""));
        assertTrue(html.contains("10000000000001"));
    }

}
