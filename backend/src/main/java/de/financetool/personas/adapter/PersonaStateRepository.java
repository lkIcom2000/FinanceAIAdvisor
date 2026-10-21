package de.financetool.personas.adapter;

import de.financetool.shared.PersonaScope;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** The persona's simulated clock and fixture version; reads take a {@link PersonaScope} (AD-11). */
public interface PersonaStateRepository extends Repository<PersonaStateEntity, String> {

    Optional<PersonaStateEntity> findByPersonaId(String personaId);

    default Optional<PersonaStateEntity> find(PersonaScope scope) {
        return findByPersonaId(scope.id());
    }

    boolean existsByPersonaId(String personaId);

    /** Every persona's state, for startup fixture seeding (no persona of a request involved). */
    List<PersonaStateEntity> findAllByOrderByPersonaIdAsc();

    /** Researcher operations (persona id from the research path); returns the number of updated rows. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PersonaStateEntity s set s.simulatedDate = :date where s.personaId = :personaId")
    int updateSimulatedDate(@Param("personaId") String personaId, @Param("date") LocalDate date);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PersonaStateEntity s set s.simulatedDate = :date, s.fixtureVersion = :fixtureVersion "
            + "where s.personaId = :personaId")
    int resetState(@Param("personaId") String personaId, @Param("date") LocalDate date,
            @Param("fixtureVersion") String fixtureVersion);
}
