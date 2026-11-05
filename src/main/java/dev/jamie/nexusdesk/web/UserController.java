package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.service.UserService;
import dev.jamie.nexusdesk.service.ValidationProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Administrator only: SecurityConfig restricts /users/** to ROLE_ADMIN, and UserService checks again. */
@Controller
@RequestMapping("/users")
public class UserController {
    private final UserService users;
    private final Guide guide;
    public UserController(UserService users, Guide guide) { this.users = users; this.guide = guide; }

    @GetMapping
    String list(Model model) {
        model.addAttribute("people", users.list());
        if (!model.containsAttribute("form")) model.addAttribute("form", Map.of("role", "EMPLOYEE"));
        return "users";
    }

    @PostMapping
    String create(Authentication auth, Model model, HttpServletResponse response, @RequestParam(defaultValue="") String name,
                  @RequestParam(defaultValue="") String email, @RequestParam(defaultValue="") String role,
                  @RequestParam(defaultValue="") String password, RedirectAttributes flash, HttpServletRequest request) {
        try {
            var me = users.current(auth);
            users.create(me, name, email, role, password);
            guide.done(request, me, "account");
            flash.addFlashAttribute("success", "Account created for " + name.strip() + ". Share the password with them securely.");
            return "redirect:/users";
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            model.addAttribute("errors", problem.fields());
            // The password is deliberately not sent back to the browser.
            model.addAttribute("form", Map.of("name", name, "email", email, "role", role));
            return list(model);
        }
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        var person = users.find(id);
        model.addAttribute("person", person);
        model.addAttribute("form", Map.of("name", person.name(), "email", person.email(), "role", person.role(), "enabled", person.enabled()));
        return "user-edit";
    }

    @PostMapping("/{id}")
    String update(Authentication auth, @PathVariable long id, Model model, HttpServletResponse response,
                  @RequestParam(defaultValue="") String name, @RequestParam(defaultValue="") String email,
                  @RequestParam(defaultValue="") String role, @RequestParam(defaultValue="false") boolean enabled,
                  RedirectAttributes flash, HttpServletRequest request) {
        try {
            var me = users.current(auth);
            var before = users.find(id);
            users.edit(me, id, name, email, role, enabled);
            if (!before.role().equals(role) || before.enabled() != enabled) guide.done(request, me, "access");
            flash.addFlashAttribute("success", "Account updated for " + name.strip() + ".");
            return "redirect:/users";
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            model.addAttribute("errors", problem.fields());
            model.addAttribute("person", users.find(id));
            model.addAttribute("form", Map.of("name", name, "email", email, "role", role, "enabled", enabled));
            return "user-edit";
        }
    }
}
