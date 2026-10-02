package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:assets;DB_CLOSE_DELAY=-1", "NEXUSDESK_ADMIN_PASSWORD=Test-password-123"})
@Transactional
class AssetServiceTest {
    @Autowired AssetService assets;
    @Autowired UserService users;
    @Autowired JdbcTemplate db;
    Account admin;
    Account employee;
    long id;
    @BeforeEach void setup() {
        admin = users.list().stream().filter(Account::isAdmin).findFirst().orElseThrow();
        users.create(admin, "Employee", "employee@example.test", "EMPLOYEE", "Test-password-123");
        employee = users.list().stream().filter(a -> !a.isStaff()).findFirst().orElseThrow();
        assets.create(admin, "Laptop", "Lenovo", "E14", "TEST-1", LocalDate.now());
        id = assets.list(admin, null).get(0).id();
    }
    @Test void assignsReturnsAndKeepsThePreviousOwner() {
        assets.assign(admin, id, employee.id());
        assertThat(assets.get(employee, id).ownerId()).isEqualTo(employee.id());
        assets.returnAsset(admin, id);
        assertThat(assets.get(admin, id).status()).isEqualTo("Available");
        assertThat(assets.list(employee, null)).isEmpty();
        assets.assign(admin, id, admin.id());
        assertThat(assets.history(admin, id)).hasSize(2);
    }
    @Test void rejectsDoubleAssignmentInTheServiceAndDatabase() {
        assets.assign(admin, id, employee.id());
        assertThatThrownBy(() -> assets.assign(admin, id, admin.id())).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> db.update("INSERT INTO asset_assignments(asset_id,user_id,assigned_by) VALUES (?,?,?)", id, admin.id(), admin.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assets.get(admin, id).ownerId()).isEqualTo(employee.id());
    }
    @Test void employeeCannotReadOrManageUnassignedEquipment() {
        assertThatThrownBy(() -> assets.get(employee, id)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> assets.assign(employee, id, employee.id())).isInstanceOf(Problem.class);
    }
    @Test void repairAndRetiredEquipmentCannotBeAssigned() {
        assets.edit(admin, id, 0, "Laptop", "Lenovo", "E14", "TEST-1", LocalDate.now(), "Repair");
        assertThatThrownBy(() -> assets.assign(admin, id, employee.id())).isInstanceOf(Problem.class);
        assets.edit(admin, id, 1, "Laptop", "Lenovo", "E14", "TEST-1", LocalDate.now(), "Retired");
        assertThatThrownBy(() -> assets.assign(admin, id, employee.id())).isInstanceOf(Problem.class);
    }
    @Test void validatesSerialDateAndStaleVersion() {
        assertThatThrownBy(() -> assets.create(admin, "Laptop", "Dell", "Test", "TEST-1", LocalDate.now())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> assets.create(admin, "Laptop", "Dell", "Test", "TEST-2", LocalDate.now().plusDays(1))).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> assets.edit(admin, id, 9, "Laptop", "Lenovo", "E14", "TEST-1", LocalDate.now(), "Available")).isInstanceOf(Problem.class);
    }
}
