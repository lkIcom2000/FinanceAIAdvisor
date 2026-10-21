package de.financetool.personas.adapter;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Account lookup for login only; not persona-scoped because it runs before a persona is known. */
public interface UserRepository extends Repository<UserEntity, String> {

    Optional<UserEntity> findByUsername(String username);
}
