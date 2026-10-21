package de.financetool.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.shared.AuthenticatedUser;
import de.financetool.shared.Role;
import de.financetool.shared.domain.PersonaId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class FinePrincipalTest {

    /** Sessions hold the principal; a persisted or replicated session must survive a serialization round trip. */
    @Test
    void survivesSessionSerialization() throws Exception {
        FinePrincipal principal = new FinePrincipal(
                new AuthenticatedUser("p-lena", Role.PERSONA, new PersonaId("p-lena"), "Lena"), "{bcrypt}hash");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(principal);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            FinePrincipal copy = (FinePrincipal) in.readObject();
            assertThat(copy.user()).isEqualTo(principal.user());
        }
    }
}
