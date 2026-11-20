package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

class HistoryServiceTests {
    private HistoryTransaction transaction(
            HistoryRepository repository, PlatformTransactionManager manager) {
        var factory = new ProxyFactory(new HistoryTransaction(repository));
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (HistoryTransaction) factory.getProxy();
    }

    @Test
    void cacheCleanupFailureCannotReportCommittedHistoryAsUnsaved() {
        var repository = mock(HistoryRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        var evicted = java.util.UUID.randomUUID();
        when(repository.trim()).thenReturn(java.util.List.of(evicted));
        var service = new HistoryService(repository, JsonMapper.builder().build(), transaction(repository, manager));
        service.onEviction(
                ids -> {
                    verify(manager).commit(status);
                    assertEquals(java.util.List.of(evicted), ids);
                    throw new IllegalStateException("cache unavailable");
                });
        assertNotNull(
                service.save("scenario", HistoryIntegrationTests.content("topic"), Instant.now()));
    }

    @Test
    void commitFailureDoesNotNotifyEvictionListeners() {
        var repository = mock(HistoryRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.trim()).thenReturn(java.util.List.of(java.util.UUID.randomUUID()));
        doThrow(new TransactionSystemException("commit failed")).when(manager).commit(any());
        var service = new HistoryService(repository, JsonMapper.builder().build(), transaction(repository, manager));
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        service.onEviction(ids -> called.set(true));
        assertThrows(
                TransactionSystemException.class,
                () -> service.save(
                        "scenario",
                        HistoryIntegrationTests.content("topic"),
                        Instant.now()));
        assertFalse(called.get());
    }

    @Test
    void serializationFailureDoesNotBeginTransactionOrChangeStorage() {
        var repository = mock(HistoryRepository.class);
        var mapper = mock(JsonMapper.class);
        var manager = mock(PlatformTransactionManager.class);
        var service = new HistoryService(repository, mapper, transaction(repository, manager));
        when(mapper.writeValueAsString(any()))
                .thenThrow(new IllegalArgumentException("serialization failed"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(
                        "scenario",
                        HistoryIntegrationTests.content("topic"),
                        Instant.now()));
        verifyNoInteractions(repository, manager);
    }

    @Test
    void insertFailureRollsBackAndDoesNotTrim() {
        var repository = mock(HistoryRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("insert failed"))
                .when(repository)
                .insert(any(), any(), any(), any(), any());
        var service = new HistoryService(repository, JsonMapper.builder().build(), transaction(repository, manager));
        assertThrows(
                IllegalStateException.class,
                () -> service.save(
                        "scenario",
                        HistoryIntegrationTests.content("topic"),
                        Instant.now()));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verify(repository, never()).trim();
    }

    @Test
    void commitFailureIsPropagatedInsteadOfReportingSaveSuccess() {
        var repository = mock(HistoryRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new TransactionSystemException("commit failed")).when(manager).commit(status);
        var service = new HistoryService(repository, JsonMapper.builder().build(), transaction(repository, manager));
        assertThrows(
                TransactionSystemException.class,
                () -> service.save(
                        "scenario",
                        HistoryIntegrationTests.content("topic"),
                        Instant.now()));
        var order = inOrder(repository, manager);
        order.verify(manager).getTransaction(any());
        order.verify(repository).insert(any(), any(), any(), any(), any());
        order.verify(repository).trim();
        order.verify(manager).commit(status);
    }
}
