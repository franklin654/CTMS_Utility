package com.ctms.ctms_backend;

import org.springframework.boot.SpringApplication;

public class TestCtmsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(CtmsBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
