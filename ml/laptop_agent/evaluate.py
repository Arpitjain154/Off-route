"""Fail-closed data and comparison gates. Measurements use exported TFLite bytes."""
import numpy as np
from phone_learning import metrics, promotion_blockers, training_blockers


def data_blockers(data, state, min_new_trips=3, min_new_fixes=600):
    reasons = training_blockers(data)
    new = ~np.isin(data['session'], state.get('trained_sessions', []))
    if len(set(data['session'][new])) < min_new_trips or int(new.sum()) < min_new_fixes:
        reasons.append(f'Need {min_new_trips} new usable trips and {min_new_fixes} new quality fixes')
    fresh_test = (data['split'] == 'test') & ~np.isin(data['session'], state.get('used_test_sessions', []))
    if fresh_test.sum() < 100:
        reasons.append('Need at least 100 fixes from new, never-evaluated test trips')
    for split in ('train', 'validation', 'test'):
        mask = data['split'] == split
        if split == 'test':
            mask = fresh_test
        if (data['speed'][mask] < .5).sum() < 30:
            reasons.append(f'Need at least 30 reliable stopped fixes in {split}')
    return reasons


def compare_speed(y, baseline, candidate):
    arrays = [np.asarray(a) for a in (y, baseline, candidate)]
    y, baseline, candidate = arrays
    if not len(y) or any(a.shape != y.shape or not np.isfinite(a).all() for a in arrays):
        return {'blockers': ['Missing or non-finite speed evaluation']}
    stop = y < .5
    report = {'baseline': metrics(y, baseline), 'candidate': metrics(y, candidate),
              'blockers': promotion_blockers(y, candidate, baseline)}
    if report['candidate']['mae'] > report['baseline']['mae']:
        report['blockers'].append('Mean speed error regression')
    if (candidate < 0).any() or (candidate > 80).any():
        report['blockers'].append('Candidate predicts speed outside the phone contract (0..80 m/s)')
    if stop.sum() < 30:
        report['blockers'].append('Insufficient stopped observations')
    else:
        for name, values in [('baseline', baseline), ('candidate', candidate)]:
            report[name]['stopped_false_movement_rate'] = float((values[stop] > .5).mean())
            report[name]['stopped_mean_speed_mps'] = float(np.abs(values[stop]).mean())
        for key in ('stopped_false_movement_rate', 'stopped_mean_speed_mps'):
            if report['candidate'][key] > report['baseline'][key] + 1e-6:
                report['blockers'].append('Regression while stopped: ' + key)
    return report


def interpreter(path):
    import tensorflow as tf
    runner = tf.lite.Interpreter(model_path=str(path), num_threads=1)
    runner.allocate_tensors()
    ins, outs = runner.get_input_details(), runner.get_output_details()
    if len(ins) != 1 or len(outs) != 1 or tuple(ins[0]['shape']) != (1, 300, 6) or tuple(outs[0]['shape']) != (1, 1) or ins[0]['dtype'] != np.float32 or outs[0]['dtype'] != np.float32:
        raise ValueError('Speed model must be float32 [1,300,6] -> [1,1]')
    return runner, ins[0]['index'], outs[0]['index']


def predictions(path, windows):
    runner, inp, out = interpreter(path)
    result = []
    for w in windows:
        runner.set_tensor(inp, np.asarray(w[None], dtype=np.float32))
        runner.invoke(); result.append(float(runner.get_tensor(out)[0, 0]))
    return np.asarray(result)


def compare_navigation(rows):
    report = {'blockers': [], 'outage_observations': 0}
    hidden = [r for r in rows if int(r['hidden']) == 1]
    report['outage_observations'] = len(hidden)
    if len(hidden) < 30:
        report['blockers'].append('Need at least 30 reliable GPS references inside simulated outages')
        return report
    for name in ('baseline', 'candidate'):
        errors = np.array([float(r[name + '_error']) for r in hidden])
        available = np.array([float(r[name + '_available']) for r in hidden])
        if not np.isfinite(errors).all() or not np.isfinite(available).all():
            report['blockers'].append('Non-finite navigation replay'); return report
        report[name] = {'drift_rmse_m': float(np.sqrt(np.mean(errors ** 2))),
                        'drift_p95_m': float(np.quantile(errors, .95)),
                        'drift_max_m': float(np.max(errors)), 'availability': float(available.mean())}
    for key in ('drift_rmse_m', 'drift_p95_m', 'drift_max_m'):
        if report['candidate'][key] > report['baseline'][key] + 1e-6:
            report['blockers'].append('Navigation regression: ' + key)
    if report['candidate']['availability'] < report['baseline']['availability'] or report['candidate']['availability'] < .9:
        report['blockers'].append('Navigation unavailable during too much of the outage')
    active = sum(float(r['candidate_ai']) > 0 for r in hidden)
    report['active_candidate_observations'] = active
    if active < 5:
        report['blockers'].append('Candidate corrections never meaningfully activated in outage replay')
    return report
