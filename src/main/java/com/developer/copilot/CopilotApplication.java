package com.developer.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.developer.copilot.auth.config.EmailProperties;

@SpringBootApplication(excludeName = {
		"org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
		"org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration",
		"org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
@EnableConfigurationProperties(EmailProperties.class)
public class CopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(CopilotApplication.class, args);
	}

}
