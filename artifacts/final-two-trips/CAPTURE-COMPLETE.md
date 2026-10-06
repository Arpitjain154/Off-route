# Final two trips captured and backed up

Both phone recordings and both laptop captures are stopped. Location was ON and GPS was fresh at the end of Trip 2. Five journeys remain listed on the phone; originals were preserved.

| Capture | Trip 1 | Trip 2 |
|---|---:|---:|
| Duration | 6 min 19 sec | 11 min 40 sec |
| Location OFF/ON cycles | 3 | 7 |
| Raw sensor rows | 45,784 | 84,786 |
| Effective raw rate | 120.69 Hz | 121.05 Hz |
| Largest raw interval | 12.57 ms | 12.65 ms |
| Live telemetry rows | 834 | 1,362 |
| Capture error marker | None | None |

All ten per-session files were copied after recording ended and their SHA-256 hashes compared with the phone. Raw timestamps all have corresponding quality metadata. Laptop telemetry reports no capture errors. Location-state observations, app logs, sensor metadata and calibration/navigation traces are included in the verified archive `final-two-trips-capture.zip`. See `capture-index.json` and `backup-manifest.json` for identities and checksums.

This confirms data completeness for the implemented capture channels, not navigation accuracy. The installed app does not log every raw GNSS measurement or separate gyro/magnetometer event timestamps; those limitations are documented in PROTOCOL.md. No new model or calibration constants were installed during these trips. Trip 2 remains separate from Trip 1 and can serve as a held-out evaluation session if Trip 1 is used for development.

The phone may now be disconnected. Additional driving is not needed to begin offline analysis of the captured evidence; the recordings cannot by themselves establish true position throughout manual GPS-off periods.
