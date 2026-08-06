package pk.nust.cuas.alert.infrastructure.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.alert.domain.port.EvidenceStore;

/**
 * A filesystem-backed {@link EvidenceStore} for development (ARCHITECTURE.md section 2.4.3).
 *
 * <p>MinIO — already the platform's content-addressed object store (section 6.6) — is the M8
 * infrastructure swap. Nothing above the port changes when it happens, which is the point of the
 * port existing now rather than later: the same shape track-correlation gives
 * {@code ProtectedAssetRegistry} while it waits for config-service.
 *
 * <p><strong>Paths are resolved against a configured root and checked to stay inside it.</strong>
 * A URI is operator-supplied when evidence is attached, and a store that resolved {@code ../..}
 * would read any file the process can — the classic traversal, made worse here by the fact that
 * whatever it read would then be hashed and recorded as evidence.
 */
public class FilesystemEvidenceStore implements EvidenceStore {

    private static final Logger log = LoggerFactory.getLogger(FilesystemEvidenceStore.class);
    private static final String SCHEME = "evidence://";
    private static final int BUFFER_BYTES = 8192;

    private final Path root;

    /**
     * @param root the directory every evidence object lives under
     */
    public FilesystemEvidenceStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public boolean verify(String uri, String sha256) {
        return resolve(uri)
                .filter(Files::isRegularFile)
                .map(path -> sha256.equalsIgnoreCase(hash(path)))
                .orElse(false);
    }

    @Override
    public Optional<Long> sizeOf(String uri) {
        return resolve(uri).filter(Files::isRegularFile).map(path -> {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                log.warn("Could not size the evidence object at {}", uri, ex);
                return null;
            }
        });
    }

    /**
     * @return the resolved path, or empty when the URI is malformed or escapes the root. Both are
     *         reported the same way on purpose: a caller learning <em>which</em> of the two happened
     *         would learn something about the filesystem outside the root.
     */
    private Optional<Path> resolve(String uri) {
        if (uri == null || !uri.startsWith(SCHEME)) {
            return Optional.empty();
        }
        Path candidate = root.resolve(uri.substring(SCHEME.length())).normalize();
        if (!candidate.startsWith(root)) {
            log.warn("Refusing an evidence path that escapes the store root");
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    private String hash(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            log.warn("Could not hash the evidence object at {}", path, ex);
            return "";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
