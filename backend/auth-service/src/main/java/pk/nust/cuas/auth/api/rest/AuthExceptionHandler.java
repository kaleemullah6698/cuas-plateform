package pk.nust.cuas.auth.api.rest;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;

/**
 * Translates auth-service exceptions into RFC 7807 responses.
 *
 * <p>Adds nothing of its own: the domain exceptions already carry the status and code the API
 * should return, and the shared base handles validation and the opaque fallback. Every
 * authentication failure is deliberately uniform, so there is no per-case handling to add here.
 */
@RestControllerAdvice
public class AuthExceptionHandler extends BaseExceptionHandler {
}
