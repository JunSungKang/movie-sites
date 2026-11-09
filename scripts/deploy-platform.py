#!/usr/bin/env python3
"""Migrate the existing moviesites container under the shared macOS-compatible flock.

Requires a passing isolated verify-platform.py report for the exact candidate.
Only platform/movie-kids-web is created/stopped; other services are untouched.
"""
import argparse
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
loader = importlib.util.spec_from_file_location('platform_verification', ROOT / 'scripts/verify-platform.py')
verification = importlib.util.module_from_spec(loader)
loader.loader.exec_module(verification)
docker, inspect = verification.docker, verification.inspect


def manifest(directory):
    result = {}
    for file in sorted(directory.rglob('*')):
        key = file.relative_to(directory).as_posix()
        if file.is_symlink():
            result[key] = {'symlink': os.readlink(file)}
        elif file.is_file():
            stat = file.stat()
            with file.open('rb') as source:
                digest = hashlib.file_digest(source, 'sha256').hexdigest()
            result[key] = {'size': stat.st_size, 'mtimeNs': stat.st_mtime_ns, 'sha256': digest}
    return result


def wait_container(container):
    deadline = time.monotonic() + 100
    while time.monotonic() < deadline:
        current = inspect(container)
        if current['State'].get('Health', {}).get('Status') == 'healthy':
            return current
        if not current['State']['Running'] or current['State'].get('Health', {}).get('Status') == 'unhealthy':
            raise RuntimeError('Candidate failed container health')
        time.sleep(.5)
    raise RuntimeError('Candidate health timeout')


def real_media_check(base, container):
    page = verification.listing(base)
    visited = ['']
    while not any(item['kind'] == 'video' for item in page['entries']):
        folder = next((item for item in page['entries'] if item['kind'] == 'folder'), None)
        if folder is None or len(visited) >= 10:
            raise RuntimeError('No representative video found in inspected navigation path')
        visited.append(folder['path'])
        page = verification.listing(base, folder['path'])
    video = next(item for item in page['entries'] if item['kind'] == 'video')
    info = verification.media_ready(base, video['path'])
    stream = verification.verify_stream(base, video['path'], info)
    # Real media decode/encode without writing into the original media or data root.
    verification.ffmpeg_file_check(container, video['path'])
    return {'navigationDepth': len(visited), 'stream': stream, 'ffmpegRealMediaDecodeEncode': True}


