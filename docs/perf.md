# Performance notes

The queue index begins with `(status, run_at)` to support due-run scans. Workers use bounded claim batches, virtual threads, and lease heartbeats. Measure throughput and tail latency with representative payloads before changing batch sizes or worker counts. The `load/` directory is the home for repeatable load profiles.
