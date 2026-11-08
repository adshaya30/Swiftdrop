package com.swiftdrop.parcel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ParcelApplication {

	public static void main(String[] args) {
		SpringApplication.run(ParcelApplication.class, args);
	}

}
