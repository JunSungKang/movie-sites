import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const theme=readFileSync(new URL('../src/main/resources/static/themes/child-toy/theme.css',import.meta.url),'utf8');
const token=name=>{
 const match=theme.match(new RegExp('--'+name+':(#[a-f0-9]{6}|#[a-f0-9]{3})(?:;|})','i'));
 assert.ok(match,'Missing child theme token: '+name);return match[1];
};
function luminance(hex){
 if(hex.length===4)hex='#'+[...hex.slice(1)].map(value=>value+value).join('');
 const [r,g,b]=hex.slice(1).match(/../g).map(value=>parseInt(value,16)/255).map(value=>value<=.04045?value/12.92:((value+.055)/1.055)**2.4);
 return .2126*r+.7152*g+.0722*b;
}
function contrast(foreground,background){
 const [light,dark]=[luminance(foreground),luminance(background)].sort((a,b)=>b-a);
 return (light+.05)/(dark+.05);
}

test('small secondary copy stays readable on child theme surfaces',()=>{
 for(const background of [token('paper'),token('soft'),token('surface')])assert.ok(contrast(token('muted'),background)>=4.5,'Secondary copy needs at least 4.5:1 contrast on '+background);
});

test('the child theme footer retains readable contrast on its page background',()=>{
 const rule=theme.match(/\.page-footer\{([^}]+)\}/)?.[1];assert.ok(rule,'Missing child theme footer rule');
 const value=rule.match(/(?:^|;)color:([^;]+)/)?.[1];assert.ok(value,'Missing footer foreground');
 const foreground=value.startsWith('var(--')?token(value.slice(6,-1)):value;
 assert.ok(contrast(foreground,token('paper'))>=4.5,'Footer copy needs at least 4.5:1 contrast');
});
