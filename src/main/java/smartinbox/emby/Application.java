package smartinbox.emby;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import smartinbox.emby.services.Trainer;

@SpringBootApplication
@EnableAsync
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public Trainer trainer(){
        return new Trainer();
    }

//    @Bean
//    public RestTemplate restTemplate(RestTemplateBuilder builder) {
//        return builder.build();
//    }
//
//
//    @Bean
//    public CommandLineRunner commandLineRunner() {
//        return (String... args) -> {
//
//        };
//    }
}