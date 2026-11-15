import {sanitizeQueue} from '../shared/api/index.js?v=d58f5843b24a8ea7';
export class PlaybackSession {
  constructor(select, finish) { this.select = select; this.finish = finish; this.serial = 0; this.items = []; this.index = -1; }
  get active() { return this.index >= 0; }
  get token() { return this.serial; }
  get hasPrevious() { return this.active && this.index > 0; }
  get hasNext() { return this.active && this.index < this.items.length - 1; }
  current(token) { return this.active && token === this.serial; }
  start(entries) {
    this.close(); this.items = sanitizeQueue(entries);
    if (!this.items.length) return false;
    this.index = 0; this.show(); return true;
  }
  show() { this.serial++; this.select(this.items[this.index], this.serial); }
  previous() { if (this.hasPrevious) { this.index--; this.show(); } }
  next() { if (this.hasNext) { this.index++; this.show(); } }
  ended(token) {
    if (!this.current(token)) return;
    if (this.hasNext) this.next();
    else { this.close(); this.finish(); }
  }
  close() { this.serial++; this.items = []; this.index = -1; }
}


export function transitionPlayer(state,event) {
  const transitions={closed:{OPEN:'preparing'},preparing:{OPEN:'preparing',READY:'ready',FAIL:'error',CLOSE:'closed'},ready:{OPEN:'preparing',PLAY:'playing',PAUSE:'paused',FAIL:'error',CLOSE:'closed'},playing:{OPEN:'preparing',PAUSE:'paused',FAIL:'error',CLOSE:'closed'},paused:{OPEN:'preparing',PLAY:'playing',FAIL:'error',CLOSE:'closed'},error:{OPEN:'preparing',RETRY:'preparing',CLOSE:'closed'}};
  const next=transitions[state]?.[event];if(!next)throw new Error('Invalid player transition: '+state+' / '+event);return next;
}
