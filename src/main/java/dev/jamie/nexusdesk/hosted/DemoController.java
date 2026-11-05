package dev.jamie.nexusdesk.hosted;

import dev.jamie.nexusdesk.config.AccountDetailsService;
import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * One-click demo access. Starting a demo creates the visitor's sandbox and signs them in as one of
 * its fictional accounts, so there's no shared password for anyone to change or lock.
 */
@Controller
@Profile("hosted")
public class DemoController {
    private static final Map<String, String> LANDING = Map.of("employee", "/tickets", "technician", "/", "admin", "/");
    private static final String CSRF_KEY = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private final SandboxRegistry registry;
    private final UserService users;
    private final AccountDetailsService accounts;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    private final int trustedHops;

    public DemoController(SandboxRegistry registry, UserService users, AccountDetailsService accounts,
                          @org.springframework.beans.factory.annotation.Value("${nexusdesk.hosted.trusted-proxy-hops:0}") int trustedHops) {
        this.registry = registry; this.users = users; this.accounts = accounts; this.trustedHops = trustedHops;
    }

    @PostMapping("/demo/start")
    String start(@RequestParam(defaultValue = "") String role, HttpServletRequest request, HttpServletResponse response,
                 RedirectAttributes flash) {
        if (!LANDING.containsKey(role)) return "redirect:/login";
        Sandbox sandbox = SandboxContext.current();
        if (sandbox == null) {
            try {
                sandbox = registry.create(client(request));
            } catch (SandboxRegistry.Busy busy) {
                flash.addFlashAttribute("demoError", busy.getMessage());
                return "redirect:/login";
            }
            request.getSession(true).setAttribute(SandboxFilter.SESSION_KEY, sandbox.id());
            SandboxContext.bind(sandbox);
        }
        return signIn(role, request, response, flash);
    }

    @PostMapping("/demo/switch")
    String switchRole(@RequestParam(defaultValue = "") String role, HttpServletRequest request, HttpServletResponse response,
                      RedirectAttributes flash) {
        if (!LANDING.containsKey(role) || SandboxContext.current() == null) return "redirect:/login";
        return signIn(role, request, response, flash);
    }

    @PostMapping("/demo/reset")
    String reset(@RequestParam(defaultValue = "employee") String role, HttpServletRequest request, HttpServletResponse response,
                 RedirectAttributes flash) {
        Sandbox old = SandboxContext.current();
        if (old == null) return "redirect:/login";
        Sandbox fresh;
        try {
            fresh = registry.create(client(request));
        } catch (SandboxRegistry.Busy busy) {
            flash.addFlashAttribute("error", busy.getMessage());
            return "redirect:/";
        }
        request.getSession().setAttribute(SandboxFilter.SESSION_KEY, fresh.id());
        registry.remove(old.id());
        SandboxContext.bind(fresh);
        flash.addFlashAttribute("info", "Your demo has been reset to the original fictional data.");
        return signIn(LANDING.containsKey(role) ? role : "employee", request, response, flash);
    }

    /**
     * The visitor's address for the per-visitor limits, read from the hop the hosting proxy adds
     * (nexusdesk.hosted.trusted-proxy-hops from the right of X-Forwarded-For). Entries a visitor writes
     * themselves are never used, so nobody can claim someone else's address. With no trusted hop
     * configured, this returns null and only the overall limits apply.
     */
    private String client(HttpServletRequest request) {
        if (trustedHops < 1) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return null;
        String[] parts = forwarded.split(",");
        return parts.length >= trustedHops ? parts[parts.length - trustedHops].strip() : null;
    }

    private String signIn(String role, HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        Long id = registry.userFor(role);
        Account account = id == null ? null : users.findOrNull(id);
        if (account == null || !account.enabled()) {
            var current = SecurityContextHolder.getContext().getAuthentication();
            boolean signedIn = current != null && current.isAuthenticated()
                    && !(current instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
            flash.addFlashAttribute(signedIn ? "error" : "demoError",
                    "That demo account is disabled or no longer exists in your demo. Sign in as the administrator to "
                            + "enable it, or use Reset demo to start again.");
            return signedIn ? "redirect:/" : "redirect:/login";
        }
        var user = accounts.loadUserByUsername(account.email());
        // A new session ID and CSRF token on every sign-in, as with the password form.
        request.changeSessionId();
        request.getSession().removeAttribute(CSRF_KEY);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        flash.addFlashAttribute("success", "You're signed in as " + account.name() + " ("
                + (account.isAdmin() ? "administrator" : account.isStaff() ? "IT technician" : "employee") + ").");
        return "redirect:" + LANDING.get(role);
    }
}
