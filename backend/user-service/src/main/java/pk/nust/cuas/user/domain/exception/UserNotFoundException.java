package pk.nust.cuas.user.domain.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/** No user exists with the given identifier. */
public class UserNotFoundException extends DomainException {

    public static final String CODE = "USER_NOT_FOUND";

    public UserNotFoundException(UUID userId) {
        super(CODE, HttpStatus.NOT_FOUND, "No user with id " + userId,
                Map.of("userId", userId.toString()), null);
    }
}
