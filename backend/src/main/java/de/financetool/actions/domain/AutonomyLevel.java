package de.financetool.actions.domain;

import org.springframework.modulith.NamedInterface;

/** The maximum autonomy level the Autonomy Gate grants an Action (FR-35); lower is more restrictive. */
@NamedInterface("types")
public enum AutonomyLevel {
    /** Fine only informs. */
    L0,
    /** Fine recommends; the user acts. */
    L1,
    /** Fine acts only after the user's confirmation. */
    L2,
    /** Fine acts within a Mandate and tells the user afterwards. */
    L3
}
