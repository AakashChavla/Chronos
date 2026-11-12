package io.chronos.api.stream;

import io.chronos.api.run.RunService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public final class StreamController {
    private final RunService runs;

    public StreamController(RunService runs) {
        this.runs = runs;
    }

    @GetMapping(value = "/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runs() {
        SseEmitter emitter = new SseEmitter(0L);
        var updates = Executors.newSingleThreadScheduledExecutor();
        emitter.onCompletion(updates::shutdown);
        emitter.onTimeout(updates::shutdown);
        emitter.onError(error -> updates.shutdown());
        updates.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("runs").data(runs.findRecent(null)));
                    } catch (Exception exception) {
                        emitter.completeWithError(exception);
                        updates.shutdown();
                    }
                },
                0,
                2,
                TimeUnit.SECONDS);
        return emitter;
    }
}
