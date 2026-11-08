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
catalog=get('/themes/catalog.json');assert config['theme']['id'] in {t['id'] for t in catalog['themes']}
listing=get('/api/browse?path=');assert len(listing['entries'])<=config['pageSize']
for path in ['../secret','/etc/passwd','folder/../../secret']:
    assert get('/api/browse?'+urllib.parse.urlencode({'path':path}),400)['code']=='INVALID_PATH'
assert get('/api/browse?path=missing-folder-that-does-not-exist',404)['code']=='NOT_FOUND'
for path in ['/index.html','/js/app.js','/js/cache.js','/js/playback.js','/css/app.css','/themes/runtime.js','/themes/preview.html','/themes/'+config['theme']['directory']+'/theme.css']:
    request=urllib.request.Request(base+path,headers={'If-Modified-Since':'Sun, 07 Nov 2027 00:00:00 GMT'})
    with urllib.request.urlopen(request,timeout=15) as response:
        assert response.status==200,(path,response.status)
        assert response.headers.get('Cache-Control')=='no-store'
        assert response.headers.get('Last-Modified') is None
        assert response.read()
print(json.dumps({'static_upgrade_revalidation':'pass','health':'pass','root_listing':'pass','path_boundaries':'pass','page_size':config['pageSize'],'cache_ttl_seconds':config['cacheTtlSeconds']}))
