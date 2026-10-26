package de.financetool.profile.application;

import de.financetool.profile.adapter.ProfileRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persona reset of the profile module: the fixtures hold no profile, so the persona's profile rows are deleted. */
@Service
class ProfileResetter implements PersonaResettable {

    private final ProfileRepository profiles;

    ProfileResetter(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public String module() {
        return "profile";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        profiles.deletePersonaRows(personaId);
    }
}
