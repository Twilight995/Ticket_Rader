package com.wol32.ticketradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TicketRadarApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketRadarApplication.class, args);
	}

}
