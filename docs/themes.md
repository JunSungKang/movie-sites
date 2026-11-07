# 테마 안내

모든 테마는 동일한 영상 플랫폼 위에서 동작한다. 나이대는 탐색용 분류이며 사용 제한이나 자동 개인화를 의미하지 않는다. 현재 화면을 보존한 `child-toy`가 기본값이다.

## 선택과 미리보기

```bash
MOVIESITES_THEME=30s-calm-gallery java -jar target/moviesites.jar
```

Docker에서는 `-e MOVIESITES_THEME=30s-calm-gallery`를 지정한다. 변경 후 프로세스 또는 컨테이너를 다시 시작한다. 테마 ID는 대소문자를 구분하며, 등록되지 않은 값은 시작 오류가 된다.

서비스의 `/themes/preview.html`에서 전체 테마를 확인할 수 있다. 카드의 도식은 배치의 특징을 보여준다. 각 카드의 실제 화면 링크는 `/?preview-theme=<ID>`를 사용한다. 명시적인 미리보기 안내가 표시되며 서비스 기본 화면으로 돌아갈 수 있다. 미리보기는 서버 설정·다른 방문자의 화면을 바꾸지 않는다.

## 프리셋

| 분류 | ID | 디자인 |
|---|---|---|
| 어린이 | `child-toy` | 토이 플레이그라운드 — 기존의 장난감 폴더 데크 · 둥근 카드와 파스텔 친구들 |
| 10대 | `10s-neon-lab` | 네온 랩 — 네온 사이드 레일 · 비스듬한 패널과 유리 카드 |
| 10대 | `10s-pop-sticker` | 팝 스티커 — 두꺼운 테두리 · 기울어진 스티커와 큼직한 타일 |
| 10대 | `10s-pixel-arcade` | 픽셀 아케이드 — 픽셀 프레임 · 정사각 아케이드 선반 |
| 20대 | `20s-midnight-stream` | 미드나이트 스트림 — 시네마틱 히어로 · 이미지 위에 겹치는 제목 |
| 20대 | `20s-soft-minimal` | 소프트 미니멀 — 가벼운 여백 · 가로로 이어지는 미디어 리스트 |
| 20대 | `20s-color-block` | 컬러 블록 — 비대칭 벤토 · 선명한 면과 커다란 숫자 |
| 30대 | `30s-studio-grid` | 스튜디오 그리드 — 정돈된 3열 · 작업실 같은 얇은 그리드 |
| 30대 | `30s-calm-gallery` | 캄 갤러리 — 세이지 그린 · 넓은 2열 사진 갤러리 |
| 30대 | `30s-home-cinema` | 홈 시네마 — 버건디 극장 · 와이드 스크린과 황동 라인 |
| 40대 | `40s-classic-library` | 클래식 라이브러리 — 책장형 내비게이션 · 잉크와 아이보리 |
| 40대 | `40s-forest-view` | 포레스트 뷰 — 숲빛 헤더 · 한 줄씩 펼쳐지는 컬렉션 |
| 40대 | `40s-slate-dashboard` | 슬레이트 대시보드 — 모듈형 패널 · 촘촘하고 명확한 4열 |
| 50대 | `50s-paper-light` | 페이퍼 라이트 — 큰 활자 · 종이 위의 편안한 2열 |
| 50대 | `50s-navy-theater` | 네이비 시어터 — 세로 포스터 · 네이비와 크림빛 극장 |
| 50대 | `50s-warm-lounge` | 웜 라운지 — 아치형 컬렉션 · 테라코타 가로 목록 |
| 60대 | `60s-clear-blue` | 클리어 블루 — 한눈에 보는 한 줄 목록 · 큰 글자와 버튼 |
| 60대 | `60s-warm-comfort` | 웜 컴포트 — 넉넉한 간격 · 부드러운 크림빛 2열 |
| 60대 | `60s-bold-contrast` | 볼드 콘트라스트 — 검정과 노랑 · 굵은 윤곽과 선명한 조작부 |

## 전용 구조

```text
src/main/resources/static/themes/
├── catalog.json             # 허용된 ID와 디렉터리
├── runtime.js               # 테마 로딩·문구·미리보기
├── preview.{html,css,js}     # 독립적인 테마 목록 페이지
├── _shared/base.css         # 공통 구조
├── child-toy/
│   ├── theme.json           # 문구·장식·메타데이터
│   ├── theme.css
│   └── assets/{hero,preview}.svg
├── 10s/{neon-lab,pop-sticker,pixel-arcade}/
├── 20s/{midnight-stream,soft-minimal,color-block}/
├── 30s/{studio-grid,calm-gallery,home-cinema}/
├── 40s/{classic-library,forest-view,slate-dashboard}/
├── 50s/{paper-light,navy-theater,warm-lounge}/
└── 60s/{clear-blue,warm-comfort,bold-contrast}/
```

각 프리셋 디렉터리는 동일하게 `theme.json`, `theme.css`, `assets/hero.svg`, `assets/preview.svg`를 가진다. 영상·캐시·운영 설정을 이 디렉터리에 보관하지 않는다. 공통 탐색·캐시·재생 로직은 테마마다 복제하지 않는다. 신규 테마 작성 절차는 [테마 소스 안내](../src/main/resources/static/themes/README.md)에 있다.

나이대가 높다는 이유로 기능을 제거하지 않는다. 큰 글자 테마에서도 같은 탐색과 재생 기능을 제공한다. 고대비 프리셋은 시각적 선택지이며 접근성 표준 인증을 뜻하지 않는다.
