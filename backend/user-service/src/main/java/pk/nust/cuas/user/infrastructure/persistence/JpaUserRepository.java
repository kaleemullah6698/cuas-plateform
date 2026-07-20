package pk.nust.cuas.user.infrastructure.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.commons.security.Role;
import pk.nust.cuas.user.domain.UserRepository;
import pk.nust.cuas.user.domain.model.User;
import pk.nust.cuas.user.domain.model.UserStatus;
import pk.nust.cuas.user.infrastructure.persistence.entity.RoleChangeLogEntity;
import pk.nust.cuas.user.infrastructure.persistence.entity.UserEntity;
import pk.nust.cuas.user.infrastructure.persistence.repository.RoleChangeLogJpaRepository;
import pk.nust.cuas.user.infrastructure.persistence.repository.UserJpaRepository;

/**
 * PostgreSQL-backed implementation of the user port.
 *
 * <p>Role codes are stored as strings and mapped back through {@link Role#valueOf}. An unknown
 * code is a hard failure rather than a silent skip: it means the database holds a role this build
 * does not define, and quietly dropping it would understate a user's authority in one direction or
 * hide an unrecognised grant in the other. Failing loudly is the safe reading.
 */
@Repository
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository users;
    private final RoleChangeLogJpaRepository roleChanges;

    public JpaUserRepository(UserJpaRepository users, RoleChangeLogJpaRepository roleChanges) {
        this.users = users;
        this.roleChanges = roleChanges;
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = users.findById(user.id()).orElseGet(UserEntity::new);
        entity.setId(user.id());
        entity.setUsername(user.username());
        entity.setEmail(user.email());
        entity.setDisplayName(user.displayName());
        entity.setStatus(user.status().name());
        entity.setRoleCodes(user.roles().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return toDomain(users.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return users.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return users.findByUsername(username).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return users.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return users.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll(int offset, int limit) {
        int safeLimit = Math.max(limit, 1);
        return users.findAll(PageRequest.of(offset / safeLimit, safeLimit,
                        Sort.by("username").ascending()))
                .map(this::toDomain)
                .getContent();
    }

    @Override
    @Transactional
    public void recordRoleChange(UUID userId, String roleCode, String changeType,
                                 String justification, String actorId) {
        RoleChangeLogEntity entry = new RoleChangeLogEntity();
        entry.setUserId(userId);
        entry.setRoleCode(roleCode);
        entry.setChangeType(changeType);
        entry.setJustification(justification);
        entry.setActorId(actorId);
        roleChanges.saveAndFlush(entry);
    }

    private User toDomain(UserEntity entity) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String code : entity.getRoleCodes()) {
            try {
                roles.add(Role.valueOf(code));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "User %s holds unknown role '%s'; the database and the Role enum disagree"
                                .formatted(entity.getId(), code), ex);
            }
        }
        return new User(entity.getId(), entity.getUsername(), entity.getEmail(),
                entity.getDisplayName(), UserStatus.valueOf(entity.getStatus()), roles,
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
}
