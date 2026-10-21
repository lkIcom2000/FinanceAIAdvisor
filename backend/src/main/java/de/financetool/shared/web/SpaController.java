package de.financetool.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the Angular app for its client-side routes, so that a reload or deep link works. */
@Controller
class SpaController {

    @GetMapping({"/login"})
    String forwardToApp() {
        return "forward:/index.html";
    }
}