def main():
    if sys.version_info[:2] != (3, 14):
        raise SystemExit('Use Python 3.14')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True)
    parser.add_argument('--qa-report', required=True, type=Path)
    parser.add_argument('--lock', type=Path, default=ROOT.parent / 'operations/run/deploy.lock')
    parser.add_argument('--port', type=int, default=20010, choices=range(20010, 20015))
    args = parser.parse_args()
    qa = json.loads(args.qa_report.read_text())
    image = json.loads(docker('image', 'inspect', args.image).stdout)[0]['Id']
    assert qa.get('status') == 'pass' and qa['image'] == image
    assert qa['composeSha256'] == hashlib.sha256((ROOT / 'compose.platform.yaml').read_bytes()).hexdigest()
    ops = ROOT / '.codex-work/platform-release'
    assert ops.resolve().is_relative_to(ROOT)
    ops.mkdir(parents=True, exist_ok=True)
    lock = args.lock.absolute()
    # The caller explicitly authorizes the named shared lock outside this repository.
    lock.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(lock, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'r+') as handle:
        deadline = time.monotonic() + 600
        while True:
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() > deadline:
                    raise RuntimeError('Shared deployment lock is busy; no container was changed')
                print('Waiting for the shared deployment lock...', flush=True)
                time.sleep(5)
        probe = '''import fcntl,sys
with open(sys.argv[1], 'r+') as lock:
 try: fcntl.flock(lock, fcntl.LOCK_EX|fcntl.LOCK_NB)
 except BlockingIOError: print('blocked');sys.exit(0)
 sys.exit(1)
'''
        proof = verification.command([sys.executable, '-B', '-c', probe, str(lock)])
        assert proof.stdout.strip() == 'blocked'
        old = inspect('moviesites')
        assert old['State']['Running'] and old['State']['Health']['Status'] == 'healthy'
        old_id = old['Id']
        assert old['Config']['User'] and old['Config']['User'].split(':')[0] != '0'
        mounts = {m['Destination']: m for m in old['Mounts']}
        assert set(mounts) == {'/media', '/data'} and not mounts['/media']['RW'] and mounts['/data']['RW']
        media, data = Path(mounts['/media']['Source']).resolve(), Path(mounts['/data']['Source']).resolve()
        assert media == (ROOT / 'movie').resolve() and data == (ROOT / 'runtime').resolve()
        hc = old['HostConfig']
        assert hc['Memory'] == 2147483648 and hc['NanoCpus'] == 2000000000 and hc['PidsLimit'] == 128
        assert hc['Init'] and hc['ReadonlyRootfs'] and hc['LogConfig']['Type'] == 'local'
        assert hc['RestartPolicy']['Name'] == 'unless-stopped'
        bindings = hc['PortBindings']['8080/tcp']
        assert len(bindings) == 1
        host = bindings[0]['HostIp']
        base_host = '127.0.0.1' if host in ('', '0.0.0.0') else host
        base = f'http://{base_host}:{args.port}'
        old_base = f'http://{base_host}:{bindings[0]["HostPort"]}'
        before = manifest(media)
        (ops / 'media-before.json').write_text(json.dumps(before, ensure_ascii=False, indent=2))
        # Full configuration is kept private, and the stopped original remains the rollback source.
        backup = ops / 'production-before.json'
        backup.write_text(json.dumps(old, indent=2))
        backup.chmod(0o600)
        application = ops / 'production.application.env'
        old_defaults = dict(value.split('=', 1) for value in json.loads(docker('image', 'inspect', old['Image']).stdout)[0]['Config']['Env'])
        # Preserve operator settings, but inherit new-image metadata such as JAVA_VERSION.
        entries = {key: value for key, value in (item.split('=', 1) for item in old['Config']['Env'])
                   if key.startswith('MOVIESITES_') or key in ('PORT', 'JAVA_TOOL_OPTIONS') or old_defaults.get(key) != value}
        entries['MOVIESITES_PUBLIC_URL'] = base
        assert all('\n' not in value and '\r' not in value for value in entries.values())
        application.write_text(''.join(key + '=' + value + '\n' for key, value in entries.items()))
        application.chmod(0o600)
        uid, gid = old['Config']['User'].split(':')
        composeenv = ops / 'production.compose.env'
        values = {'MOVIESITES_IMAGE': image, 'MOVIESITES_UID': uid, 'MOVIESITES_GID': gid,
                  'MOVIESITES_BIND_ADDRESS': host or '0.0.0.0', 'MOVIESITES_WEB_PORT': str(args.port),
                  'MOVIESITES_MEDIA_PATH': str(media), 'MOVIESITES_DATA_PATH': str(data), 'MOVIESITES_ENV_FILE': str(application)}
        composeenv.write_text(''.join(key + '=' + value + '\n' for key, value in values.items()))
        composeenv.chmod(0o600)
        env = {k: v for k, v in os.environ.items() if not k.startswith(('MOVIESITES_', 'JAVA_BUILD_IMAGE', 'JAVA_RUNTIME_IMAGE', 'COMPOSE_'))}
        prefix = ['docker', 'compose', '-p', 'platform', '--env-file', str(composeenv), '-f', str(ROOT / 'compose.platform.yaml')]
        def compose(*items):
            return verification.command(prefix + list(items), env=env)
        model = json.loads(compose('config', '--format', 'json').stdout)
        assert list(model['services']) == ['movie-kids-web']
        assert not compose('ps', '-aq', 'movie-kids-web').stdout.strip(), 'An existing Compose service must be reconciled before this first migration'
        rollback = 'moviesites-before-ubuntu26-' + time.strftime('%Y%m%d%H%M%S')
        assert docker('inspect', rollback, check=False).returncode != 0
        report = {'lock': str(lock), 'lockInode': os.fstat(handle.fileno()).st_ino, 'competingLockBlocked': True,
                  'previousContainer': old_id, 'previousImage': old['Image'], 'rollbackContainer': rollback,
                  'candidateImage': image, 'origin': base, 'composeProject': 'platform', 'service': 'movie-kids-web'}
        new_id = None
        stopped = False
        renamed = False
        try:
            print('Shared lock held; stopping the verified old container...', flush=True)
            docker('stop', '--time', '30', old_id)
            stopped = True
            old_state = inspect(old_id)['State']
            assert not old_state['Running'] and old_state['ExitCode'] in (0, 143) and not old_state['OOMKilled']
            docker('rename', old_id, rollback)
            renamed = True
            compose('up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'movie-kids-web')
            new_id = compose('ps', '-q', 'movie-kids-web').stdout.strip()
            assert new_id and new_id != old_id
            verification.wait_health(base)
            current = inspect(new_id)
            assert current['Image'] == image and current['Config']['User'] == old['Config']['User']
            current_mounts = {m['Destination']: m for m in current['Mounts']}
            assert set(current_mounts) == set(mounts)
            for destination, mount in current_mounts.items():
                assert mount['Type'] == 'bind' and mount['RW'] == mounts[destination]['RW']
                assert Path(mount['Source']).resolve() == Path(mounts[destination]['Source']).resolve()
            for key in ('Memory', 'NanoCpus', 'PidsLimit', 'Init', 'ReadonlyRootfs', 'LogConfig', 'RestartPolicy'):
                assert current['HostConfig'][key] == hc[key], key
            actual_env = dict(value.split('=', 1) for value in current['Config']['Env'])
            assert all(actual_env[key] == value for key, value in entries.items())
            report['productionChecks'] = real_media_check(base, new_id)
            print('Production checks passed; verifying a controlled restart...', flush=True)
            compose('stop', 'movie-kids-web')
            assert inspect(new_id)['State']['ExitCode'] in (0, 143)
            compose('start', 'movie-kids-web')
            verification.wait_health(base)
            report['afterRestartChecks'] = real_media_check(base, new_id)
            current = wait_container(new_id)
            assert manifest(media) == before
            assert not inspect(old_id)['State']['Running']
            report.update(status='pass', container=new_id, health=current['State']['Health']['Status'], mediaFilesUnchanged=len(before),
                          runtimeDataSource=str(data), mediaSource=str(media), uidGid=current['Config']['User'], persistentDataPreserved=True)
            (ops / 'deployment.json').write_text(json.dumps(report, indent=2))
            print(json.dumps(report, indent=2), flush=True)
        except BaseException as error:
            # Remove only this candidate. Never down a shared project or delete a volume.
            if new_id:
                docker('stop', '--time', '30', new_id, check=False)
                docker('rm', new_id)
            else:
                partial = compose('ps', '-aq', 'movie-kids-web').stdout.strip()
                if partial:
                    docker('stop', '--time', '30', partial, check=False)
                    docker('rm', partial)
            if renamed:
                docker('rename', old_id, 'moviesites')
            if stopped:
                docker('start', old_id)
                verification.wait_health(old_base)
            report.update(status='rolled-back', error=str(error), priorServiceRestored=stopped)
            (ops / 'deployment.json').write_text(json.dumps(report, indent=2))
            raise
        # Closing the handle releases the lock; never unlink its inode.

if __name__ == '__main__':
    main()
