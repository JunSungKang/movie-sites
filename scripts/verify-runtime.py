#!/usr/bin/env python3
"""HTTP smoke checks against a running MovieSites instance; never changes media files."""
import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def request(base, endpoint, headers=None):
    try:
        response = urllib.request.urlopen(urllib.request.Request(base + endpoint, headers=headers or {}), timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read()


def get(base, endpoint, expected=200):
    status, _, body = request(base, endpoint)
    assert status == expected, (endpoint, status)
    return json.loads(body)


def ready_listing(base):
    params = {'path': '', 'sort': 'duration'}
    deadline = time.monotonic() + 150
    while True:
        page = get(base, '/api/browse?' + urllib.parse.urlencode(params))
        if page['state'] == 'ready':
            return page
        assert page['state'] == 'preparing', page['state']
        assert time.monotonic() < deadline, 'Directory preparation timed out'
        params['query'] = page['query']
        time.sleep(.2)


def verify_static(base, path, policy):
    # Reproducible archive timestamps must never turn an upgraded resource into a stale 304.
    status, headers, body = request(base, path, {'If-Modified-Since': 'Sun, 07 Nov 2027 00:00:00 GMT'})
    assert status == 200 and body, (path, status)
    assert headers.get('Cache-Control') == policy, (path, headers.get('Cache-Control'))
    assert headers.get('Last-Modified') is None, path
    if policy == 'no-store':
        return
    etag = headers.get('ETag')
    assert etag, path
    status, _, body = request(base, path, {'If-None-Match': etag})
    assert status == 304 and not body, (path, status)
    version = etag.removeprefix('W/').strip('"')[:16]
    status, headers, _ = request(base, path + '?v=' + version)
    expected = 'public, no-cache' if path in ('/service-worker.js', '/manifest.webmanifest') else 'public, max-age=31536000, immutable'
    assert status == 200 and headers.get('Cache-Control') == expected, (path, status, headers.get('Cache-Control'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base')
    base = parser.parse_args().base.rstrip('/')
    assert get(base, '/api/health')['status'] == 'UP'
    config = get(base, '/api/config')
    assert 60 <= config['cacheTtlSeconds'] <= 180
    catalog = get(base, '/themes/catalog.json')
    assert config['theme']['id'] in {theme['id'] for theme in catalog['themes']}
    listing = ready_listing(base)
    assert len(listing['entries']) <= config['pageSize']
    for path in ['../secret', '/etc/passwd', 'folder/../../secret']:
        assert get(base, '/api/browse?' + urllib.parse.urlencode({'path': path}), 400)['code'] == 'INVALID_PATH'
    missing = 'missing-' + uuid.uuid4().hex
    assert get(base, '/api/browse?' + urllib.parse.urlencode({'path': missing}), 404)['code'] == 'NOT_FOUND'
    for path in ['/', '/index.html', '/themes/preview.html', '/offline.html']:
        verify_static(base, path, 'no-store')
    for path in ['/js/app.js', '/js/catalog/index.js', '/js/player/index.js', '/js/queue/index.js',
                 '/js/random-playback/index.js', '/js/shared/i18n/index.js', '/js/pwa.js',
                 '/css/app.css', '/css/pwa.css', '/themes/runtime.js', '/themes/catalog.json',
                 '/themes/' + config['theme']['directory'] + '/theme.css', '/manifest.webmanifest', '/service-worker.js']:
        verify_static(base, path, 'public, no-cache')
    print(json.dumps({'static_upgrade_revalidation': 'pass', 'etag_304': 'pass', 'fingerprint_cache': 'pass',
                      'health': 'pass', 'root_listing': 'pass', 'path_boundaries': 'pass',
                      'page_size': config['pageSize'], 'cache_ttl_seconds': config['cacheTtlSeconds']}))


if __name__ == '__main__':
    main()
