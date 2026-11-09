#!/usr/bin/env python3
"""Isolated Compose verification; Python 3.14. Never mounts production data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SERVICE = 'movie-kids-web'

def command(args, *, check=True, timeout=90, env=None):
    result = subprocess.run(args, text=True, capture_output=True, timeout=timeout, env=env)
    if check and result.returncode:
        raise RuntimeError(f'{args[0:3]} failed ({result.returncode}): {result.stderr[-3000:]} {result.stdout[-1000:]}')
    return result

def docker(*args, **kwargs):
    return command(['docker', *args], **kwargs)

def request(base, path, headers=None):
    try:
        response = urllib.request.urlopen(urllib.request.Request(base + path, headers=headers or {}), timeout=12)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, dict(response.headers), response.read()

def api(base, path):
    status, headers, body = request(base, path)
    if status != 200:
        raise RuntimeError(f'HTTP {status} for {path.split("?")[0]}: {body[:200]!r}')
    return json.loads(body), headers

def wait_health(base, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if api(base, '/api/health')[0]['status'] == 'UP':
                return
        except (OSError, RuntimeError):
            pass
        time.sleep(.3)
    raise RuntimeError('Application health timeout')

def listing(base, path='', refresh=True):
    params = {'sort': 'duration', 'path': path}
    if refresh:
        params['refresh'] = 'true'
    deadline = time.monotonic() + 150
    while True:
        page, _ = api(base, '/api/browse?' + urllib.parse.urlencode(params))
        if page['state'] == 'ready':
            return page
        if page['state'] == 'failed' or time.monotonic() > deadline:
            raise RuntimeError('List preparation failed or timed out')
        params = {'sort': 'duration', 'path': path, 'query': page['query']}
        time.sleep(.2)

def media_ready(base, path):
    deadline = time.monotonic() + 150
    while time.monotonic() < deadline:
        info, _ = api(base, '/api/media/info?' + urllib.parse.urlencode({'path': path, 'play': 'true'}))
        if info['state'] == 'failed':
            raise RuntimeError('Media preparation failed: ' + info['message'])
        if info.get('streamToken') and info['thumbnailState'] == 'ready':
            return info
        time.sleep(.2)
    raise RuntimeError('Media preparation timeout')

def verify_stream(base, path, info):
    query = {'path': path, 'v': info['version'], 'token': info['streamToken']}
    url = '/api/media/stream?' + urllib.parse.urlencode(query)
    status, headers, body = request(base, url, {'Range': 'bytes=0-31'})
    assert status == 206 and len(body) == 32 and headers['Cache-Control'] == 'no-store'
    query['token'] = 'forged'
    assert request(base, '/api/media/stream?' + urllib.parse.urlencode(query))[0] == 403
    thumb = '/api/media/thumbnail?' + urllib.parse.urlencode({'path': path, 'v': info['thumbnailVersion']})
    status, _, image = request(base, thumb)
    assert status == 200 and image[:2] == b'\xff\xd8'
    return {'range': 206, 'thumbnail': 200, 'forgedToken': 403, 'playback': info['playback']}

def inspect(container):
    return json.loads(docker('inspect', container).stdout)[0]

def ffmpeg_file_check(container, path):
    docker('exec', container, 'ffmpeg', '-nostdin', '-v', 'error', '-protocol_whitelist', 'file,pipe',
           '-i', '/media/' + path, '-t', '0.2', '-vf', 'scale=160:-2', '-c:v', 'libx264',
           '-threads', '1', '-an', '-f', 'null', '-', timeout=60)

def main():
    if sys.version_info[:2] != (3, 14):
        raise SystemExit('Use Python 3.14; no Python runtime is needed in the application image.')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True)
    parser.add_argument('--port', type=int, default=20010, choices=range(20010, 20015))
    parser.add_argument('--keep', action='store_true', help='Keep only this QA instance for browser inspection; see qa-state.json for cleanup.')
    args = parser.parse_args()
    image = json.loads(docker('image', 'inspect', args.image).stdout)[0]['Id']
    ops = ROOT / '.codex-work/platform-release'
    ops.mkdir(parents=True, exist_ok=True)
    work = ops / ('qa-' + uuid.uuid4().hex[:10])
    assert work.resolve().is_relative_to(ROOT) and not work.exists()
    work.mkdir()
    media, data = work / 'media', work / 'data'
    (media / 'Collection/Nested').mkdir(parents=True)
    data.mkdir()
    (data / 'preserve.txt').write_text('restart marker')
    (media / 'Collection/Nested/direct.mp4.json').write_text('{"tags":["Fixture topic"]}')
    (media / 'linked.mp4').symlink_to('/etc/passwd')
    env = {k: v for k, v in os.environ.items() if not k.startswith(('MOVIESITES_', 'JAVA_BUILD_IMAGE', 'JAVA_RUNTIME_IMAGE', 'COMPOSE_'))}
    project = 'movie-kids-qa-' + uuid.uuid4().hex[:8]
    appenv = work / 'application.env'
    appenv.write_text('MOVIESITES_THEME=child-toy\nMOVIESITES_MIN_FREE_BYTES=0\nMOVIESITES_PAGE_SIZE=2\nMOVIESITES_PUBLIC_URL=http://127.0.0.1:' + str(args.port) + '\n')
    composeenv = work / 'compose.env'
    values = {'MOVIESITES_IMAGE': image, 'MOVIESITES_UID': '501', 'MOVIESITES_GID': '20', 'MOVIESITES_BIND_ADDRESS': '127.0.0.1', 'MOVIESITES_WEB_PORT': str(args.port), 'MOVIESITES_MEDIA_PATH': str(media), 'MOVIESITES_DATA_PATH': str(data), 'MOVIESITES_ENV_FILE': str(appenv)}
    composeenv.write_text(''.join(k + '=' + v + '\n' for k, v in values.items()))
    prefix = ['docker', 'compose', '-p', project, '--env-file', str(composeenv), '-f', str(ROOT / 'compose.platform.yaml')]
    def compose(*items, **kwargs):
        return command(prefix + list(items), env=env, **kwargs)
    report = {'image': image, 'project': project, 'qaRoot': str(work), 'productionDataMounted': False,
              'composeSha256': hashlib.sha256((ROOT / 'compose.platform.yaml').read_bytes()).hexdigest()}
    state = {'prefix': prefix, 'project': project, 'work': str(work)}
    (work / 'qa-state.json').write_text(json.dumps(state, indent=2))
    container = None
    passed = False
    try:
        print('Preparing synthetic direct and converted media...', flush=True)
        fixture = '''ffmpeg -v error -f lavfi -i testsrc=size=160x90:rate=10 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 2 -c:v libx264 -pix_fmt yuv420p -threads 1 -c:a aac /fixtures/Collection/Nested/direct.mp4
ffmpeg -v error -f lavfi -i testsrc=size=160x90:rate=10 -f lavfi -i sine=frequency=330:sample_rate=44100 -t 2 -c:v mpeg4 -pix_fmt yuv420p -threads 1 -c:a pcm_s16le /fixtures/Collection/convert.avi'''
        docker('run', '--rm', '--read-only', '--network', 'none', '--user', '501:20', '--mount', f'type=bind,source={media},target=/fixtures', '--entrypoint', 'sh', image, '-ec', fixture)
        originals = {p.relative_to(media).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in media.rglob('*') if p.is_file() and not p.is_symlink()}
        model = json.loads(compose('config', '--format', 'json').stdout)
        assert list(model['services']) == [SERVICE]
        service = model['services'][SERVICE]
        assert service['user'] == '501:20' and service['read_only'] and service['init']
        assert len(service['ports']) == 1 and service['ports'][0]['target'] == 8080
        assert service['volumes'][0]['read_only'] and service['volumes'][0]['bind']['create_host_path'] is False
        compose('up', '-d', '--no-build', '--pull', 'never', '--no-deps', SERVICE)
        container = compose('ps', '-q', SERVICE).stdout.strip()
        state['container'] = container
        (work / 'qa-state.json').write_text(json.dumps(state, indent=2))
        base = f'http://127.0.0.1:{args.port}'
        wait_health(base)
        details = inspect(container)
        assert details['Config']['User'] == '501:20' and details['HostConfig']['ReadonlyRootfs']
        for mount in details['Mounts']:
            assert Path(mount['Source']).resolve().is_relative_to(work)
        assert {(m['Destination'], m['RW']) for m in details['Mounts']} == {('/media', False), ('/data', True)}
        runtime = docker('exec', container, 'sh', '-ec', '. /etc/os-release\ntest "$VERSION_ID" = 26.04\njava -version\nffmpeg -version\ntest -s /etc/ssl/certs/ca-certificates.crt\n! command -v python3\n! command -v node\n! command -v javac').stdout
        assert 'ffmpeg version' in runtime
        assert request(base, '/')[0] == 200 and request(base, '/about')[0] == 200
        assert listing(base)['entries'][0]['path'] == 'Collection'
        assert {x['name'] for x in listing(base, 'Collection')['entries']} == {'Nested', 'convert.avi'}
        assert listing(base, 'Collection/Nested')['parent'] == 'Collection'
        direct = media_ready(base, 'Collection/Nested/direct.mp4')
        converted = media_ready(base, 'Collection/convert.avi')
        assert direct['playback'] == 'original' and converted['playback'] == 'converted'
        report['direct'] = verify_stream(base, 'Collection/Nested/direct.mp4', direct)
        report['converted'] = verify_stream(base, 'Collection/convert.avi', converted)
        ffmpeg_file_check(container, 'Collection/Nested/direct.mp4')
        for invalid in ['../outside.mp4', '/etc/passwd', 'linked.mp4']:
            status, _, body = request(base, '/api/media/info?' + urllib.parse.urlencode({'path': invalid}))
            assert status in (400, 403, 404, 503) and b'/Users/' not in body
        docker('exec', container, 'sh', '-ec', 'test "$(id -u):$(id -g)" = 501:20\n! touch /media/forbidden-write\n! touch /app/forbidden-write\ntest -w /data')
        print('Checking exclusive writer and graceful restart...', flush=True)
        duplicate_name = project + '-second-writer'
        try:
            duplicate = compose('run', '--rm', '--name', duplicate_name, '--no-deps', SERVICE, check=False, timeout=25)
        finally:
            leftover = docker('inspect', duplicate_name, check=False)
            if leftover.returncode == 0:
                owned = json.loads(leftover.stdout)[0]
                assert owned['Config']['Labels']['com.docker.compose.project'] == project
                docker('stop', '--timeout', '20', owned['Id'], check=False)
                docker('rm', owned['Id'], check=False)
        assert duplicate.returncode != 0 and 'already in use' in duplicate.stdout + duplicate.stderr
        wait_health(base)
        compose('stop', SERVICE)
        stopped = inspect(container)
        assert stopped['State']['ExitCode'] in (0, 143) and not stopped['State']['OOMKilled']
        assert 'Graceful shutdown complete' in docker('logs', container).stdout
        compose('start', SERVICE)
        wait_health(base)
        assert (data / 'preserve.txt').read_text() == 'restart marker'
        verify_stream(base, 'Collection/Nested/direct.mp4', media_ready(base, 'Collection/Nested/direct.mp4'))
        current = {p.relative_to(media).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in media.rglob('*') if p.is_file() and not p.is_symlink()}
        assert originals == current
        report.update(status='pass', ubuntu='26.04', uidGid='501:20', webPort=args.port, browsing=True, traversalBlocked=True, mediaReadOnly=True, rootReadOnly=True, exclusiveWriter=True, gracefulShutdown=True, restart=True, mediaHashesUnchanged=True, container=container, base=base)
        passed = True
        print(json.dumps(report, indent=2), flush=True)
    finally:
        if container:
            (work / 'container.log').write_text(docker('logs', container, check=False).stdout)
        (work / 'verification.json').write_text(json.dumps(report, indent=2))
        if not (passed and args.keep):
            cleanup = compose('down', check=False)
            if cleanup.returncode:
                raise RuntimeError('QA Compose cleanup failed; fixture mounts retained for safe recovery')
            import shutil
            shutil.rmtree(media)
            shutil.rmtree(data)
    return work

if __name__ == '__main__':
    print('Evidence:', main())
