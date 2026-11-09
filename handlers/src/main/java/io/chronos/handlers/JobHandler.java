package io.chronos.handlers; import io.chronos.common.RunContext; public interface JobHandler { String type(); void handle(RunContext context) throws Exception; }
