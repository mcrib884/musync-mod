*fix: MP3 decoding no longer allocates 4x file size up front, capped at 10MB initial buffer — prevents OOM on large MP3s
*fix: OGG files stream from disk instead of being fully read into memory (OggFileStream replaces OggStream(file.readBytes()))
*fix: listenerLatency() now checks public fields as fallback when reflection fails to find latency/ping method
*fix: config file paths resolve relative to game directory instead of JVM working directory (MuSyncConfig, dimension delays)
*fix: removed dead duplicate >=1.21.11 version branch in Compat.kt musicEventLocation
*fix: PausedSourceTracker evicts stale OpenAL sources every 30s to prevent source ID leaks
*fix: JukeboxBlockEntityMixin replaced fragile reflection (getSongPlayer) with direct getItem(0) check
*fix: vanilla music suppress call reduced from every tick to every 10 ticks to reduce CPU waste
*fix: KeyBindings fields changed from lateinit to nullable to prevent crash on dedicated server access
*fix: executor re-creation races fixed with synchronized double-check locking (loadExecutor, trackSendExecutor)
*fix: pollRandomQueuedTrack now synchronizes on userPlaylist to prevent lost queue entries during shuffle
*fix: resumeMusic() and dimResumeMusic() now include specificSound in the RESUME packet for cross-client sync
*fix: dimension_delays migration no longer deletes entire musync/ directory, only the old file
