package ly.lynk;

import org.springframework.boot.SpringApplication;

public class TestApplication {

    static void main(String[] args) {
        SpringApplication.from(Application::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
