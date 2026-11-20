package aggregator;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class MainTest {
    @Test
    void delegatesApplicationStartupToSpringBoot() {
        String[] arguments = {"--spring.main.web-application-type=none"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            Main.main(arguments);

            springApplication.verify(() -> SpringApplication.run(Main.class, arguments));
        }
    }
}
