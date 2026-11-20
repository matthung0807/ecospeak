package com.matthung.ecospeak;

import com.matthung.ecospeak.service.DialogueService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class EcospeakApplicationTests {

    @MockitoBean
    DialogueService dialogueService;

    @Test
    void contextLoads() {
    }
}
