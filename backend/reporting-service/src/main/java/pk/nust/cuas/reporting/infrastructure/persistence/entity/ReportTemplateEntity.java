package pk.nust.cuas.reporting.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for {@code report_templates} — the seeded catalogue of available reports. */
@Entity
@Table(name = "report_templates")
public class ReportTemplateEntity {

    @Id
    @Column(name = "type")
    private String type;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "formats", nullable = false)
    private String formats;

    protected ReportTemplateEntity() {
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String formats() {
        return formats;
    }
}
