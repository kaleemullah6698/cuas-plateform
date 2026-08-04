package pk.nust.cuas.ingestion.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Uuidv7GeneratorTest {

    @Test
    void carriesTheVersionAndVariantOfUuidv7() {
        UUID id = new Uuidv7Generator(new Random(1)).generate(Instant.now());

        assertThat(id.version()).isEqualTo(7);
        // Variant 2 is the RFC 4122/9562 layout ('10' in the two most-significant bits of the LSB).
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheTimestampInTheHighBitsSoIdsAreTimeSortable() {
        Instant earlier = Instant.ofEpochMilli(1_000_000_000_000L);
        Instant later = Instant.ofEpochMilli(1_000_000_050_000L);
        Uuidv7Generator generator = new Uuidv7Generator(new Random(1));

        UUID first = generator.generate(earlier);
        UUID second = generator.generate(later);

        assertThat(timestampBits(first)).isEqualTo(earlier.toEpochMilli());
        assertThat(timestampBits(second)).isEqualTo(later.toEpochMilli());
        // Time-sortable: the earlier instant produces the lexicographically-smaller identifier.
        assertThat(first.toString()).isLessThan(second.toString());
    }

    @Test
    void isDeterministicForAGivenRandomSourceAndInstant() {
        Instant now = Instant.ofEpochMilli(1_700_000_000_000L);

        UUID a = new Uuidv7Generator(new Random(42)).generate(now);
        UUID b = new Uuidv7Generator(new Random(42)).generate(now);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentRandomBitsProduceDifferentIdsAtTheSameInstant() {
        Instant now = Instant.ofEpochMilli(1_700_000_000_000L);

        UUID a = new Uuidv7Generator(new Random(1)).generate(now);
        UUID b = new Uuidv7Generator(new Random(2)).generate(now);

        assertThat(a).isNotEqualTo(b);
        assertThat(timestampBits(a)).isEqualTo(timestampBits(b));
    }

    private long timestampBits(UUID id) {
        return id.getMostSignificantBits() >>> 16;
    }
}
