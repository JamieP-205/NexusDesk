package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.Account;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    public record Bar(String label, long total) {}
    private final JdbcTemplate db;
    public DashboardService(JdbcTemplate db) { this.db = db; }
    public Map<String, Long> totals(Account actor) {
        Rules.staff(actor);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Open workload", count("SELECT COUNT(*) FROM tickets WHERE status NOT IN ('Resolved','Closed')"));
        counts.put("Critical", count("SELECT COUNT(*) FROM tickets WHERE priority='Critical' AND status NOT IN ('Resolved','Closed')"));
        counts.put("Assigned to me", count("SELECT COUNT(*) FROM tickets WHERE assignee_id=? AND status NOT IN ('Resolved','Closed')", actor.id()));
        counts.put("Resolved this week", count("SELECT COUNT(*) FROM tickets WHERE resolved_at>=? AND status IN ('Resolved','Closed')", LocalDateTime.now().minusDays(7)));
        counts.put("Available assets", count("SELECT COUNT(*) FROM assets WHERE status='Available'"));
        return counts;
    }
    public List<Bar> categories(Account actor) {
        Rules.staff(actor);
        return db.query("SELECT category, COUNT(*) AS total FROM tickets GROUP BY category ORDER BY total DESC, category",
                (rs, row) -> new Bar(rs.getString(1), rs.getLong(2)));
    }
    public List<Bar> priorities(Account actor) {
        Rules.staff(actor);
        return db.query("SELECT priority,COUNT(*) AS total FROM tickets GROUP BY priority ORDER BY CASE priority WHEN 'Critical' THEN 1 WHEN 'High' THEN 2 WHEN 'Medium' THEN 3 ELSE 4 END",
                (rs, row) -> new Bar(rs.getString(1), rs.getLong(2)));
    }
    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
