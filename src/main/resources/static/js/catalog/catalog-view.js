import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {createDom, title, size, duration, date, folderName} from '../shared/ui/index.js?v=50e99f871f8b453e';
export function createCatalogView(document, browser) {
  const {$, node, icon, button} = createDom(document); const rows = new Map();
  let actions, observer, copy, labels = new Map();
  const label = name => labels.get(name) || name;
  const notice = (message, retry) => { $('notice').replaceChildren(node('div', '', message)); if (retry) $('notice').append(button(t('다시 불러오기'), actions.refresh)); $('notice').hidden = false; };
  function video(entry, path) {
    const row = node('article', 'video-entry'); row._entry = entry;
    const thumb = button('', () => actions.play(entry), 'thumbnail-button'); thumb.setAttribute('aria-label', title(entry.name) + t(' 재생'));
    const placeholder = node('span', 'thumbnail-placeholder'); placeholder.append(icon('film')); thumb.append(placeholder);
    const play = node('span', 'play-mark'); play.append(icon('play')); thumb.append(play); const time = node('span', 'duration'); time.hidden = true; thumb.append(time);
    const text = node('div', 'entry-copy'), category = node('div', 'entry-category'); category.append(icon('film'), node('span', '', path.split('/')[0] || copy.rootCollection));
    const heading = node('h4', 'entry-title'); heading.append(button(title(entry.name), () => actions.play(entry), 'title-button'));
    const topics = entry.topicLabels, tags = node('div', 'topic-tags'); tags.setAttribute('aria-label', topics.recommended ? t('파일명 기반 추천 주제') : t('영상 주제'));
    if (topics.recommended && topics.tags.length) tags.append(node('span', 'topic-source', t('추천 주제')));
    for (const text of topics.tags) tags.append(node('span', 'topic-tag', text));
    if (!topics.tags.length) tags.append(node('span', 'topic-empty', topics.empty));
    if (typeof entry.duration === 'number' && entry.duration > 0 && Number.isFinite(entry.duration)) { time.textContent = duration(entry.duration); time.hidden = false; }
    const meta = node('div', 'entry-meta'); meta.append(node('span', '', date(entry.modified)), node('span', '', size(entry.size))); text.append(category, heading, tags, meta);
    const add = button(t('대기열에 담기'), () => actions.enqueue(entry), 'queue-button enqueue-button'); add.dataset.path = entry.path; add.setAttribute('aria-label', title(entry.name) + t(' 대기열에 담기')); text.append(add); row.append(thumb, text);
    row._thumb = thumb; row._time = time; row._meta = meta; rows.set(entry.path, row); return row;
  }
  return {
    bind(next) { actions = next; $('home-button').addEventListener('click', next.home); $('parent-button').addEventListener('click', next.parent); $('refresh-button').addEventListener('click', next.refresh); $('first-button').addEventListener('click', next.first); $('previous-button').addEventListener('click', next.previous); $('next-button').addEventListener('click', next.next); },
    configure(config, themeCopy) { copy = themeCopy; labels = new Map(Object.entries(config.folderLabels || {})); },
    begin(path, preview) {
      observer?.disconnect(); rows.clear(); $('queue-add-page').hidden = true;
      $('notice').hidden = true; $('empty-state').hidden = true; $('folder-section').hidden = true; $('video-section').hidden = true; $('pagination').hidden = true; $('video-list').replaceChildren(); $('loading-state').hidden = false; $('video-list').setAttribute('aria-busy', 'true'); $('refresh-button').disabled = true;
      $('loading-text').textContent = copy.loadingText || t('영상 목록을 불러오고 있습니다.');
      const parts = path.split('/').filter(Boolean), parent = parts.slice(0, -1).join('/');
      if (!$('theme-preview').hidden) $('exit-preview').href = preview;
      const heading = path ? label(folderName(path)) : copy.rootTitle; $('folder-title').textContent = heading; document.title = `${heading} — MovieSites`;
      $('folder-description').textContent = path ? copy.nestedDescription : copy.rootDescription; $('folders-heading').textContent = path ? copy.nestedFolders : copy.rootFolders;
      $('location-caption').textContent = parts.length > 1 ? parts[0] + copy.collectionSuffix : copy.rootCaption; $('screen-navigation').hidden = !parts.length; $('parent-button').hidden = !parent;
      if (parent) { const destination = label(folderName(parent)); $('return-label').textContent = destination + t(' 돌아가기'); $('parent-button').setAttribute('aria-label', destination + t(' 화면으로 돌아가기')); }
    },
    preparing(data) { $('loading-text').textContent = t('짧은 영상부터 볼 수 있도록 길이를 확인하고 있어요.'); $('listing-status').textContent = t`${data.analyzed || 0}편 확인했어요. 처음에는 조금 걸릴 수 있어요.`; },
    render({data, groups, path, history, cached}) {
      const folders = data.entries.filter(entry => entry.kind === 'folder'); $('folder-list').replaceChildren();
      for (const [index, entry] of folders.entries()) {
        const text = label(entry.name), card = button('', () => actions.navigate(entry.path), 'folder-card'); card.setAttribute('aria-label', text + t(' 보기')); card.dataset.tone = ['mint', 'rose', 'sky', 'lilac'][index % 4];
        const tab = node('span', 'folder-tab', copy.folderTab); tab.setAttribute('aria-hidden', 'true'); const face = node('span', 'folder-face'), portal = node('span', 'folder-portal'); portal.setAttribute('aria-hidden', 'true');
        const fallback = node('span', 'cover-fallback'); fallback.append(icon('folder')); portal.append(fallback); const image = node('img'); image.alt = ''; image.loading = 'lazy'; image.decoding = 'async'; image.addEventListener('error', () => image.remove(), {once: true}); image.src = entry.coverUrl; portal.append(image);
        const textBox = node('span', 'folder-copy'), bottom = node('span', 'folder-bottom'); bottom.append(node('span', '', copy.openLabel), node('span', 'folder-arrow', '↗')); textBox.append(node('span', 'folder-name', text), bottom); face.append(portal, textBox); card.append(tab, face); $('folder-list').append(card);
      }
      $('folder-section').hidden = !folders.length; $('video-section').hidden = !groups.length; $('queue-add-page').hidden = !groups.length;
      for (const group of groups) { const heading = node('h3', 'duration-divider', group.label); heading.dataset.durationGroup = group.id; $('video-list').append(heading); for (const entry of group.entries) $('video-list').append(video(entry, path)); }
      observer = browser.observeVisible([...rows.values()], entry => actions.visible(entry));
      $('empty-state').hidden = data.entries.length > 0 || Boolean(data.nextCursor); $('first-button').hidden = history.offset === 0; $('previous-button').disabled = !history.canPrevious; $('next-button').disabled = !data.nextCursor; $('page-label').textContent = t`${history.pageNumber}번째 목록`; $('pagination').hidden = history.pageNumber === 1 && !data.nextCursor;
      if (data.warnings) notice(t('일부 항목이나 주제 정보를 읽지 못했습니다. 연결된 링크는 표시하지 않습니다. 잘못된 주제 파일은 주제 추천이 켜져 있을 때만 추천 태그로 대신합니다.'), false);
      if (!data.entries.length && data.nextCursor) notice(t('이 구간에는 표시할 영상이 없습니다. 다음 목록에서 계속 확인할 수 있습니다.'), false);
      $('listing-status').textContent = cached ? t('최근에 본 목록입니다. 새로고침하면 변경된 내용을 확인할 수 있습니다.') : t('짧은 영상부터 보여드려요. 길이별로 나누어 편하게 골라 보세요.');
    },
    failure(message) { notice(message, true); $('listing-status').textContent = t('목록을 확인할 수 없습니다.'); },
    finish() { $('loading-state').hidden = true; $('video-list').setAttribute('aria-busy', 'false'); $('refresh-button').disabled = false; delete document.body.dataset.catalogPending; },
    present: path => Boolean(rows.get(path)?.isConnected),
    enrich(path, info) { const row = rows.get(path); if (!row) return;
      if (info.duration !== null) { row._time.textContent = duration(info.duration); row._time.hidden = !row._time.textContent; }
      if (info.width && info.height && !row._resolution) { row._resolution = node('span', '', `${info.width} × ${info.height}`); row._meta.append(row._resolution); }
      if (info.thumbnailUrl && !row._image) { const image = node('img'); image.alt = ''; image.loading = 'lazy'; image.decoding = 'async'; image.addEventListener('load', () => row._thumb.querySelector('.thumbnail-placeholder')?.remove(), {once: true}); image.src = info.thumbnailUrl; row._thumb.prepend(image); row._image = image; }
    },
    dispose() { observer?.disconnect(); }
  };
}
