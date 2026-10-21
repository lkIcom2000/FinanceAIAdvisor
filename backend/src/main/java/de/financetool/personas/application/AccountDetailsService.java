package de.financetool.personas.application;

import de.financetool.personas.adapter.PersonaEntity;
import de.financetool.personas.adapter.PersonaRepository;
import de.financetool.personas.adapter.UserEntity;
import de.financetool.personas.adapter.UserRepository;
import de.financetool.shared.AuthenticatedUser;
import de.financetool.shared.Role;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.web.FinePrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads login accounts for Spring Security; the persona id of a session comes only from here. */
@Service
class AccountDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final PersonaRepository personas;

    AccountDetailsService(UserRepository users, PersonaRepository personas) {
        this.users = users;
        this.personas = personas;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserEntity user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        return new FinePrincipal(toAuthenticatedUser(user), user.getPasswordHash());
    }

    private AuthenticatedUser toAuthenticatedUser(UserEntity user) {
        if (user.getRole() != Role.PERSONA) {
            return new AuthenticatedUser(user.getUsername(), user.getRole(), null, user.getDisplayName());
        }
        PersonaEntity persona = personas.findById(user.getPersonaId())
                .orElseThrow(() -> new IllegalStateException("Account without persona: " + user.getUsername()));
        return new AuthenticatedUser(user.getUsername(), Role.PERSONA, new PersonaId(persona.getId()),
                persona.getDisplayName());
    }
}
