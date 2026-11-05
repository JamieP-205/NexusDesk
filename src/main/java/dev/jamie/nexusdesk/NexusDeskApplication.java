package dev.jamie.nexusdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NexusDeskApplication {
    public static void main(String[] args) throws Exception {
        var app = new SpringApplication(NexusDeskApplication.class);
        // "--demo" runs the downloadable demo without needing a launcher script (see LocalDemo).
        if (LocalDemo.requested(args)) args = LocalDemo.prepare(app, args);
        app.run(args);
    }
}
