package com.stoxsim.campus.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.stoxsim.campus.api.CampusCompetitionDtos.*;

@Repository
public class CampusCompetitionRepository {
    private final JdbcTemplate jdbc;
    public CampusCompetitionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public JdbcTemplate jdbc() { return jdbc; }
    public static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private Institution institution(ResultSet rs, int row) throws SQLException {
        return new Institution(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email_domain"), rs.getBoolean("suspended"));
    }
    public List<Institution> directory(String query, boolean admin) {
        return jdbc.query("""
            SELECT id,name,email_domain,suspended FROM campus_institution
            WHERE (? OR NOT suspended) AND strpos(lower(name), lower(?)) > 0 ORDER BY name,id LIMIT 50
            """, this::institution, admin, query);
    }
    public Institution institution(UUID id, boolean lock) {
        return jdbc.query("SELECT id,name,email_domain,suspended FROM campus_institution WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            this::institution, id).stream().findFirst().orElse(null);
    }
    public String role(UUID institution, UUID user) {
        return jdbc.query("SELECT member_role FROM campus_membership WHERE institution_id=? AND user_id=?",
            (rs,row) -> rs.getString(1), institution,user).stream().findFirst().orElse(null);
    }
    public UUID membershipInstitution(UUID user) {
        return jdbc.query("SELECT institution_id FROM campus_membership WHERE user_id=?",
            (rs,row) -> rs.getObject(1,UUID.class),user).stream().findFirst().orElse(null);
    }
    public List<Member> members(UUID institution) {
        return jdbc.query("""
            SELECT u.id,u.display_name,m.member_role,m.joined_at FROM campus_membership m JOIN app_user u ON u.id=m.user_id
            WHERE m.institution_id=? ORDER BY m.joined_at,u.id LIMIT 200
            """, (rs,row) -> new Member(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("member_role"),instant(rs,"joined_at")),institution);
    }
    private static final String REQUEST = """
        SELECT r.*, i.name AS institution_name, u.display_name,u.email FROM campus_join_request r
        JOIN campus_institution i ON i.id=r.institution_id JOIN app_user u ON u.id=r.user_id
        """;
    private JoinRequest request(ResultSet rs,int row) throws SQLException {
        return new JoinRequest(rs.getObject("id",UUID.class),rs.getObject("institution_id",UUID.class),rs.getString("institution_name"),
            rs.getObject("user_id",UUID.class),rs.getString("display_name"),rs.getString("email"),rs.getString("note"),
            rs.getString("status"),rs.getString("review_note"),instant(rs,"submitted_at"));
    }
    public JoinRequest latest(UUID user) {
        return jdbc.query(REQUEST+" WHERE r.user_id=? ORDER BY r.submitted_at DESC,r.id LIMIT 1",this::request,user).stream().findFirst().orElse(null);
    }
    public JoinRequest request(UUID id) {
        return jdbc.query(REQUEST+" WHERE r.id=?",this::request,id).stream().findFirst().orElse(null);
    }
    public List<JoinRequest> pending(UUID institution) {
        return jdbc.query(REQUEST+" WHERE r.institution_id=? AND r.status='PENDING' ORDER BY r.submitted_at,r.id LIMIT 200",this::request,institution);
    }
    public List<Audit> audit(UUID institution) {
        return jdbc.query("""
            SELECT a.action,a.created_at,COALESCE(u.display_name,'Deleted account') AS actor,
                t.display_name AS target, c.title AS competition FROM campus_audit a LEFT JOIN app_user u ON u.id=a.actor_user_id
                LEFT JOIN app_user t ON t.id=a.target_user_id LEFT JOIN campus_competition c ON c.id=a.competition_id WHERE a.institution_id=? ORDER BY a.created_at DESC,a.id LIMIT 50
            """,(rs,row) -> new Audit(rs.getString("action"),rs.getString("actor"),rs.getString("target"),rs.getString("competition"),instant(rs,"created_at")),institution);
    }
    public void audit(UUID institution,UUID actor,UUID target,String action,UUID competition,Instant at) {
        jdbc.update("INSERT INTO campus_audit(institution_id,actor_user_id,target_user_id,action,competition_id,created_at) VALUES (?,?,?,?,?,?)",
            institution,actor,target,action,competition,Timestamp.from(at));
    }
    private static final String COMPETITION = """
        SELECT c.*,
            (SELECT count(*) FROM campus_competition_entry e WHERE e.competition_id=c.id AND e.withdrawn_at IS NULL) AS participants,
            EXISTS (SELECT 1 FROM campus_competition_entry e WHERE e.competition_id=c.id AND e.user_id=? AND e.withdrawn_at IS NULL) AS enrolled,
            EXISTS (SELECT 1 FROM campus_competition_entry e WHERE e.competition_id=c.id AND e.user_id=? AND e.withdrawn_at IS NOT NULL) AS withdrawn
        FROM campus_competition c
        """;
    private Competition competition(ResultSet rs,Instant now) throws SQLException {
        var start=instant(rs,"starts_at"); var end=instant(rs,"ends_at");
        String status=rs.getBoolean("cancelled") ? "CANCELLED" : now.isBefore(start) ? "SCHEDULED" : now.isBefore(end) ? "OPEN" : "ENDED";
        return new Competition(rs.getObject("id",UUID.class),rs.getObject("institution_id",UUID.class),rs.getString("title"),start,end,
            rs.getInt("capacity"),status,rs.getInt("participants"),rs.getBoolean("enrolled"),rs.getBoolean("withdrawn"),rs.getString("cancellation_note"));
    }
    public List<Competition> competitions(UUID institution,UUID user,Instant now) {
        return jdbc.query(COMPETITION+" WHERE c.institution_id=? ORDER BY c.starts_at DESC,c.id LIMIT 50",(rs,row)->competition(rs,now),user,user,institution);
    }
    public Competition competition(UUID id,UUID user,Instant now) {
        return jdbc.query(COMPETITION+" WHERE c.id=?",(rs,row)->competition(rs,now),user,user,id).stream().findFirst().orElse(null);
    }
}
