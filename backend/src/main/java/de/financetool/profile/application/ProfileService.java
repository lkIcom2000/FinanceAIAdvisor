package de.financetool.profile.application;

import de.financetool.profile.AssessmentFields;
import de.financetool.profile.ProfileCompleteness;
import de.financetool.profile.ProfileFields;
import de.financetool.profile.Profiles;
import de.financetool.profile.adapter.ProfileRepository;
import de.financetool.profile.adapter.ProfileRepository.Table;
import de.financetool.shared.PersonaScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The profile module's only writer: the confirm control (AD-12). */
@Service
class ProfileService implements Profiles {

    private final ProfileRepository profiles;

    ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> stored(PersonaScope scope) {
        Map<String, Object> out = new LinkedHashMap<>(profiles.fields(scope, Table.SUITABILITY));
        out.putAll(profiles.fields(scope, Table.NEEDS));
        return out;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirm(PersonaScope scope, Map<String, Object> answers, Instant confirmedAt) {
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        answers.keySet().stream().filter(field -> !ProfileFields.known(field)).findFirst().ifPresent(field -> {
            throw new IllegalArgumentException("Unknown profile field: " + field);
        });
        Map<String, Object> values = new LinkedHashMap<>();
        answers.forEach((field, value) -> {
            if (!ProfileCompleteness.present(value)) {
                throw new IllegalArgumentException("Empty value for profile field: " + field);
            }
            values.put(field, AssessmentFields.normalize(field, value));
        });
        values.forEach((field, value) -> profiles.upsert(scope, ProfileFields.NEEDS.contains(field) ? Table.NEEDS
                : Table.SUITABILITY, field, value, confirmedAt));
    }
}
