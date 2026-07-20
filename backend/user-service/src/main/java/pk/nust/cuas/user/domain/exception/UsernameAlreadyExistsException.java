package pk.nust.cuas.user.domain.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/** The requested username or email is already taken. */
public class UsernameAlreadyExistsException extends DomainException {

    public static final String CODE = "USERNAME_ALREADY_EXISTS";

    public UsernameAlreadyExistsException(String field, String value) {
        super(CODE, HttpStatus.CONFLICT, "That %s is already registered".formatted(field),
                Map.of("field", field, "value", value), null);
    }
}
