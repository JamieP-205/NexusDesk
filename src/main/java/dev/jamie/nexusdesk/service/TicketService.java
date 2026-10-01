package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.*;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
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
    public List<Ticket> search(Account actor, String query, String status, String priority, String category,
                               Long creator, Long assignee, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!actor.isStaff()) { sql.append(" AND t.creator_id=?"); params.add(actor.id()); }
        if (query != null && !query.isBlank()) {
            if (query.length() > 140) throw Problem.invalid("Search is limited to 140 characters.");
            sql.append(" AND LOWER(t.title) LIKE ? ESCAPE '!'");
            params.add("%" + query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        addFilter(sql, params, "t.status", status, Rules.STATUSES);
        addFilter(sql, params, "t.priority", priority, Rules.PRIORITIES);
        addFilter(sql, params, "t.category", category, Rules.CATEGORIES);
        if (creator != null) { sql.append(" AND t.creator_id=?"); params.add(creator); }
        if (assignee != null) { sql.append(" AND t.assignee_id=?"); params.add(assignee); }
        if (from != null && to != null && from.isAfter(to)) throw Problem.invalid("The end date must be on or after the start date.");
        if (from != null) { sql.append(" AND t.created_at>=?"); params.add(from.atStartOfDay()); }
        if (to != null) { sql.append(" AND t.created_at<?"); params.add(to.plusDays(1).atStartOfDay()); }
        sql.append(" ORDER BY t.updated_at DESC, t.id DESC");
        return db.query(sql.toString(), MAPPER, params.toArray());
    }
    private void addFilter(StringBuilder sql, List<Object> args, String column, String value, List<String> allowed) {
        if (value == null || value.isBlank()) return;
        Rules.choice(value, allowed, column);
        // Only the fixed column names above reach this method. Values stay as bound parameters.
        sql.append(" AND ").append(column).append("=?"); args.add(value);
    }
    @Transactional
    public long create(Account actor, String title, String description, String category, String priority) {
        String cleanTitle = Rules.text(title, "Title", 140);
        String cleanDescription = Rules.text(description, "Description", 6000);
        Rules.choice(category, Rules.CATEGORIES, "category"); Rules.choice(priority, Rules.PRIORITIES, "priority");
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
    @Transactional
    public void update(Account actor, long id, int version, String status, String priority, Long assignee, String resolution) {
        Rules.staff(actor);
        Ticket old = get(actor, id);
        if (old.version() != version) throw new Problem(409, "Someone updated this ticket. Reload it before saving.");
        Rules.choice(status, Rules.STATUSES, "status"); Rules.choice(priority, Rules.PRIORITIES, "priority");
        TicketFlow.check(old.status(), status);
        if (status.equals("Open")) assignee = null;
        if (!status.equals("Open") && assignee == null) throw Problem.invalid("Choose a technician before moving this ticket forward.");
        if (assignee != null) {
            db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", assignee);
            Account assigned = users.find(assignee);
            if (!assigned.enabled() || !assigned.isStaff()) throw Problem.invalid("Choose an active technician.");
        }
        boolean finished = status.equals("Resolved") || status.equals("Closed");
        String cleanResolution = finished ? Rules.text(resolution, "Resolution", 3000) : null;
        var resolvedAt = finished ? (old.resolvedAt() == null ? java.time.LocalDateTime.now() : old.resolvedAt()) : null;
        int changed = db.update("""
                UPDATE tickets SET status=?,priority=?,assignee_id=?,resolution=?,resolved_at=?,
                updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=?
                """, status, priority, assignee, cleanResolution, resolvedAt, id, version);
        if (changed != 1) throw new Problem(409, "Someone updated this ticket. Reload it before saving.");
        if (!old.status().equals(status)) history(id, actor, "Status: " + old.status() + " → " + status);
        if (!old.priority().equals(priority)) history(id, actor, "Priority: " + old.priority() + " → " + priority);
        if (!Objects.equals(old.assigneeId(), assignee)) history(id, actor, "Assigned technician: " +
                (old.assigneeName() == null ? "Unassigned" : old.assigneeName()) + " → " + (assignee == null ? "Unassigned" : users.find(assignee).name()));
        if (!Objects.equals(old.resolution(), cleanResolution) && cleanResolution != null) db.update("INSERT INTO ticket_history(ticket_id,actor_id,event,details) VALUES (?,?,?,?)", id, actor.id(), "Saved resolution notes", cleanResolution);
    }
    @Transactional
    public void comment(Account actor, long id, String body) {
        db.queryForList("SELECT id FROM tickets WHERE id=? FOR UPDATE", id);
        Ticket ticket = get(actor, id);
        if (ticket.status().equals("Closed")) throw Problem.invalid("Reopen this ticket before adding a comment.");
        db.update("INSERT INTO comments(ticket_id,author_id,body) VALUES (?,?,?)", id, actor.id(), Rules.text(body, "Comment", 3000));
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
