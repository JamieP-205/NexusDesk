package dev.jamie.nexusdesk.hosted;

import javax.sql.DataSource;

/** The sandbox database the current request is working in. Set and cleared by {@link SandboxFilter}. */
public final class SandboxContext {
    private static final ThreadLocal<Sandbox> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<DataSource> DIRECT = new ThreadLocal<>();
    private SandboxContext() {}

    public static Sandbox current() { return CURRENT.get(); }
    static DataSource dataSource() {
        DataSource direct = DIRECT.get();
        if (direct != null) return direct;
        Sandbox sandbox = CURRENT.get();
        return sandbox == null ? null : sandbox.dataSource();
    }
    static void bind(Sandbox sandbox) { CURRENT.set(sandbox); }
    /** Used while building the template, which isn't a visitor sandbox. */
    static void bindDirect(DataSource dataSource) { DIRECT.set(dataSource); }
    static void clear() { CURRENT.remove(); DIRECT.remove(); }
}
