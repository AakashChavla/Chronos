package io.chronos.handlers;

import io.chronos.common.RunContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BuiltinHandlers {
  private static final Logger LOGGER = LoggerFactory.getLogger(BuiltinHandlers.class);

  public List<JobHandler> all() {
    return List.of(new Sleep(), new Flaky(), new Email(), new Http());
  }

  static class Sleep implements JobHandler {
    public String type() {
      return "sleep-and-log";
    }

    public void handle(RunContext c) throws Exception {
      Thread.sleep(100);
    }
  }

  static class Flaky implements JobHandler {
    public String type() {
      return "flaky";
    }

    public void handle(RunContext c) {
      if (Math.random() < .5) throw new IllegalStateException("configured flaky failure");
    }
  }

  static class Email implements JobHandler {
    public String type() {
      return "send-email-mock";
    }

    public void handle(RunContext c) {
      LOGGER.info("Mock email handler completed for run {} and job {}", c.runId(), c.jobId());
    }
  }

  static class Http implements JobHandler {
    public String type() {
      return "http-call";
    }

    public void handle(RunContext c) throws Exception {
      HttpClient.newHttpClient()
          .send(
              HttpRequest.newBuilder(URI.create("https://example.com"))
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.discarding());
    }
  }
}
