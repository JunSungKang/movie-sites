// Theme data is packaged with the application. Preview overrides never alter server settings.
export function selectTheme(catalog, configuredId, requestedId) {
  if (catalog.version !== 1 || !Array.isArray(catalog.themes)) throw new Error('테마 목록을 확인할 수 없습니다.');
  const registered = new Map();
  for (const item of catalog.themes) {
    if (typeof item.id !== 'string' || typeof item.directory !== 'string'
      || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(item.id)
      || !/^(?:[1-6]0s\/)?[a-z0-9]+(?:-[a-z0-9]+)*$/.test(item.directory)
      || registered.has(item.id)) throw new Error('테마 목록이 올바르지 않습니다.');
    registered.set(item.id, item);
  }
  if (!registered.has(configuredId)) throw new Error('기본 테마를 찾을 수 없습니다.');
  return {theme: registered.get(requestedId) || registered.get(configuredId), preview: registered.has(requestedId), invalidPreview: Boolean(requestedId) && !registered.has(requestedId)};
}

const copyIds = {headerLabel:'header-label', videosHeading:'videos-heading', footer:'footer-label', emptyTitle:'empty-title', emptyDescription:'empty-description', loadingText:'loading-text'};
const requiredCopy = ['rootTitle','rootDescription','nestedDescription','rootCaption','rootFolders','nestedFolders','folderTab','openLabel','collectionSuffix','rootCollection',...Object.keys(copyIds)];
export function validateManifest(manifest, id) {
  if (manifest.id !== id || manifest.hero !== 'assets/hero.svg' || !/^#[a-f0-9]{6}$/i.test(manifest.color)
    || !manifest.copy || requiredCopy.some(key => typeof manifest.copy[key] !== 'string' || !manifest.copy[key].trim())) throw new Error('테마 정보를 확인할 수 없습니다.');
  return manifest;
}

export async function applyTheme(config) {
  const read = async url => {const response = await fetch(url, {cache:'no-cache'});if (!response.ok) throw new Error('테마를 불러오지 못했습니다.');return response.json();};
  const catalog = await read('/themes/catalog.json');
  const selected = selectTheme(catalog, config.theme.id, new URL(location.href).searchParams.get('preview-theme'));
  const base = '/themes/' + selected.theme.directory + '/';
  const manifest = validateManifest(await read(base + 'theme.json'), selected.theme.id);
  document.documentElement.dataset.theme = selected.theme.id;
  document.querySelector('meta[name="theme-color"]').content = manifest.color;
  for (const [key,id] of Object.entries(copyIds)) document.getElementById(id).textContent = manifest.copy[key];
  const art = document.getElementById('heading-art');
  art.src = base + manifest.hero;
  art.addEventListener('error', () => {art.hidden = true;}, {once:true});
  await new Promise((resolve,reject) => {
    const link = document.createElement('link');link.rel = 'stylesheet';link.href = base + 'theme.css';link.id = 'active-theme';
    const timer = setTimeout(() => {link.remove();reject(new Error('테마 응답이 지연되고 있습니다.'));}, 15000);
    link.onload = () => {clearTimeout(timer);resolve();};
    link.onerror = () => {clearTimeout(timer);link.remove();reject(new Error('테마 스타일을 불러오지 못했습니다.'));};
    document.head.append(link);
  });
  if (selected.preview || selected.invalidPreview) {
    const banner = document.getElementById('theme-preview');banner.hidden = false;
    document.getElementById('preview-label').textContent = selected.preview ? selected.theme.name + ' · 미리보기' : '등록되지 않은 테마입니다. 서비스 기본 테마로 표시합니다.';
    const original = new URL(location.href);original.searchParams.delete('preview-theme');
    document.getElementById('exit-preview').href = original.pathname + original.search;
    if (selected.preview) document.querySelector('.brand').href = '/?' + new URLSearchParams({'preview-theme':selected.theme.id});
  }
  document.body.dataset.ready = 'true';document.getElementById('boot-state').hidden = true;
  return manifest.copy;
}
