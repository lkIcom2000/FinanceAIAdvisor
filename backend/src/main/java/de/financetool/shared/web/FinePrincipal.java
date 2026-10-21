package de.financetool.shared.web;

import de.financetool.shared.AuthenticatedUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The Spring Security principal of every Fine session. */
public final class FinePrincipal implements UserDetails, CredentialsContainer {

    private final AuthenticatedUser user;
    private String passwordHash;

    public FinePrincipal(AuthenticatedUser user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
    }

    public AuthenticatedUser user() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.role().authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
