package de.financetool.personas.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "personas")
public class PersonaEntity {

    @Id
    private String id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected PersonaEntity() {
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}
