package pk.nust.cuas.commons.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CorrelationIdTest {

    @AfterEach
    void clearMdc() {
        CorrelationId.clear();
    }

    @Test
    void currentIsEmptyWhenNothingBound() {
        assertThat(CorrelationId.current()).isEmpty();
    }

    @Test
    void setThenCurrentReturnsBoundValue() {
        CorrelationId.set("abc-123");
        assertThat(CorrelationId.current()).contains("abc-123");
    }

    @Test
    void clearRemovesBoundValue() {
        CorrelationId.set("abc-123");
        CorrelationId.clear();
        assertThat(CorrelationId.current()).isEmpty();
    }

    @Test
    void currentOrNewReturnsBoundValueWhenPresent() {
        CorrelationId.set("bound");
        assertThat(CorrelationId.currentOrNew()).isEqualTo("bound");
    }

    @Test
    void currentOrNewGeneratesWhenAbsent() {
        String generated = CorrelationId.currentOrNew();
        assertThat(generated).isNotBlank();
        // currentOrNew does not itself bind the generated value
        assertThat(CorrelationId.current()).isEmpty();
    }

    @Test
    void generateProducesDistinctNonBlankValues() {
        assertThat(CorrelationId.generate())
                .isNotBlank()
                .isNotEqualTo(CorrelationId.generate());
    }

    @Test
    void setRejectsNull() {
        assertThatThrownBy(() -> CorrelationId.set(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setRejectsBlank() {
        assertThatThrownBy(() -> CorrelationId.set("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
