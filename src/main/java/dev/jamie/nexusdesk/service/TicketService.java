package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.*;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
    public static final String STALE = "Someone else saved this ticket while you were editing it. Nothing was saved.";
    private final JdbcTemplate db;
    private final UserService users;
    private static final DataClassRowMapper<Ticket> MAPPER = new DataClassRowMapper<>(Ticket.class);
    private static final String SELECT = """
            SELECT t.*, u.name AS creator_name, a.name AS assignee_name FROM tickets t
            JOIN users u ON t.creator_id=u.id LEFT JOIN users a ON t.assignee_id=a.id
            """;
    public TicketService(JdbcTemplate db, UserService users) { this.db = db; this.users = users; }

    public Ticket get(Account actor, long id) {
        var rows = db.query(SELECT + " WHERE t.id=?", MAPPER, id);
        if (rows.isEmpty()) throw Problem.missing();
        Ticket ticket = rows.get(0);
        if (!actor.isStaff() && ticket.creatorId() != actor.id()) throw Problem.missing();
        return ticket;
    }

    /** Searches the tickets this person may see and returns one page, newest activity first. */
    public TicketPage search(Account actor, TicketFilter filter, int page, int size) {
        if (size < 1 || size > 100) throw Problem.invalid("Page size must be between 1 and 100.");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        var v = new Validation();
        // Employees only ever see their own tickets, whatever filters they send.
        if (!actor.isStaff()) { where.append(" AND t.creator_id=?"); params.add(actor.id()); }
        String query = filter.q();
        if (query != null && !query.isBlank()) {
            if (query.length() > 140) v.reject("q", "Search is limited to 140 characters.");
            where.append(" AND LOWER(t.title) LIKE ? ESCAPE '!'");
            params.add("%" + query.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        addFilter(v, where, params, "status", "t.status", filter.status(), Rules.STATUSES);
        addFilter(v, where, params, "priority", "t.priority", filter.priority(), Rules.PRIORITIES);
        addFilter(v, where, params, "category", "t.category", filter.category(), Rules.CATEGORIES);
        if (filter.creator() != null) { where.append(" AND t.creator_id=?"); params.add(filter.creator()); }
        if (filter.assignee() != null) { where.append(" AND t.assignee_id=?"); params.add(filter.assignee()); }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            v.reject("to", "The end date must be on or after the start date.");
        }
        if (filter.from() != null) { where.append(" AND t.created_at>=?"); params.add(filter.from().atStartOfDay()); }
        if (filter.to() != null) { where.append(" AND t.created_at<?"); params.add(filter.to().plusDays(1).atStartOfDay()); }
        v.check();

        long total = db.queryForObject("SELECT COUNT(*) FROM tickets t" + where, Long.class, params.toArray());
        int pages = (int) Math.max(1, (total + size - 1) / size);
        int current = Math.min(Math.max(page, 1), pages);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) (current - 1) * size);
        var items = db.query(SELECT + where + " ORDER BY t.updated_at DESC, t.id DESC LIMIT ? OFFSET ?", MAPPER, pageParams.toArray());
        return new TicketPage(items, current, size, total);
    }

    private void addFilter(Validation v, StringBuilder sql, List<Object> args, String field, String column, String value, List<String> allowed) {
        if (value == null || value.isBlank()) return;
        if (!allowed.contains(value)) { v.reject(field, "Choose a valid " + field + "."); return; }
        // Only the fixed column names above reach this method. Values stay as bound parameters.
        sql.append(" AND ").append(column).append("=?"); args.add(value);
    }

    @Transactional
    public long create(Account actor, String title, String description, String category, String priority) {
        var v = new Validation();
        String cleanTitle = v.text("title", title, "Title", 140);
        String cleanDescription = v.text("description", description, "Description", 6000);
        v.choice("category", category, Rules.CATEGORIES, "category");
        v.choice("priority", priority, Rules.PRIORITIES, "priority");
        v.check();
        var key = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tickets(title,description,category,priority,creator_id) VALUES (?,?,?,?,?)", new String[]{"id"});
            statement.setString(1, cleanTitle); statement.setString(2, cleanDescription);
            statement.setString(3, category); statement.setString(4, priority); statement.setLong(5, actor.id());
            return statement;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        history(id, actor, "Created the ticket");
        return id;
    }

    /**
     * Applies a technician's changes and records each one in the history, all in one transaction.
     * Returns a short description of each change; an empty list means nothing changed and nothing was written.
     */
    @Transactional
    public List<String> update(Account actor, long id, int version, String status, String priority, Long assignee, String resolution) {
        Rules.staff(actor);
        // Lock the row first so a comment or another save can't slip in between the checks and the write.
        db.queryForList("SELECT id FROM tickets WHERE id=? FOR UPDATE", id);
        Ticket old = get(actor, id);
        if (old.version() != version) throw Problem.conflict(STALE);

        var v = new Validation();
        v.choice("status", status, Rules.STATUSES, "status");
        v.choice("priority", priority, Rules.PRIORITIES, "priority");
        if (!v.has("status") && !TicketFlow.allowed(old.status(), status)) {
            v.reject("status", "A ticket can't move from " + old.status() + " to " + status + ". From "
                    + old.status() + " it can move to " + String.join(" or ", TicketFlow.next(old.status())) + ".");
        }
        Account technician = null;
        if (assignee != null) {
            db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", assignee);
            technician = users.findOrNull(assignee);
            boolean keepingCurrent = assignee.equals(old.assigneeId());
            // A new assignment must go to active IT staff. Keeping the existing technician is allowed,
            // so a finished ticket can still be closed after its technician has left.
            if (technician == null || (!keepingCurrent && (!technician.enabled() || !technician.isStaff()))) {
                v.reject("assignee", "Choose an active IT technician.");
                technician = null;
            }
        }
        if (!v.has("status") && !v.has("assignee")) {
            if (!TicketFlow.needsTechnician(status) && assignee != null) {
                v.reject("assignee", old.status().equals("Open")
                        ? "An Open ticket has no technician. To give it to " + technician.name() + ", change the status to Assigned."
                        : "Reopening returns the ticket to the queue without a technician. Choose Unassigned, or keep the current status.");
            } else if (TicketFlow.needsTechnician(status) && assignee == null) {
                v.reject("assignee", "Choose a technician. A ticket that is " + status + " needs one.");
            }
        }
        boolean finished = !v.has("status") && TicketFlow.isFinished(status);
        String cleanResolution = null;
        if (finished) {
            if (resolution == null || resolution.isBlank()) v.reject("resolution", "Add resolution notes before marking the ticket " + status + ".");
            else cleanResolution = v.text("resolution", resolution, "Resolution notes", 3000);
        } else if (!v.has("status") && resolution != null && !resolution.isBlank()
                && !resolution.strip().equals(Objects.toString(old.resolution(), "").strip())) {
            v.reject("resolution", "Resolution notes are saved only when the ticket is Resolved or Closed. Choose Resolved, or post this as a comment.");
        }
        v.check();

        List<String> changes = new ArrayList<>();
        if (!old.status().equals(status)) changes.add("status " + old.status() + " → " + status);
        if (!old.priority().equals(priority)) changes.add("priority " + old.priority() + " → " + priority);
        if (!Objects.equals(old.assigneeId(), assignee)) changes.add("technician " + nameOr(old.assigneeName()) + " → " + nameOr(technician == null ? null : technician.name()));
        boolean resolutionChanged = cleanResolution != null && !Objects.equals(old.resolution(), cleanResolution);
        if (resolutionChanged) changes.add("resolution notes saved");
        if (changes.isEmpty()) return changes;

        var resolvedAt = finished ? (old.resolvedAt() == null ? LocalDateTime.now() : old.resolvedAt()) : null;
        int changed = db.update("""
                UPDATE tickets SET status=?,priority=?,assignee_id=?,resolution=?,resolved_at=?,
                updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=?
                """, status, priority, assignee, cleanResolution, resolvedAt, id, version);
        if (changed != 1) throw Problem.conflict(STALE);
        if (!old.status().equals(status)) history(id, actor, "Status: " + old.status() + " → " + status);
        if (!old.priority().equals(priority)) history(id, actor, "Priority: " + old.priority() + " → " + priority);
        if (!Objects.equals(old.assigneeId(), assignee)) history(id, actor, "Assigned technician: " +
                nameOr(old.assigneeName()) + " → " + nameOr(technician == null ? null : technician.name()));
        if (resolutionChanged) db.update("INSERT INTO ticket_history(ticket_id,actor_id,event,details) VALUES (?,?,?,?)",
                id, actor.id(), "Saved resolution notes", cleanResolution);
        return changes;
    }

    private static String nameOr(String name) { return name == null ? "Unassigned" : name; }

    @Transactional
    public void comment(Account actor, long id, String body) {
        db.queryForList("SELECT id FROM tickets WHERE id=? FOR UPDATE", id);
        Ticket ticket = get(actor, id);
        if (ticket.status().equals("Closed")) throw ValidationProblem.of("body", "Reopen this ticket before adding a comment.");
        var v = new Validation();
        String clean = v.text("body", body, "Comment", 3000);
        v.check();
        db.update("INSERT INTO comments(ticket_id,author_id,body) VALUES (?,?,?)", id, actor.id(), clean);
        db.update("UPDATE tickets SET updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?", id);
        history(id, actor, "Added a comment");
    }
    public List<Activity> comments(Account actor, long id) {
        get(actor, id);
        return db.query("SELECT c.id,u.name,c.body,c.created_at FROM comments c JOIN users u ON c.author_id=u.id WHERE c.ticket_id=? ORDER BY c.id",
                new DataClassRowMapper<>(Activity.class), id);
    }
    public List<Activity> history(Account actor, long id) {
        get(actor, id);
        return db.query("SELECT h.id,u.name,CONCAT(h.event, CASE WHEN h.details IS NULL THEN '' ELSE CONCAT(CHAR(10), h.details) END) AS body,h.created_at FROM ticket_history h JOIN users u ON h.actor_id=u.id WHERE h.ticket_id=? ORDER BY h.id DESC",
                new DataClassRowMapper<>(Activity.class), id);
    }
    private void history(long id, Account actor, String event) {
        db.update("INSERT INTO ticket_history(ticket_id,actor_id,event) VALUES (?,?,?)", id, actor.id(), event);
    }
}
