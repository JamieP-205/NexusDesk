package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }
    @GetMapping
    String list(Model model) { model.addAttribute("people", users.list()); return "users"; }
    @PostMapping
    String create(Authentication auth, @RequestParam String name, @RequestParam String email,
                  @RequestParam String role, @RequestParam String password, RedirectAttributes flash) {
        users.create(users.current(auth), name, email, role, password);
        flash.addFlashAttribute("success", "Account created."); return "redirect:/users";
    }
    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) { model.addAttribute("person", users.find(id)); return "user-edit"; }
    @PostMapping("/{id}")
    String update(Authentication auth, @PathVariable long id, @RequestParam String name, @RequestParam String email,
                  @RequestParam String role, @RequestParam(defaultValue="false") boolean enabled, RedirectAttributes flash) {
        users.edit(users.current(auth), id, name, email, role, enabled);
        flash.addFlashAttribute("success", "Account updated."); return "redirect:/users";
    }
}
