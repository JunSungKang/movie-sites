// Lexical import locations for the repository's native ES modules. No code is evaluated.
const identifierStart = character => character !== undefined && /[\p{ID_Start}_$]/u.test(character);
const identifierPart = character => character !== undefined && /[\p{ID_Continue}$\u200c\u200d]/u.test(character);

function decodedString(raw) {
  return raw.replace(/\\(?:\r\n|[\n\r\u2028\u2029]|u\{[\da-fA-F]+\}|u[\da-fA-F]{4}|x[\da-fA-F]{2}|.)/gs, escape => {
    const value = escape.slice(1);
    if (/^(?:\r\n|[\n\r\u2028\u2029])$/.test(value)) return '';
    if (value.startsWith('u{')) return String.fromCodePoint(parseInt(value.slice(2, -1), 16));
    if (value.startsWith('u') || value.startsWith('x')) return String.fromCodePoint(parseInt(value.slice(1), 16));
    return ({n: '\n', r: '\r', t: '\t', b: '\b', f: '\f', v: '\v', 0: '\0'})[value] ?? value;
  });
}

// Tokens retain identifiers and literal strings while excluding comments, regexp bodies
// and template text. Interpolation expressions are scanned like ordinary JavaScript.
export function sourceTokens(source) {
  const tokens = [];
  let position = 0;
  function code(interpolation = false) {
    let expression = true;
    const parentheses = [], braces = [];
    let previous;
    const push = (type, value, start, end = position) => {
      const token = {type, value, start, end}; tokens.push(token); previous = token; return token;
    };
    while (position < source.length) {
      const character = source[position], next = source[position + 1];
      if (/\s/.test(character)) { position++; continue; }
      if (character === '/' && next === '/') {
        position += 2; while (position < source.length && !/[\r\n\u2028\u2029]/.test(source[position])) position++;
        continue;
      }
      if (character === '/' && next === '*') {
        const end = source.indexOf('*/', position + 2);
        if (end < 0) throw new SyntaxError('Unterminated JavaScript comment');
        position = end + 2; continue;
      }
      const start = position;
      if (character === '"' || character === "'") {
        position++;
        while (position < source.length && source[position] !== character) {
          if (source[position] === '\\') position++;
          position++;
        }
        if (position >= source.length) throw new SyntaxError('Unterminated JavaScript string');
        position++;
        push('string', decodedString(source.slice(start + 1, position - 1)), start, position);
        expression = false; continue;
      }
      if (character === '`') {
        position++;
        while (position < source.length) {
          if (source[position] === '\\') { position += 2; continue; }
          if (source[position] === '`') { position++; break; }
          if (source[position] === '$' && source[position + 1] === '{') { position += 2; code(true); continue; }
          position++;
        }
        push('template', '', start); expression = false; continue;
      }
      if (character === '/' && expression) {
        position++; let inClass = false, closed = false;
        while (position < source.length) {
          const current = source[position++];
          if (current === '\\') { position++; continue; }
          if (current === '[') inClass = true;
          if (current === ']') inClass = false;
          if (current === '/' && !inClass) { closed = true; break; }
          if (/[\r\n\u2028\u2029]/.test(current)) break;
        }
        if (!closed) throw new SyntaxError('Unterminated JavaScript regexp');
        while (identifierPart(source[position])) position++;
        push('regexp', '', start); expression = false; continue;
      }
      if (identifierStart(character)) {
        position++; while (identifierPart(source[position])) position++;
        const property = ['.', '?.'].includes(previous?.value);
        const value = source.slice(start, position); push('identifier', value, start);
        expression = !property && /^(return|throw|case|delete|typeof|void|new|in|of|yield|await|instanceof|else|do)$/.test(value);
        continue;
      }
      if (/\d/.test(character)) {
        position++; while (position < source.length && /[\w.]/.test(source[position])) position++;
        push('number', source.slice(start, position), start); expression = false; continue;
      }
      if (character === '}' && interpolation && braces.length === 0) { position++; return; }
      const operator = ['===','!==','>>>','**=','=>','?.','++','--','==','!=','<=','>=','&&','||','??','+=','-=','*=','/=','**','<<','>>','...'].find(value => source.startsWith(value, position));
      const value = operator ?? character; position += value.length;
      if (value === '(') { parentheses.push(/^(if|while|for|with|switch|catch)$/.test(previous?.value ?? '')); expression = true; }
      else if (value === ')') expression = parentheses.pop() ?? false;
      else if (value === '{') { braces.push(!previous || /^(\)|;|\}|=>|else|try|finally|do)$/.test(previous.value)); expression = true; }
      else if (value === '}') expression = braces.pop() ?? true;
      else expression = ![']', '.', '?.', '++', '--'].includes(value);
      push('punctuation', value, start);
    }
    if (interpolation) throw new SyntaxError('Unterminated template interpolation');
  }
  code();
  return tokens;
}

export function moduleImports(source) {
  const tokens = sourceTokens(source), imports = [];
  const add = (token, kind) => imports.push({specifier: token.value, start: token.start + 1, end: token.end - 1, kind});
  for (let index = 0; index < tokens.length; index++) {
    const token = tokens[index], next = tokens[index + 1], previous = tokens[index - 1];
    if (token.type !== 'identifier' || ['.', '?.'].includes(previous?.value)) continue;
    if (token.value === 'import') {
      if (next?.value === '.') continue; // import.meta
      if (next?.value === '(') {
        const specifier = tokens[index + 2], ending = tokens[index + 3];
        if (specifier?.type !== 'string' || ![')', ','].includes(ending?.value)) throw new SyntaxError('Module imports must use a literal specifier');
        add(specifier, 'dynamic'); continue;
      }
      if (next?.type === 'string') { add(next, 'static'); continue; }
      for (let cursor = index + 1; cursor < tokens.length && tokens[cursor].value !== ';'; cursor++) {
        if (tokens[cursor].value === 'from' && tokens[cursor + 1]?.type === 'string') { add(tokens[cursor + 1], 'static'); break; }
      }
    }
    if (token.value === 'export' && ['{', '*'].includes(next?.value)) {
      for (let cursor = index + 1; cursor < tokens.length && tokens[cursor].value !== ';'; cursor++) {
        if (tokens[cursor].value === 'from' && tokens[cursor + 1]?.type === 'string') { add(tokens[cursor + 1], 'static'); break; }
        if (['export', 'import'].includes(tokens[cursor].value)) break;
      }
    }
  }
  return imports.sort((left, right) => left.start - right.start);
}
