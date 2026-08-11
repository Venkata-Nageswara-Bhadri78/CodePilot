package com.developer.copilot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SwaggerApiDocsTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void testSwaggerApiDocsEndpoint() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        System.out.println("=== API DOCS STATUS: " + result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        System.out.println("=== API DOCS BODY LENGTH: " + body.length());
        System.out.println("=== API DOCS BODY PREVIEW: " + body.substring(0, Math.min(200, body.length())));
    }

    @Test
    void testSwaggerUiIndex() throws Exception {
        MvcResult result = mockMvc.perform(get("/swagger-ui/index.html"))
                .andDo(print())
                .andReturn();
        System.out.println("=== SWAGGER UI STATUS: " + result.getResponse().getStatus());
    }
}
