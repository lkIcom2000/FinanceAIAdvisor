package de.financetool.personas.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "persona_state")
public class PersonaStateEntity {

    @Id
    @Column(name = "persona_id")
    private String personaId;

    @Column(name = "simulated_date", nullable = false)
    private LocalDate simulatedDate;

    @Column(name = "fixture_version", nullable = false)
    private String fixtureVersion;

    protected PersonaStateEntity() {
    }

    public String getPersonaId() {
        return personaId;
    }

    public LocalDate getSimulatedDate() {
        return simulatedDate;
    }

    public String getFixtureVersion() {
        return fixtureVersion;
    }
}
