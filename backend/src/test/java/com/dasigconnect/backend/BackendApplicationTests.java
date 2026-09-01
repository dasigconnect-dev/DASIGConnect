package com.dasigconnect.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.dasigconnect.backend.external.MailTransport;

@SpringBootTest
class BackendApplicationTests {

	@MockitoBean
	private MailTransport mailTransport;

	@Test
	void contextLoads() {
	}

}
