package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

class HistoryServiceTests {
    @Test void serializationFailureDoesNotBeginTransactionOrChangeStorage() {
        var repository = mock(HistoryRepository.class);
        var mapper = mock(JsonMapper.class);
        var manager = mock(PlatformTransactionManager.class);
        var service = new HistoryService(repository, mapper, manager);
        when(mapper.writeValueAsString(any())).thenThrow(new IllegalArgumentException("serialization failed"));
        assertThrows(IllegalArgumentException.class, () ->
                service.save("scenario", HistoryIntegrationTests.content("topic"), Instant.now()));
        verifyNoInteractions(repository, manager);
    }

    @Test void insertFailureRollsBackAndDoesNotTrim() {
        var repository = mock(HistoryRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("insert failed")).when(repository).insert(any(), any(), any(), any(), any());
        var service = new HistoryService(repository, JsonMapper.builder().build(), manager);
        assertThrows(IllegalStateException.class, () ->
                service.save("scenario", HistoryIntegrationTests.content("topic"), Instant.now()));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verify(repository, never()).trim();
    }

    @Test void commitFailureIsPropagatedInsteadOfReportingSaveSuccess() {
        var repository = mock(HistoryRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new TransactionSystemException("commit failed")).when(manager).commit(status);
        var service = new HistoryService(repository, JsonMapper.builder().build(), manager);
        assertThrows(TransactionSystemException.class, () ->
                service.save("scenario", HistoryIntegrationTests.content("topic"), Instant.now()));
        var order = inOrder(repository, manager);
        order.verify(manager).getTransaction(any());
        order.verify(repository).insert(any(), any(), any(), any(), any());
        order.verify(repository).trim();
        order.verify(manager).commit(status);
    }
}
