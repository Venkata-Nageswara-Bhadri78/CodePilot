package com.developer.copilot.util;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailTemplateService {

    private final TemplateEngine templateEngine;

    public String process(String templateName,
                          Map<String, Object> variables) {

        Context context = new Context();
        context.setVariables(variables);

        return templateEngine.process(templateName, context);
    }
}