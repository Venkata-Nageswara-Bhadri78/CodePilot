package com.developer.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.developer.copilot.config.EmailProperties;

@SpringBootApplication
@EnableConfigurationProperties(EmailProperties.class)
public class CopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(CopilotApplication.class, args);
	}

}
