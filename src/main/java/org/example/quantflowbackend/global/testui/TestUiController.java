package org.example.quantflowbackend.global.testui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "app.test-ui.enabled", havingValue = "true")
public class TestUiController {
    private final String googleClientId;

    public TestUiController(@Value("${app.oauth2.google.client-id}") String googleClientId) {
        this.googleClientId = googleClientId;
    }

    @GetMapping("/test-ui/config")
    public ResponseEntity<Map<String, String>> config() {
        // A Google web client ID is public. No secrets or broker credentials are exposed.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("googleClientId", googleClientId));
    }
}
