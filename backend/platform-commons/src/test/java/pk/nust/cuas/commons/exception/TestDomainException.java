package pk.nust.cuas.commons.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Concrete {@link DomainException} used only by the commons test suite to exercise the base type
 * and the exception handler, standing in for the per-service subclasses defined elsewhere.
 */
class TestDomainException extends DomainException {

    TestDomainException(String code, HttpStatus status, String message) {
        super(code, status, message);
    }

    TestDomainException(String code, HttpStatus status, String message, Map<String, Object> props) {
        super(code, status, message, props, null);
    }
}
