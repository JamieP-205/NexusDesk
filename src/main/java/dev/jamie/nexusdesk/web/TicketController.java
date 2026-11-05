package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.model.Ticket;
import dev.jamie.nexusdesk.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/tickets")
public class TicketController {
    private final TicketService tickets;
    private final UserService users;
    private final int pageSize;
    private final Guide guide;
    public TicketController(TicketService tickets, UserService users, Guide guide, @Value("${nexusdesk.tickets.page-size:20}") int pageSize) {
        this.tickets = tickets; this.users = users; this.guide = guide; this.pageSize = pageSize;
    }

    @GetMapping
    String list(Authentication auth, Model model, HttpServletResponse response, @RequestParam(defaultValue="") String q,
                @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="") String priority,
                @RequestParam(defaultValue="") String category, @RequestParam(required=false) Long creator,
                @RequestParam(required=false) Long assignee, @RequestParam(required=false) LocalDate from,
                @RequestParam(required=false) LocalDate to, @RequestParam(defaultValue="1") String page) {
        var me = users.current(auth);
        var filter = new TicketFilter(q, status, priority, category, me.isStaff() ? creator : null, me.isStaff() ? assignee : null, from, to);
        TicketPage results;
        try {
            results = tickets.search(me, filter, parsePage(page), pageSize);
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            model.addAttribute("errors", problem.fields());
            results = new TicketPage(List.of(), 1, pageSize, 0);
        }
        model.addAttribute("results", results);
        model.addAttribute("filtered", !filter.isEmpty());
        model.addAttribute("pageLinks", pageLinks(filter, results));
        model.addAttribute("q", q); model.addAttribute("status", status); model.addAttribute("priority", priority);
        model.addAttribute("category", category); model.addAttribute("creator", filter.creator()); model.addAttribute("assignee", filter.assignee());
        model.addAttribute("from", from); model.addAttribute("to", to);
        model.addAttribute("people", me.isStaff() ? users.list() : List.of(me));
        model.addAttribute("technicians", me.isStaff() ? users.technicians() : List.of());
        return "tickets";
    }

    private static int parsePage(String page) {
        try { return Math.max(1, Integer.parseInt(page.strip())); } catch (NumberFormatException e) { return 1; }
    }

    /** Page links keep every active filter, so moving between pages never loses the search. */
    private static Map<String, Object> pageLinks(TicketFilter f, TicketPage results) {
        var base = UriComponentsBuilder.fromPath("/tickets");
        if (!f.q().isBlank()) base.queryParam("q", f.q());
        if (!f.status().isBlank()) base.queryParam("status", f.status());
        if (!f.priority().isBlank()) base.queryParam("priority", f.priority());
        if (!f.category().isBlank()) base.queryParam("category", f.category());
        if (f.creator() != null) base.queryParam("creator", f.creator());
        if (f.assignee() != null) base.queryParam("assignee", f.assignee());
        if (f.from() != null) base.queryParam("from", f.from());
        if (f.to() != null) base.queryParam("to", f.to());
        Map<String, Object> links = new LinkedHashMap<>();
        int current = results.page(), last = results.totalPages();
        List<Map<String, Object>> numbers = new ArrayList<>();
        Integer previousShown = null;
        for (int n = 1; n <= last; n++) {
            // Show the first, last and nearby pages; collapse the rest into a gap.
            if (n != 1 && n != last && Math.abs(n - current) > 2) continue;
            if (previousShown != null && n - previousShown > 1) numbers.add(Map.of("gap", true));
            numbers.add(Map.of("number", n, "url", url(base, n), "current", n == current, "gap", false));
            previousShown = n;
        }
        links.put("numbers", numbers);
        if (results.hasPrevious()) links.put("previous", url(base, current - 1));
        if (results.hasNext()) links.put("next", url(base, current + 1));
        return links;
    }
    private static String url(UriComponentsBuilder base, int page) {
        var copy = base.cloneBuilder();
        if (page > 1) copy.queryParam("page", page);
        return copy.encode().build().toUriString();
    }

    @GetMapping("/new")
    String newTicket(Model model) {
        model.addAttribute("form", Map.of("priority", "Medium"));
        return "ticket-new";
    }

    @PostMapping
    String create(Authentication auth, Model model, HttpServletResponse response, @RequestParam(defaultValue="") String title,
                  @RequestParam(defaultValue="") String description, @RequestParam(defaultValue="") String category,
                  @RequestParam(defaultValue="") String priority, RedirectAttributes flash, HttpServletRequest request) {
        try {
            var me = users.current(auth);
            long id = tickets.create(me, title, description, category, priority);
            guide.done(request, me, "raise");
            flash.addFlashAttribute("success", "Ticket ND-" + id + " created. IT can now pick it up.");
            return "redirect:/tickets/" + id;
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            model.addAttribute("errors", problem.fields());
            model.addAttribute("form", Map.of("title", title, "description", description, "category", category, "priority", priority));
            return "ticket-new";
        }
    }

    @GetMapping("/{id}")
    String detail(Authentication auth, @PathVariable long id, Model model) {
        var me = users.current(auth);
        var ticket = tickets.get(me, id);
        detailModel(me, ticket, model);
        model.addAttribute("formStatus", ticket.status());
        model.addAttribute("formPriority", ticket.priority());
        model.addAttribute("formAssignee", ticket.assigneeId());
        model.addAttribute("formResolution", ticket.resolution());
        return "ticket-detail";
    }

    private void detailModel(Account me, Ticket ticket, Model model) {
        long id = ticket.id();
        model.addAttribute("ticket", ticket); model.addAttribute("comments", tickets.comments(me, id));
        model.addAttribute("history", tickets.history(me, id));
        var next = new ArrayList<String>(); next.add(ticket.status()); next.addAll(TicketFlow.next(ticket.status()));
        model.addAttribute("nextStatuses", next);
        // The current technician stays in the list even after leaving IT, so the form shows the truth.
        var technicians = new ArrayList<>(users.technicians());
        if (ticket.assigneeId() != null && technicians.stream().noneMatch(t -> t.id() == ticket.assigneeId())) {
            var current = users.findOrNull(ticket.assigneeId());
            if (current != null) technicians.add(0, current);
        }
        model.addAttribute("technicians", technicians);
    }

    @PostMapping("/{id}")
    String update(Authentication auth, @PathVariable long id, Model model, HttpServletResponse response,
                  @RequestParam int version, @RequestParam(defaultValue="") String status,
                  @RequestParam(defaultValue="") String priority, @RequestParam(required=false) Long assignee,
                  @RequestParam(defaultValue="") String resolution, @RequestParam(required=false) String seenStatus,
                  @RequestParam(required=false) String seenPriority, @RequestParam(required=false) Long seenAssignee,
                  RedirectAttributes flash, HttpServletRequest request) {
        var me = users.current(auth);
        try {
            var changes = tickets.update(me, id, version, status, priority, assignee, resolution);
            if (!changes.isEmpty() && status.equals("Resolved")) guide.done(request, me, "resolve");
            if (!changes.isEmpty() && Objects.equals(assignee, me.id()) && !Objects.equals(seenAssignee, me.id())) guide.done(request, me, "take");
            if (changes.isEmpty()) flash.addFlashAttribute("info", "Nothing changed, so there was nothing to save.");
            else flash.addFlashAttribute("success", "Saved: " + String.join("; ", changes) + ".");
            return "redirect:/tickets/" + id;
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            model.addAttribute("errors", problem.fields());
        } catch (Problem problem) {
            if (!problem.isConflict()) throw problem;
            // Someone saved first. Show their version, and carry over only the fields this person
            // actually changed, so saving again can't quietly undo the other person's work.
            response.setStatus(409);
            var latest = tickets.get(me, id);
            boolean known = seenStatus != null && seenPriority != null;
            String keepStatus = known && status.equals(seenStatus) ? latest.status() : status;
            String keepPriority = known && priority.equals(seenPriority) ? latest.priority() : priority;
            Long keepAssignee = known && Objects.equals(assignee, seenAssignee) ? latest.assigneeId() : assignee;
            var kept = new ArrayList<String>();
            if (!keepStatus.equals(latest.status())) kept.add("status " + keepStatus);
            if (!keepPriority.equals(latest.priority())) kept.add("priority " + keepPriority);
            if (!Objects.equals(keepAssignee, latest.assigneeId())) kept.add("technician");
            if (!resolution.isBlank() && !resolution.strip().equals(Objects.toString(latest.resolution(), "").strip())) kept.add("resolution notes");
            model.addAttribute("error", problem.getMessage() + " The page now shows their changes."
                    + (kept.isEmpty() ? " Make your change again if it's still needed."
                       : " Your edits (" + String.join(", ", kept) + ") are still in the form: check them and save again."));
            detailModel(me, latest, model);
            model.addAttribute("formStatus", keepStatus);
            model.addAttribute("formPriority", keepPriority);
            model.addAttribute("formAssignee", keepAssignee);
            model.addAttribute("formResolution", resolution.isBlank() ? latest.resolution() : resolution);
            return "ticket-detail";
        }
        // Re-show the page with what the person chose, so nothing they typed is lost.
        detailModel(me, tickets.get(me, id), model);
        model.addAttribute("formStatus", status);
        model.addAttribute("formPriority", priority);
        model.addAttribute("formAssignee", assignee);
        model.addAttribute("formResolution", resolution);
        return "ticket-detail";
    }

    @PostMapping("/{id}/comments")
    String comment(Authentication auth, @PathVariable long id, Model model, HttpServletResponse response,
                   @RequestParam(defaultValue="") String body, RedirectAttributes flash, HttpServletRequest request) {
        var me = users.current(auth);
        try {
            tickets.comment(me, id, body);
            guide.done(request, me, "comment");
            flash.addFlashAttribute("success", "Comment added.");
            return "redirect:/tickets/" + id + "#conversation";
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            model.addAttribute("errors", problem.fields());
            model.addAttribute("commentDraft", body);
            var ticket = tickets.get(me, id);
            detailModel(me, ticket, model);
            model.addAttribute("formStatus", ticket.status());
            model.addAttribute("formPriority", ticket.priority());
            model.addAttribute("formAssignee", ticket.assigneeId());
            model.addAttribute("formResolution", ticket.resolution());
            return "ticket-detail";
        }
    }

    // --- Quick actions: one-press versions of the common steps, with the same rules as the full form ---

    @PostMapping("/{id}/take")
    String take(Authentication auth, @PathVariable long id, @RequestParam int version, RedirectAttributes flash, HttpServletRequest request) {
        var me = users.current(auth);
        return quick(id, flash, () -> {
            tickets.take(me, id, version);
            guide.done(request, me, "take");
            flash.addFlashAttribute("success", "It's yours. The ticket is now assigned to you.");
        });
    }

    @PostMapping("/{id}/resolve")
    String resolve(Authentication auth, @PathVariable long id, @RequestParam int version, @RequestParam(defaultValue="") String resolution,
                   RedirectAttributes flash, HttpServletRequest request) {
        var me = users.current(auth);
        return quick(id, flash, () -> {
            tickets.resolve(me, id, version, resolution);
            guide.done(request, me, "resolve");
            flash.addFlashAttribute("success", "Marked Resolved. The person who raised it can now confirm it's fixed or reopen it.");
        });
    }

    @PostMapping("/{id}/confirm")
    String confirm(Authentication auth, @PathVariable long id, @RequestParam int version, RedirectAttributes flash, HttpServletRequest request) {
        var me = users.current(auth);
        return quick(id, flash, () -> {
            tickets.confirmFixed(me, id, version);
            guide.done(request, me, "confirm");
            flash.addFlashAttribute("success", "Thanks for confirming. The ticket is now Closed.");
        });
    }

    @PostMapping("/{id}/reopen")
    String reopen(Authentication auth, @PathVariable long id, @RequestParam int version, @RequestParam(defaultValue="") String reason,
                  RedirectAttributes flash, HttpServletRequest request) {
        var me = users.current(auth);
        return quick(id, flash, () -> {
            tickets.reopen(me, id, version, reason);
            guide.done(request, me, "confirm");
            flash.addFlashAttribute("success", "Reopened. It's back in IT's queue with your comment.");
        });
    }

    /** Runs a quick action and returns to the ticket, with the reason in a message if it couldn't be done. */
    private static String quick(long id, RedirectAttributes flash, Runnable action) {
        try {
            action.run();
        } catch (ValidationProblem problem) {
            flash.addFlashAttribute("error", String.join(" ", problem.fields().values()));
        } catch (Problem problem) {
            if (problem.status() == 404 || problem.status() == 403) throw problem;
            flash.addFlashAttribute("error", problem.isConflict()
                    ? problem.getMessage() + " The page now shows the latest version." : problem.getMessage());
        }
        return "redirect:/tickets/" + id;
    }
}
