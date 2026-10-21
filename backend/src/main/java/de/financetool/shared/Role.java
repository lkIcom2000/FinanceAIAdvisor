package de.financetool.shared;

/** The three account roles (AD-11). */
public enum Role {
    PERSONA,
    ADVISOR,
    RESEARCHER;

    /** The Spring Security authority for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
