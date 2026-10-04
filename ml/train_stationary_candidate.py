"""Bounded IO-VNBD stationary proxy experiment; never promotes a deployed model."""
import hashlib
import json
import math
import warnings
from pathlib import Path

import numpy as np
import sklearn
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "artifacts/ml/windows-v2.npz"
OUT = ROOT / "artifacts/drift-fix/desktop-model"
FEATURES = ["accel_norm_mean", "accel_norm_std", "accel_norm_p95",
            "centered_accel_energy", "gyro_norm_mean", "gyro_norm_std", "gyro_norm_p95"]
MAX_FALSE_STOP = 0.001


def features(w):
    """Features invariant to one fixed orthonormal mounting rotation per window."""
    a = w[:, :, :3].astype(np.float64)
    g = w[:, :, 3:].astype(np.float64)
    an = np.linalg.norm(a, axis=2)
    gn = np.linalg.norm(g, axis=2)
    centered_energy = np.mean(np.sum((a - a.mean(axis=1, keepdims=True)) ** 2, axis=2), axis=1)
    return np.column_stack([an.mean(1), an.std(1), np.percentile(an, 95, axis=1),
                            centered_energy, gn.mean(1), gn.std(1), np.percentile(gn, 95, axis=1)])


def metrics(y, p, threshold, speed):
    predicted = p >= threshold
    tp = int(np.sum(predicted & y))
    fp = int(np.sum(predicted & ~y))
    nstop = int(y.sum())
    nmove = int((~y).sum())
    buckets = {}
    for low, high in [(2, 5), (5, 10), (10, 20), (20, 81)]:
        mask = (speed > low if low == 2 else speed >= low) & (speed < high)
        count = int(mask.sum())
        buckets[f"{low}_to_{high}_mps"] = {
            "moving_windows": count, "false_stops": int(predicted[mask].sum()),
            "false_stop_rate": float(predicted[mask].mean()) if count else None}
    return {"evaluated_windows": len(y), "stationary_windows": nstop, "moving_windows": nmove,
            "true_stops": tp, "false_stops": fp, "missed_stops": nstop - tp,
            "stationary_precision": tp / (tp + fp) if tp + fp else None,
            "stationary_recall": tp / nstop if nstop else None,
            "moving_false_stop_rate": fp / nmove if nmove else None,
            "moving_by_speed": buckets}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with np.load(CACHE) as d:
        w, speed, split = d["w"], d["speed"], d["split"]
    x = features(w)
    # Sanity-check mounting invariance without fitting extra candidates.
    rotated = w[:64].copy()
    rotation = np.array([[0., -1., 0.], [1., 0., 0.], [0., 0., 1.]])
    rotated[:, :, :3] = rotated[:, :, :3] @ rotation
    rotated[:, :, 3:] = rotated[:, :, 3:] @ rotation
    invariance_error = float(np.max(np.abs(features(rotated) - x[:64])))
    assert invariance_error < 1e-8, invariance_error
    eligible = np.isfinite(x).all(1) & np.isfinite(speed) & ((speed < .5) | (speed > 2))
    y = speed < .5
    train = eligible & (split == "train")
    valid = eligible & (split == "validation")
    assert train.any() and valid.any() and len(np.unique(y[train])) == 2
    scaler = StandardScaler().fit(x[train])
    model = LogisticRegression(class_weight="balanced", C=1.0, solver="lbfgs",
                               max_iter=200, random_state=2026)
    with warnings.catch_warnings(record=True) as captured:
        warnings.simplefilter("always", ConvergenceWarning)
        model.fit(scaler.transform(x[train]), y[train])
    convergence_warnings = [str(item.message) for item in captured if issubclass(item.category, ConvergenceWarning)]
    p = model.predict_proba(scaler.transform(x))[:, 1]
    moving_validation = np.sort(p[valid & ~y])[::-1]
    allowed = math.floor(MAX_FALSE_STOP * len(moving_validation))
    # Strictly exceed the next disallowed score; ties never exceed the FP budget.
    threshold = float(np.nextafter(moving_validation[allowed], np.inf))
    assert int(np.sum(moving_validation >= threshold)) <= allowed
    coefficient_artifact = {
        "candidate_only": True, "features": FEATURES, "positive_class": "speed_below_0.5_mps",
        "standardizer_fit": "Eligible train windows only", "mean": scaler.mean_.tolist(),
        "scale": scaler.scale_.tolist(), "coefficients": model.coef_[0].tolist(),
        "intercept": float(model.intercept_[0]), "stationary_probability_threshold": threshold,
        "prediction": "sigmoid(dot((features-mean)/scale, coefficients)+intercept) >= threshold"}
    (OUT / "coefficients.json").write_text(json.dumps(coefficient_artifact, indent=2) + "\n")
    report = {
        "candidate_only": True, "promoted": False, "seed": 2026, "sklearn_version": sklearn.__version__,
        "cache_sha256": hashlib.sha256(CACHE.read_bytes()).hexdigest(),
        "model": "Balanced logistic regression, C=1, seven rotation-invariant IMU features",
        "label": "CAN endpoint speed <0.5 m/s stationary; >2 m/s moving; intermediate windows excluded",
        "threshold_selection": "Smallest threshold satisfying empirical validation moving false-stop <=0.1%; ties excluded",
        "threshold": threshold, "allowed_validation_false_stops": allowed,
        "iterations": model.n_iter_.tolist(), "convergence_warnings": convergence_warnings,
        "rotation_invariance_max_abs_error": invariance_error,
        "training": {"windows": int(train.sum()), "stationary_windows": int(y[train].sum()),
                     "moving_windows": int((~y[train]).sum())},
        "evaluation": {},
        "limitations": [
            "Driving-data CAN endpoint speed is a weak stationary proxy; windows may straddle transitions.",
            "Validation chooses the threshold, so its false-stop rate is not an unbiased deployment estimate.",
            "Driver A is a reused regression set, not a fresh blind test.",
            "Overlapping windows are correlated; window metrics do not establish independent-event confidence.",
            "IMU alone cannot distinguish every constant-speed interval from a true stop.",
            "Fixed mounting rotation invariance does not cover changing orientation within a window.",
            "10 Hz vehicle recordings do not establish performance on this still phone, walking, cycling, or GPS blackouts.",
            "No deployed model changed; candidate must not directly trigger zero-velocity updates without further validation."]}
    for source, name in [("validation", "validation"), ("test", "reused_driver_a")]:
        mask = eligible & (split == source)
        report["evaluation"][name] = metrics(y[mask], p[mask], threshold, speed[mask])
        report["evaluation"][name]["excluded_windows"] = int(((split == source) & ~eligible).sum())
    # Verify the portable artifact exactly reproduces the trained model probabilities.
    z = (x[eligible] - np.array(coefficient_artifact["mean"])) / np.array(coefficient_artifact["scale"])
    logits = z @ np.array(coefficient_artifact["coefficients"]) + coefficient_artifact["intercept"]
    reconstructed = np.exp(-np.logaddexp(0, -logits))
    report["coefficient_roundtrip_max_abs_error"] = float(np.max(np.abs(reconstructed - p[eligible])))
    assert report["coefficient_roundtrip_max_abs_error"] < 1e-12
    (OUT / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    main()
