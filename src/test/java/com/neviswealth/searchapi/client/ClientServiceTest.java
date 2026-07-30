package com.neviswealth.searchapi.client;

import com.neviswealth.searchapi.client.dto.CreateClientRequest;
import com.neviswealth.searchapi.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the duplicate-email handling in {@link ClientService}, focused on the
 * concurrent-insert race: the {@code existsByEmail} pre-check passes (no duplicate seen), but
 * a competing transaction commits the same email first, so the DB unique constraint fires on
 * {@code save}. The pre-check alone cannot cover this — the constraint is the real safeguard —
 * so the {@link DataIntegrityViolationException} catch path must translate it to a 409.
 */
class ClientServiceTest {

    private final ClientRepository repo = mock(ClientRepository.class);
    private final ClientService service = new ClientService(repo, null);

    private static CreateClientRequest request() {
        return new CreateClientRequest("Jane", "Doe", "jane.doe@example.com", null, List.of());
    }

    @Test
    void duplicateEmailFromRace_constraintViolationOnSave_becomesConflict() {
        // Pre-check sees no duplicate (the racing insert hasn't been observed yet)...
        when(repo.existsByEmail("jane.doe@example.com")).thenReturn(false);
        // ...but the DB unique constraint rejects the insert (the race lost).
        when(repo.save(any(Client.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jane.doe@example.com");
    }

    @Test
    void duplicateEmailSeenByPreCheck_becomesConflict() {
        // The ordinary (non-race) path: the pre-check catches the duplicate before any save.
        when(repo.existsByEmail("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jane.doe@example.com");
    }
}
