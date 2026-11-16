package org.example.quantflowbackend.global.config;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwaggerCompatibilityTest {
    @Test
    void runtimeHasOneSchemaAnnotationWithTheMethodRequiredBySwaggerCore() throws Exception {
        assertEquals(String.class, Schema.class.getDeclaredMethod("$dynamicRef").getReturnType());
        var resources = Schema.class.getClassLoader()
                .getResources("io/swagger/v3/oas/annotations/media/Schema.class");
        assertEquals(1, Collections.list(resources).size(), "Avoid duplicate legacy/Jakarta Swagger annotations");
    }
}
