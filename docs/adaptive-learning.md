# Local adaptive learning

Navigation now records physical IMU and GNSS observations internally. The dashboard no longer has the manual recording/promotion card. Collection starts with a live route, stops and saves when navigation ends, and can be paused in Sensors or Settings. There is no automatic data upload.

The device saves the original DRLOG format plus quality and provenance sidecars. Existing uploads remain compatible. The diagnostic export includes those sidecars for desktop training. Each label must come from a distinct, fresh, sufficiently accurate GNSS fix, with a complete recent IMU window and a fresh gyroscope. Simulation, legacy logs without quality metadata, timestamp gaps, and duplicate sessions are rejected. Whole journeys are assigned to train/validation/test splits.

Run from the original project directory using the existing ML environment:

```powershell
.\.venv-ml\Scripts\python.exe ml\adapt_from_phone.py --serial RZCX80NLSNN
```

This reads logs over authorized USB, retains the phone's originals, prepares the dataset, and trains/compares a speed candidate only when at least five independent sessions and sufficient quality data exist in all splits. It writes reports under `artifacts/phone-learning`. The candidate uses the existing 1×300×6 tensor contract. It compares held-out error against the bundled model and checks speed-band/tail regressions. The candidate and coefficients are saved separately; it never replaces the app's current weights or changes the model manifest.

A successful statistical comparison still requires controlled, independent-reference driving/outage testing before deployment. GNSS labels are a training proxy, not surveyed truth. No new personal model was trained during this update. USB ingestion of the current phone data rejected two short verification recordings and a legacy recording lacking quality metadata; zero windows were eligible. The report is saved at `artifacts/refinement/phone-learning-verified/phone-learning-report.json`. The existing IO-VNBD models remain intact.

Native online speed calibration was tightened to use distinct, recent GNSS fixes with reported speed accuracy of at most 1 m/s. It still requires changing speeds and demonstrated prediction agreement before bounded AI corrections can gain trust. The source's existing offline drift benchmark remains unmet; see the refinement report.
