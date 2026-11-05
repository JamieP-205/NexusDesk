package dev.jamie.nexusdesk.config;

/**
 * Chooses whose failures a sign-in attempt counts against. Normally that's the network address. The
 * hosted demo sits behind shared proxies, so it counts per visitor sandbox instead: one visitor can
 * only ever slow down their own sign-ins.
 */
@FunctionalInterface
public interface ThrottleKey {
    String clientFor(String remoteAddress);
}
