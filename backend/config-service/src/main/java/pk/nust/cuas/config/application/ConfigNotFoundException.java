package pk.nust.cuas.config.application;

/** Raised when a namespace, or a requested version of it, does not exist. Rendered as 404. */
public class ConfigNotFoundException extends RuntimeException {

    public ConfigNotFoundException(String message) {
        super(message);
    }
}
