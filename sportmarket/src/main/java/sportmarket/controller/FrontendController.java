package sportmarket.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** React Router handles these pages after the packaged index has loaded. */
@Controller
@Profile("production")
public class FrontendController {
    @GetMapping({"/", "/login", "/register", "/my-listings", "/listings/new",
            "/listings/{id}", "/listings/{id}/edit"})
    public String index() {
        return "forward:/index.html";
    }
}
