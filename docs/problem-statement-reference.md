# Problem statement reference

Source: the user's problem statement supplied on 30 September 2026. This is a structured reference summary, not an independently verified transcription of the official challenge. Requirements are targets and must not be described as achieved without evidence.

Develop a lightweight Intelligent Dead Reckoning (IDR) mobile application and edge-deployable engine for vehicle navigation during unreliable or absent GNSS reception in tunnels, underpasses, urban canyons, dense foliage and multi-level parking. Relevant users include vehicle logistics, ride-hailing, quick commerce and emergency responders. The brief refers to GNSS constellations such as GPS, Galileo and NavIC. Loss of internet and loss of satellite positioning are separate conditions.

Use smartphone accelerometer, gyroscope, magnetometer/compass and available GNSS data, without a physical vehicle-computer, OBD-II or speedometer connection. Support external IMU data through the standalone engine as well.

Required capabilities:

1. In-vehicle alignment and calibration of phone pitch, roll and yaw relative to travel, for dashboard and holder mounts.
2. AI/ML and signal processing for speed prediction, IMU noise/bias correction and handling engine vibration, potholes, bumps and phone misalignment.
3. Offline map matching, for example using OpenStreetMap, with road geometry and non-holonomic vehicle constraints. UKF plus Hidden Markov map matching is an example in the brief, not a mandatory filter choice.
4. AI-assisted GNSS/INS fusion to reduce position and velocity drift.
5. Automatic transition between GNSS-aided INS and inertial dead reckoning, including recovery. The brief requests transitions within milliseconds and expresses a lane-level accuracy ambition.
6. Real-time mobile navigation with continuous position display.
7. Reusable edge algorithms for external IMUs, including higher-rate FOG-based sensor data.

Use IO-VNBD for preliminary model training and testing. Screening submissions require the trained preliminary models and position plots inferred from an IO-VNBD subset. Additional datasets may be supplied later. Training takes place on a desktop or in the cloud before deployment; lightweight inference takes place on the smartphone during the finale. Downloaded map databases may be brought to the finale.

Performance targets:

- Dead-reckoning positional drift below 10% of travelled distance during GNSS outage.
- Examples: less than 5 m drift over 50 m within about a minute, or less than 100 m over 1 km at 60 km/h.
- Smartphone position update rate of 10 Hz.
- Edge update rates around 200 Hz with suitable external IMU/FOG data.

The user describes six team work areas: ESKF filter design, calibration engine, AI model and speed prediction, direction-selection agent, mobile app development and map matcher. Report requirements: connected professional prose, corrected grammar, natural voice, accessible explanations for general readers plus accurate technical detail for specialists. Include omitted relevant capabilities where supported by the project. Distinguish implementation, recorded validation and remaining requirements.

The project description is saved separately in solution-description-report.md.

