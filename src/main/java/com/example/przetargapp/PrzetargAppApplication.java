package com.example.przetargapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrzetargAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrzetargAppApplication.class, args);
	}

}
