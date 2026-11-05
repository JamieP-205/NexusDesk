package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final UserService users;
    private final DashboardService dashboard;
    private final TicketService tickets;
    public HomeController(UserService users, DashboardService dashboard, TicketService tickets) {
        this.users = users; this.dashboard = dashboard; this.tickets = tickets;
    }
    @GetMapping("/login") String login(@org.springframework.web.bind.annotation.RequestParam(required=false) String locked, Model model) {
        if (locked != null) {
            // Only a number is echoed back, so the message can't be spoofed through the URL.
            int minutes = 15;
            try { minutes = Math.max(1, Math.min(60, Integer.parseInt(locked))); } catch (NumberFormatException ignored) { }
            model.addAttribute("lockedMinutes", minutes);
        }
        return "login";
    }
    @GetMapping("/") String home(Authentication auth, Model model) {
        var me = users.current(auth);
        if (!me.isStaff()) return "redirect:/tickets";
        model.addAttribute("totals", dashboard.totals(me));
        var categories = dashboard.categories(me);
        model.addAttribute("categoriesChart", categories);
        model.addAttribute("prioritiesChart", dashboard.priorities(me));
        model.addAttribute("chartMax", Math.max(1, categories.stream().mapToLong(DashboardService.Bar::total).sum()));
        model.addAttribute("recent", tickets.search(me, TicketFilter.none(), 1, 6).items());
        return "dashboard";
    }
}
