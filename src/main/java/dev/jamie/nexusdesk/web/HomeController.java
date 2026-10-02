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
    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/") String home(Authentication auth, Model model) {
        var me = users.current(auth);
        if (!me.isStaff()) return "redirect:/tickets";
        model.addAttribute("totals", dashboard.totals(me));
        var categories = dashboard.categories(me);
        model.addAttribute("categoriesChart", categories);
        model.addAttribute("prioritiesChart", dashboard.priorities(me));
        model.addAttribute("chartMax", Math.max(1, categories.stream().mapToLong(DashboardService.Bar::total).sum()));
        model.addAttribute("recent", tickets.search(me, "", "", "", "", null, null, null, null).stream().limit(6).toList());
        return "dashboard";
    }
}
