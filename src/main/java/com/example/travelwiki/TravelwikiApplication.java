package com.example.travelwiki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TravelwikiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TravelwikiApplication.class, args);
	}

}
