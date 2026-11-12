package net.unicon.lti.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The admin login is read from lti13.admin.*, falling back to the older terracotta.admin.* names.
 */
public class AdminLoginPropertiesTest {

    private static String resolve(String field, MockEnvironment environment) throws Exception {
        String expression = WebSecurityConfig.class.getDeclaredField(field).getAnnotation(Value.class).value();
        return environment.resolveRequiredPlaceholders(expression);
    }

    @Test
    public void testReadsTheLti13Names() throws Exception {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("lti13.admin.user", "new-admin")
            .withProperty("lti13.admin.password", "new-password")
            .withProperty("terracotta.admin.user", "old-admin")
            .withProperty("terracotta.admin.password", "old-password");

        assertEquals("new-admin", resolve("adminUser", environment));
        assertEquals("new-password", resolve("adminPassword", environment));
    }

    @Test
    public void testFallsBackToTheTerracottaNames() throws Exception {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("terracotta.admin.user", "old-admin")
            .withProperty("terracotta.admin.password", "old-password");

        assertEquals("old-admin", resolve("adminUser", environment));
        assertEquals("old-password", resolve("adminPassword", environment));
    }

    @Test
    public void testDefaultsWhenNeitherIsSet() throws Exception {
        // a password of "admin" makes WebSecurityConfig generate a random one
        MockEnvironment environment = new MockEnvironment();

        assertEquals("admin", resolve("adminUser", environment));
        assertEquals("admin", resolve("adminPassword", environment));
    }

}
