package dev.jamie.nexusdesk.hosted;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * The application's only DataSource in the hosted demo. Every connection goes to the current
 * visitor's sandbox; requests without a sandbox (the sign-in page, static files) get the empty lobby.
 * Services and SQL are unchanged: they can't tell they're running in a sandbox.
 */
final class SandboxRoutingDataSource extends AbstractDataSource {
    private final DataSource lobby;

    SandboxRoutingDataSource(DataSource lobby) { this.lobby = lobby; }

    private DataSource target() {
        DataSource sandbox = SandboxContext.dataSource();
        return sandbox == null ? lobby : sandbox;
    }

    @Override public Connection getConnection() throws SQLException { return target().getConnection(); }
    @Override public Connection getConnection(String username, String password) throws SQLException {
        return target().getConnection(username, password);
    }
}
