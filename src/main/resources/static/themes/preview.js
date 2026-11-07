const names = {child:'어린이 · 현재 테마', '10s':'10대 · 실험과 에너지', '20s':'20대 · 나만의 취향', '30s':'30대 · 편안한 균형', '40s':'40대 · 정돈된 공간', '50s':'50대 · 여유로운 감상', '60s':'60대 · 크고 또렷하게'};
const node = (tag, text, className) => {const el=document.createElement(tag);if(text)el.textContent=text;if(className)el.className=className;return el;};
try {
  const response=await fetch('/themes/catalog.json',{cache:'no-store'});if(!response.ok)throw new Error();
  const catalog=await response.json();document.getElementById('catalog').replaceChildren();
  for(const [group,label] of Object.entries(names)){
    const jump=node('a',group==='child'?'child-toy':group.replace('s','대'));jump.href='#'+group;document.getElementById('groups').append(jump);
    const section=node('section');section.id=group;section.append(node('h2',label));const cards=node('div',null,'cards');
    for(const theme of catalog.themes.filter(item=>item.group===group)){
      const article=node('article',null,'card');const link=node('a',null,'preview-link');link.href='/?'+new URLSearchParams({'preview-theme':theme.id});
      const image=node('img');image.src='/themes/'+theme.directory+'/assets/preview.svg';image.alt='';image.loading='lazy';image.width=900;image.height=600;
      const visual=node('div',null,'visual visual-'+theme.group);visual.append(image);link.append(visual,node('h3',theme.name),node('p',theme.description,'description'),node('span','실제 화면 열어보기 ↗','open'));
      article.append(link,node('code',theme.id));cards.append(article);
    }
    section.append(cards);document.getElementById('catalog').append(section);
  }
}catch{document.getElementById('catalog').replaceChildren(node('p','테마 목록을 불러오지 못했습니다. 새로고침해 주세요.'));}
