import argparse
import getpass
import json
from pathlib import Path
import shutil
import sys
import time
from .common import HOME, atomic_bytes, locked, read_json, save_json, sha
from .cloud import Storage
from . import pipeline


def latest(home):
    pointer = read_json(home / 'latest.json')
    if not pointer:
        raise ValueError('No candidate yet. Choose Check and train; the status will explain missing data.')
    identifier = pointer['id']
    if Path(identifier).name != identifier or not identifier:
        raise ValueError('Invalid candidate identifier')
    return home / 'candidates' / identifier


def dispatch(args):
    home = args.home
    with locked(home):
        if args.command == 'login':
            Storage(home).login(); return pipeline.check(home)
        if args.command == 'check':
            return pipeline.check(home)
        if args.command == 'run':
            return pipeline.run(home, scheduled=args.scheduled)
        if args.command == 'status':
            return {'status': read_json(home / 'status.json', {'status':'Not run yet'}),
                    'downloads': read_json(home / 'download-report.json', {}),
                    'quality': read_json(home / 'quality-report.json', {}),
                    'report_folder': str(home), 'login_saved': (home / 'login.dpapi').exists()}
        root = latest(home)
        if args.command == 'phone-test':
            from .phone import phone_test
            return phone_test(root)
        if args.command in ('prepare-trial','install-trial','import-trial'):
            from .phone import prepare_trial, install_trial, import_trial
            return {'prepare-trial':prepare_trial, 'install-trial':install_trial, 'import-trial':import_trial}[args.command](root)
        if args.command == 'field-test':
            from .phone import field_evidence
            trip = args.trip or Path(read_json(root / 'field-trip.json', {}).get('path', ''))
            if not trip.is_file() or not args.video:
                raise ValueError('Choose the trip and video using the launcher file pickers.')
            return field_evidence(root, trip, args.video)
        if args.command == 'approve':
            from .release import approve, validate_evidence
            report = validate_evidence(root)
            print(json.dumps(report, indent=2))
            version = input('New release version (for example 1.2.0): ').strip()
            reviewer = input('Your name as reviewer: ').strip()
            if input('Type APPROVE to authorize this exact phone-tested candidate: ').strip() != 'APPROVE':
                raise ValueError('Approval cancelled')
            return approve(root, version, reviewer)
        if args.command == 'publish':
            from .release import release_item, publish_objects
            item = release_item(root)
            print(json.dumps(item, indent=2))
            if input('Type PUBLISH to release this approved model to all app users: ').strip() != 'PUBLISH':
                raise ValueError('Publication cancelled')
            key = getpass.getpass('Supabase secret/service-role key (hidden; never saved): ').strip()
            if not key:
                raise ValueError('Publisher key is required')
            blob = (root / 'speed-candidate.tflite').read_bytes()
            manifest = publish_objects(Storage(home, publisher_key=key), item, blob,
                                       expected_baseline=read_json(root / 'report.json')['cloud_baseline'])
            save_json(root / 'published.json', {'manifest':manifest, 'published_at':time.time()})
            # Do not mutate evaluated report; approval is bound to its checksum.
            (home / 'latest.json').unlink()
            save_json(home / 'status.json', {'status':'published', 'version':item['version']})
            return {'status':'published', 'version':item['version']}
        if args.command == 'discard':
            if input('Type DISCARD to stop reviewing this candidate (all files remain saved): ').strip() != 'DISCARD':
                raise ValueError('Cancelled')
            save_json(root / 'discarded.json', {'at':time.time()})
            (home / 'latest.json').unlink()
            return {'status':'discarded', 'candidate':root.name}


def main():
    parser = argparse.ArgumentParser(description='OFF-ROUTE laptop training agent')
    parser.add_argument('command', choices=['login','check','run','status','phone-test','prepare-trial','install-trial','import-trial','field-test','approve','publish','discard'])
    parser.add_argument('--home',type=Path,default=HOME)
    parser.add_argument('--scheduled',action='store_true')
    parser.add_argument('--trip',type=Path); parser.add_argument('--video',type=Path)
    args = parser.parse_args()
    try:
        result = dispatch(args)
        print(json.dumps(result,indent=2,allow_nan=False))
    except (Exception, KeyboardInterrupt) as error:
        # Never echo HTTP bodies, auth responses, passwords or tokens.
        print('Agent stopped: ' + (str(error) or 'Cancelled'), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
