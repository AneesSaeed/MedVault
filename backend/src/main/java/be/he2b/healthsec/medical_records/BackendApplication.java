package be.he2b.healthsec.medical_records;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public final class BackendApplication {

	private BackendApplication() {
		// Prevent instantiation - this is the main application class
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
