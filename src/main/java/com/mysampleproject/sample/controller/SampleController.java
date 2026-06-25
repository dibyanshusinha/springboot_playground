package com.mysampleproject.sample.controller;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleController {
  @GetMapping("/defaultHello")
    public ResponseEntity<Map<String,String>> defaultHello(
            @RequestParam(required = false) String message) {

        Map<String, String> response = new HashMap<>();
        if (message == null) {
            response.put("echo", "Hello World!");
        } else {
            response.put("echo", "Hello " + message);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/customHello")
    public ResponseEntity<?> customHello(@RequestParam(required = false) String message) {

        if (message == null) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("echo", "Custom " + message);

        return ResponseEntity.ok(response);
    }
}
