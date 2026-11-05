package dev.jamie.nexusdesk;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;

/**
 * {@code java -jar nexusdesk.jar --demo}: the downloadable demo in one command, with no script needed.
 *
 * <p>It switches on the demo profile, keeps the demo database in the user's own app-data folder (so the
 * download can live anywhere, even somewhere read-only), picks a free port from 8080 upwards, and opens
 * the browser once the app is ready.</p>
 */
final class LocalDemo {
    static final String FLAG = "--demo";
    private LocalDemo() {}

    static boolean requested(String[] args) { return List.of(args).contains(FLAG); }

    /** Returns the arguments to start Spring with, after preparing the data folder and port. */
    static String[] prepare(SpringApplication app, String[] args) throws IOException {
        Path data = dataDirectory();
        Files.createDirectories(data);
        int port = freePort(8080, 8099);
        String url = "http://localhost:" + port + "/";
        var result = new ArrayList<String>();
        for (String arg : args) if (!arg.equals(FLAG)) result.add(arg);
        result.add("--spring.profiles.active=demo");
        result.add("--server.port=" + port);
        // Forward slashes work in H2 URLs on every OS, including paths with spaces.
        result.add("--spring.datasource.url=jdbc:h2:file:" + data.resolve("nexusdesk-demo").toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0;TRACE_LEVEL_FILE=0");
        app.setHeadless(false);
        app.addListeners((ApplicationReadyEvent event) -> ready(url, data));
        System.out.println("Starting the NexusDesk demo. It opens " + url + " in your browser when ready.");
        return result.toArray(String[]::new);
    }

    private static void ready(String url, Path data) {
        System.out.println();
        System.out.println("NexusDesk demo is running at " + url);
        System.out.println("Sign in as employee@example.test, tech@example.test or admin@example.test (password Demo-password-123).");
        System.out.println("Demo data is kept in " + data);
        System.out.println("Close this window or press Ctrl+C to stop.");
        // -Dnexusdesk.open-browser=false keeps automated checks from opening windows.
        if (!Boolean.parseBoolean(System.getProperty("nexusdesk.open-browser", "true"))) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException | UnsupportedOperationException e) {
            System.out.println("Open " + url + " in your browser.");
        }
    }

    /** %LOCALAPPDATA%\NexusDesk\demo on Windows; ~/.local/share/nexusdesk/demo elsewhere. */
    static Path dataDirectory() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return Path.of(local, "NexusDesk", "demo");
        String xdg = System.getenv("XDG_DATA_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".local", "share");
        return base.resolve("nexusdesk").resolve("demo");
    }

    static int freePort(int first, int last) {
        for (int port = first; port <= last; port++) {
            try (var socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
                return port;
            } catch (IOException inUse) {
                // Try the next one.
            }
        }
        throw new IllegalStateException("Ports " + first + "-" + last + " are all in use. Close another copy of NexusDesk and try again.");
    }
}
