package io.chronos.api.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkerServiceTest {
    @Test
    void listsRepositoryWorkers() {
        WorkerRepository repository = mock(WorkerRepository.class);
        List<WorkerResponse> expected = List.of();
        when(repository.findAll()).thenReturn(expected);
        WorkerService service = new WorkerService(repository);

        assertThat(service.list()).isSameAs(expected);
        verify(repository).findAll();
    }
}
