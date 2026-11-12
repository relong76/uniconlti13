package net.unicon.lti.controller.app;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Vue app built into static/app (see src/main/javascript). Its routes live after the
 * #, so the server only ever serves this one page.
 */
@Controller
public class AppController {

    @GetMapping({"/app", "/app/"})
    public String app() {
        return "forward:/app/index.html";
    }

}
