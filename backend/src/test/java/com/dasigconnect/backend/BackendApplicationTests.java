package com.dasigconnect.backend;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class BackendApplicationTests {

	@MockitoBean
	private JavaMailSender javaMailSender;

	@Test
	void contextLoads() {
	}

}
