package pk.nust.cuas.user.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.user.domain.model.User;

/**
 * Persistence port for user accounts, declared by the domain and implemented by infrastructure.
 */
public interface UserRepository {

    /**
     * Inserts or updates a user.
     *
     * @param user the user to persist
     * @return the persisted user, with generated fields populated
     */
    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * @param offset rows to skip
     * @param limit  maximum rows to return
     * @return users ordered by username
     */
    List<User> findAll(int offset, int limit);

    /**
     * Records a role change with its justification (ARCHITECTURE.md section 7.4).
     *
     * @param userId        the user whose roles changed
     * @param roleCode      the role
     * @param changeType    {@code ASSIGNED} or {@code REVOKED}
     * @param justification why the change was made; required, never blank
     * @param actorId       who made it
     */
    void recordRoleChange(UUID userId, String roleCode, String changeType,
                          String justification, String actorId);
}
