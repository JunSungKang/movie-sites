import assert from 'node:assert/strict';
import test from 'node:test';
import {DirectoryCache} from '../src/main/resources/static/js/cache.js';
const page=name=>({entries:[{name}]});
test('TTL expires from fetch time rather than the most recent cache hit',()=>{
 let time=0;const c=new DirectoryCache({ttlMs:120000,clock:()=>time});c.put('root\0a\0',page('a'));time=110000;assert.ok(c.get('root\0a\0'));time=120001;assert.equal(c.get('root\0a\0'),null);
});
test('uses LRU eviction with a fixed response limit',()=>{
 const c=new DirectoryCache({maxEntries:2});c.put('a',page('a'));c.put('b',page('b'));c.get('a');c.put('c',page('c'));assert.equal(c.get('b'),null);assert.ok(c.get('a'));
});
test('bounds aggregate items and refuses oversized entries',()=>{
 const c=new DirectoryCache({maxItems:2,maxBytes:1000});c.put('a',page('a'));c.put('b',{entries:[{name:'b'},{name:'c'}]});assert.equal(c.get('a'),null);assert.equal(c.get('b').entries.length,2);c.put('big',{entries:[{name:'x'.repeat(2000)}]});assert.equal(c.get('big'),null);
});
test('separates roots paths and page cursors and invalidates only one path',()=>{
 const c=new DirectoryCache();c.put('r\0a\0',page('one'));c.put('r\0a\0cursor.1',page('two'));c.put('r\0ab\0',page('other'));c.invalidatePath('r\0a\0');assert.equal(c.get('r\0a\0'),null);assert.equal(c.get('r\0a\0cursor.1'),null);assert.ok(c.get('r\0ab\0'));
});
