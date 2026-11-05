package dev.jamie.nexusdesk;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error pages are a second pass through the server (an "error dispatch"), which MockMvc doesn't
 * simulate, so this uses a real server. The error pass must still see the visitor's sandbox: without
 * it, the account check found no such user and signed the visitor out, deleting their sandbox.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@ActiveProfiles("hosted")
class HostedErrorPageTest {
    @LocalServerPort int port;
    final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void aForbiddenOrMissingPageKeepsTheVisitorSignedIn() throws Exception {
        var matcher = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(get("/login").body());
        assertThat(matcher.find()).isTrue();
        String form = "role=employee&_csrf=" + URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8);
        var started = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/demo/start"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(started.uri().getPath()).isEqualTo("/tickets");

        assertThat(get("/users").statusCode()).isEqualTo(403);
        assertThat(get("/tickets/999999").statusCode()).isEqualTo(404);

        var after = get("/tickets");
        assertThat(after.uri().getPath()).isEqualTo("/tickets");
        assertThat(after.statusCode()).isEqualTo(200);
    }
}
