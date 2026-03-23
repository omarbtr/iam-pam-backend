package com.iam.pam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IamPamBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(IamPamBackendApplication.class, args);
	}
}
