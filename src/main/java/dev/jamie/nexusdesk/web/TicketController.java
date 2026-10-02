package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
import java.util.ArrayList;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tickets")
public class TicketController {
    private final TicketService tickets;
    private final UserService users;
    public TicketController(TicketService tickets, UserService users) { this.tickets = tickets; this.users = users; }
    @GetMapping
    String list(Authentication auth, Model model, @RequestParam(defaultValue="") String q,
                @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="") String priority,
                @RequestParam(defaultValue="") String category, @RequestParam(required=false) Long creator,
                @RequestParam(required=false) Long assignee, @RequestParam(required=false) LocalDate from,
                @RequestParam(required=false) LocalDate to) {
        var me = users.current(auth);
        model.addAttribute("tickets", tickets.search(me, q, status, priority, category, creator, assignee, from, to));
        model.addAttribute("q", q); model.addAttribute("status", status); model.addAttribute("priority", priority);
        model.addAttribute("category", category); model.addAttribute("creator", creator); model.addAttribute("assignee", assignee);
        model.addAttribute("from", from); model.addAttribute("to", to);
        model.addAttribute("people", me.isStaff() ? users.list() : java.util.List.of(me));
        model.addAttribute("technicians", me.isStaff() ? users.technicians() : java.util.List.of());
        return "tickets";
    }
    @GetMapping("/new")
    String newTicket() { return "ticket-new"; }
    @PostMapping
    String create(Authentication auth, @RequestParam String title, @RequestParam String description,
                  @RequestParam String category, @RequestParam String priority, RedirectAttributes flash) {
        long id = tickets.create(users.current(auth), title, description, category, priority);
        flash.addFlashAttribute("success", "Ticket created. IT can now pick it up.");
        return "redirect:/tickets/" + id;
    }
    @GetMapping("/{id}")
    String detail(Authentication auth, @PathVariable long id, Model model) {
        var me = users.current(auth); var ticket = tickets.get(me, id);
        model.addAttribute("ticket", ticket); model.addAttribute("comments", tickets.comments(me, id));
        model.addAttribute("history", tickets.history(me, id));
        var next = new ArrayList<String>(); next.add(ticket.status()); next.addAll(TicketFlow.next(ticket.status()));
        model.addAttribute("nextStatuses", next); model.addAttribute("technicians", users.technicians());
        return "ticket-detail";
    }
    @PostMapping("/{id}")
    String update(Authentication auth, @PathVariable long id, @RequestParam int version, @RequestParam String status,
                  @RequestParam String priority, @RequestParam(required=false) Long assignee,
                  @RequestParam(defaultValue="") String resolution, RedirectAttributes flash) {
        tickets.update(users.current(auth), id, version, status, priority, assignee, resolution);
        flash.addFlashAttribute("success", "Ticket updated."); return "redirect:/tickets/" + id;
    }
    @PostMapping("/{id}/comments")
    String comment(Authentication auth, @PathVariable long id, @RequestParam String body, RedirectAttributes flash) {
        tickets.comment(users.current(auth), id, body);
        flash.addFlashAttribute("success", "Comment added."); return "redirect:/tickets/" + id;
    }
}
