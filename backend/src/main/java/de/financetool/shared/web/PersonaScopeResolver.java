package de.financetool.shared.web;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.Role;
import de.financetool.shared.domain.PersonaId;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Supplies a {@link PersonaScope} controller parameter from the authenticated principal only. Nothing in the request
 * (path, query, body) can influence which persona a request acts for.
 */
public class PersonaScopeResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PersonaScope.class.equals(parameter.getParameterType());
    }

    @Override
    public PersonaScope resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return currentScope();
    }

    /**
     * The scope a researcher acts in on the persona a research path names (AD-11: the researcher acts on any
     * persona). Only for an authenticated {@code RESEARCHER}; the caller checks that the persona exists.
     *
     * @throws AccessDeniedException without a researcher session
     */
    public static PersonaScope forResearcher(PersonaId personaId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof FinePrincipal principal
                && principal.user().role() == Role.RESEARCHER) {
            return new PersonaScope(personaId);
        }
        throw new AccessDeniedException("A researcher session is required");
    }

    static PersonaScope currentScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof FinePrincipal principal
                && principal.user().role() == Role.PERSONA) {
            return new PersonaScope(principal.user().personaId());
        }
        throw new AccessDeniedException("A persona session is required");
    }
}
