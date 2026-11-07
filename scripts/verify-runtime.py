#!/usr/bin/env python3
"""Read-only smoke checks against a running MovieSites instance."""
import argparse,json,urllib.request,urllib.parse,urllib.error
p=argparse.ArgumentParser();p.add_argument('base');a=p.parse_args();base=a.base.rstrip('/')
def get(endpoint,expected=200):
    try:
        with urllib.request.urlopen(base+endpoint,timeout=15) as r:
            assert r.status==expected,(endpoint,r.status);return json.load(r)
    except urllib.error.HTTPError as e:
        assert e.code==expected,(endpoint,e.code);return json.load(e)
assert get('/api/health')['status']=='UP'
config=get('/api/config');assert 60<=config['cacheTtlSeconds']<=180
listing=get('/api/browse?path=');assert len(listing['entries'])<=config['pageSize']
for path in ['../secret','/etc/passwd','folder/../../secret']:
    assert get('/api/browse?'+urllib.parse.urlencode({'path':path}),400)['code']=='INVALID_PATH'
assert get('/api/browse?path=missing-folder-that-does-not-exist',404)['code']=='NOT_FOUND'
print(json.dumps({'health':'pass','root_listing':'pass','path_boundaries':'pass','page_size':config['pageSize'],'cache_ttl_seconds':config['cacheTtlSeconds']}))
