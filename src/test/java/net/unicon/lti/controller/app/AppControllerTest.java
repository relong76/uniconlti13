package net.unicon.lti.controller.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppControllerTest {

    @Test
    public void testServesTheAppsPage() {
        assertEquals("forward:/app/index.html", new AppController().app());
    }

}
