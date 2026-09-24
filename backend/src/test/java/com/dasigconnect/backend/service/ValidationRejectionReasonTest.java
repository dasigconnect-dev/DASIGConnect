package com.dasigconnect.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BR-VAL-03: rejection is only for problems a revision can't fix. */
class ValidationRejectionReasonTest {

    @Test
    void acceptsEveryCurrentReason() {
        for (String code : ValidationService.REJECTION_REASON_LABELS.keySet()) {
            String notes = "OTHER".equals(code) ? "explained" : null;
            assertThatCode(() -> ValidationService.validateRejectionCode(code, notes))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsFixableReasonsThatBelongToRequestRevision() {
        assertThatThrownBy(() -> ValidationService.validateRejectionCode("INCOMPLETE_CONTENT", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Request Revision");
        assertThatThrownBy(() -> ValidationService.validateRejectionCode("WRONG_FORMAT", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void otherStillRequiresNotes() {
        assertThatThrownBy(() -> ValidationService.validateRejectionCode("OTHER", "  "))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void emailReasonUsesTheReadableLabel() {
        assertThat(ValidationService.readableRejectionReason("DUPLICATE_EVENT", " posted on 12 Sep "))
                .isEqualTo("Duplicate — posted on 12 Sep");
        assertThat(ValidationService.readableRejectionReason("OUT_OF_SCOPE", null))
                .isEqualTo("Not a DASIG activity");
    }
}
