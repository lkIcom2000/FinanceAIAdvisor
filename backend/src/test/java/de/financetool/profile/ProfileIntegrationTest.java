package de.financetool.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.PostgresIntegrationTest;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Story 6.1: the confirm control writes each field with {@code confirmed_at}; reset deletes the persona's rows. */
class ProfileIntegrationTest extends PostgresIntegrationTest {

    static final PersonaId TIM = new PersonaId("p-tim");
    static final PersonaId JONAS = new PersonaId("p-jonas");
    static final Instant CONFIRMED = Instant.parse("2026-10-26T10:00:00Z");

    @Autowired
    Profiles profiles;

    @Autowired
    List<PersonaResettable> owners;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM suitability_profiles");
        jdbc.update("DELETE FROM needs_profiles");
    }

    void confirm(PersonaId persona, Map<String, Object> answers) {
        tx.executeWithoutResult(status -> profiles.confirm(new PersonaScope(persona), answers, CONFIRMED));
    }

    Map<String, Object> stored(PersonaId persona) {
        return tx.execute(status -> profiles.stored(new PersonaScope(persona)));
    }

    @Test
    void confirmedAnswersAreStoredPerTableWithConfirmedAtAndReadPersonaScoped() {
        confirm(TIM, Map.of("risk_tolerance", "medium", "net_income", 3200, "household", "single"));
        confirm(JONAS, Map.of("occupation", "student"));

        assertThat(stored(TIM))
                .isEqualTo(Map.of("risk_tolerance", "medium", "net_income", 3200.0, "household", "single"));
        assertThat(jdbc.queryForList(
                "SELECT field FROM suitability_profiles WHERE persona_id = 'p-tim' ORDER BY field", String.class))
                .containsExactly("net_income", "risk_tolerance");
        assertThat(jdbc.queryForList("SELECT field FROM needs_profiles WHERE persona_id = 'p-tim'", String.class))
                .containsExactly("household");
        assertThat(jdbc.queryForList("SELECT DISTINCT confirmed_at FROM suitability_profiles WHERE persona_id = 'p-tim'",
                java.sql.Timestamp.class))
                .extracting(java.sql.Timestamp::toInstant).containsExactly(CONFIRMED);
    }

    @Test
    void aSecondConfirmationReplacesTheField() {
        confirm(TIM, Map.of("risk_tolerance", "medium"));
        confirm(TIM, Map.of("risk_tolerance", "low"));

        assertThat(stored(TIM)).isEqualTo(Map.of("risk_tolerance", "low"));
    }

    @Test
    void anUnknownFieldIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> confirm(TIM, Map.of("risk_tolerance", "medium", "favourite_colour", "blue")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("favourite_colour");
        assertThat(stored(TIM)).isEmpty();
    }

    @Test
    void anEmptyValueIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> confirm(TIM, Map.of("risk_tolerance", "medium", "goal", "")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("goal");
        assertThat(stored(TIM)).isEmpty();
    }

    @Test
    void aValueThatDoesNotFitItsCardFieldIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> confirm(TIM, Map.of("risk_tolerance", "medium", "goal", "lottery")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pick one of the choices");
        assertThatThrownBy(() -> confirm(TIM, Map.of("pension_situation", "a\u0000b")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stored(TIM)).isEmpty();
    }

    @Test
    void resetDeletesOnlyThePersonasProfile() {
        confirm(TIM, Map.of("risk_tolerance", "medium", "household", "single"));
        confirm(JONAS, Map.of("occupation", "student"));

        PersonaResettable owner = owners.stream().filter(o -> o.module().equals("profile")).findFirst().orElseThrow();
        tx.executeWithoutResult(status -> owner.reset(TIM));

        assertThat(stored(TIM)).isEmpty();
        assertThat(stored(JONAS)).isEqualTo(Map.of("occupation", "student"));
        assertThat(ResetPersonaUseCase.RESET_ORDER).contains("profile");
    }
}
