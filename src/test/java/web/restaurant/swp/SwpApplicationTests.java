package web.restaurant.swp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
class SwpApplicationTests {

	@Test
	void contextLoads() {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		String hash = "$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y";
		String[] guesses = {"Admin123!", "admin", "password", "Admin123", "admin123", "liteflow", "phamdung", "phamdung.2301"};
		for (String guess : guesses) {
			if (encoder.matches(guess, hash)) {
				System.out.println(">>> MATCH FOUND: " + guess);
			}
		}
		System.out.println(">>> ENCODED Admin123!: " + encoder.encode("Admin123!"));
	}

}
