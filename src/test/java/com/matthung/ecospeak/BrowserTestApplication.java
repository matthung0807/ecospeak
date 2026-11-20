package com.matthung.ecospeak;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.matthung.ecospeak.dto.DialogueLine;
import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.service.DialogueService;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Test-only launcher: real HTTP/storage/static pages with a deterministic provider stub. */
public class BrowserTestApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(EcospeakApplication.class, StubProvider.class);
        application.setDefaultProperties(
                java.util.Map.of(
                        "spring.main.allow-bean-definition-overriding",
                        "true",
                        "server.port",
                        "8081"));
        application.run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubProvider {
        @Bean
        DialogueService dialogueService() {
            var service = mock(DialogueService.class);
            when(service.generate(anyString()))
                    .thenAnswer(
                            call -> {
                                String scenario = call.getArgument(0);
                                var response = new DialogueResponse();
                                response.setTopic(scenario);
                                response.setTargetPhrase("Could I get");
                                response.setDialogue(
                                        List.of(
                                                line("A", "Could I get a coffee, please?"),
                                                line("B", "Of course. Anything else?"),
                                                line("A", "That's all, thanks."),
                                                line("B", "I'll have that ready shortly.")));
                                return response;
                            });
            return service;
        }

        private DialogueLine line(String speaker, String text) {
            var line = new DialogueLine();
            line.setSpeaker(speaker);
            line.setText(text);
            return line;
        }
    }
}
