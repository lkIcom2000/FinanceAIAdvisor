package de.financetool.personas.web;

import de.financetool.shared.AuthenticatedUser;
import de.financetool.shared.Role;
import de.financetool.shared.web.FinePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Session login for the SPA (AD-11): JSON in, JSON out, never a redirect. */
@Tag(name = "auth", description = "Session login")
@RestController
@RequestMapping("/api/auth")
class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record SessionInfo(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {

        static SessionInfo of(AuthenticatedUser user) {
            return new SessionInfo(user.role(), user.displayName());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @Operation(summary = "Log in with username and password; starts a session")
    @PostMapping("/login")
    SessionInfo login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        // Session fixation protection: a new session id for every login.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return SessionInfo.of(((FinePrincipal) authentication.getPrincipal()).user());
    }

    @Operation(summary = "End the session")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response,
            @Parameter(hidden = true) Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @Operation(summary = "The logged-in account")
    @GetMapping("/me")
    SessionInfo me(@Parameter(hidden = true) @AuthenticationPrincipal FinePrincipal principal) {
        return SessionInfo.of(principal.user());
    }

    @Operation(summary = "Issue the XSRF-TOKEN cookie; call once before the first state-changing request")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void csrf(@Parameter(hidden = true) CsrfToken token) {
        token.getToken();
    }
}
