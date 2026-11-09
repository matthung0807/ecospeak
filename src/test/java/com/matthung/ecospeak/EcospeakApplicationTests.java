package com.matthung.ecospeak;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.matthung.ecospeak.service.DialogueService;

@SpringBootTest
class EcospeakApplicationTests {

    @MockitoBean
    DialogueService dialogueService;

	@Test
	void contextLoads() {
	}

}
