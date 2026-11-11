package edu.seminolestate.reservation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VersionController {

    @GetMapping("/version")
    public String versionPage() {
        return "forward:/version.html";
    }
}
