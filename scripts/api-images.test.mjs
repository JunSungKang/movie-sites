import test from 'node:test';
import assert from 'node:assert/strict';
import {assetModule} from './frontend-test-support.mjs';

const {createApiClient} = await assetModule('js/shared/api/index.js');
const api = createApiClient(() => { throw new Error('URL construction must not fetch'); });
const url = value => new URL(value, 'https://media.example.org');

test('all collection covers use one versioned optimized image URL contract', () => {
  const parameters = {path: '한글 모음/A & B', v: 'source+version&1'};
  const image = url(api.coverUrl(parameters));
  assert.equal(image.pathname, '/api/media/folder-cover');
  assert.equal(image.searchParams.get('path'), parameters.path);
  assert.equal(image.searchParams.get('v'), parameters.v);
  assert.deepEqual(image.searchParams.getAll('w'), ['640']);
  assert.deepEqual(parameters, {path: '한글 모음/A & B', v: 'source+version&1'});
  assert.deepEqual(url(api.coverUrl({...parameters, w: 320})).searchParams.getAll('w'), ['320']);
});

test('cover sizing does not change thumbnail or playback URLs', () => {
  for (const value of [api.thumbnailUrl({path: 'clip.mp4', v: 'selected'}), api.streamUrl({path: 'clip.mp4', token: 'a+b&c'})]) {
    assert.equal(url(value).searchParams.has('w'), false);
  }
  assert.equal(url(api.streamUrl({path: 'clip.mp4', token: 'a+b&c'})).searchParams.get('token'), 'a+b&c');
});
