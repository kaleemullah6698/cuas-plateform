package pk.nust.cuas.reporting.infrastructure.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.reporting.domain.model.ArtifactRef;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.port.ArtifactStore;

/**
 * A filesystem-backed {@link ArtifactStore} for development (ARCHITECTURE.md §4.1.10).
 *
 * <p>MinIO — the platform's content-addressed object store (§6.6) — is the production swap behind the
 * port, nothing above it changing; the same shape alert-service's evidence store has. The store
 * computes the SHA-256 that makes an exported report verifiable, and on download re-hashes the bytes
 * and refuses them if they no longer match the reference — an artifact altered on disk is not served.
 * A URI is resolved against the configured root and checked to stay inside it.
 */
public class FilesystemArtifactStore implements ArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(FilesystemArtifactStore.class);
    private static final String SCHEME = "report://";

    private final Path root;

    public FilesystemArtifactStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public ArtifactRef store(UUID reportId, ReportFormat format, byte[] content, long rowCount) {
        String name = reportId + "." + format.extension();
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("refusing an artifact path outside the store root");
        }
        try {
            Files.createDirectories(root);
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("could not store the report artifact", ex);
        }
        return new ArtifactRef(SCHEME + name, hash(content), content.length, rowCount, format);
    }

    @Override
    public Optional<byte[]> load(ArtifactRef artifact) {
        return resolve(artifact.uri()).filter(Files::isRegularFile).flatMap(path -> {
            try {
                byte[] content = Files.readAllBytes(path);
                if (!artifact.sha256().equalsIgnoreCase(hash(content))) {
                    log.warn("Report artifact {} failed its hash check; refusing to serve it",
                            artifact.uri());
                    return Optional.<byte[]>empty();
                }
                return Optional.of(content);
            } catch (IOException ex) {
                log.warn("Could not read the report artifact at {}", artifact.uri(), ex);
                return Optional.empty();
            }
        });
    }

    private Optional<Path> resolve(String uri) {
        if (uri == null || !uri.startsWith(SCHEME)) {
            return Optional.empty();
        }
        Path candidate = root.resolve(uri.substring(SCHEME.length())).normalize();
        if (!candidate.startsWith(root)) {
            log.warn("Refusing an artifact path that escapes the store root");
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    private static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
