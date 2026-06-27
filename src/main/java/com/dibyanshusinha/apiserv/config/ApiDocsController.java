package com.dibyanshusinha.apiserv.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ApiDocsController {

    @GetMapping("/api_docs")
    public String apiDocs() {
        return "redirect:/api_docs/index.html?url=/api-contract/openapi.yaml&runtimeServer=true";
    }
}
