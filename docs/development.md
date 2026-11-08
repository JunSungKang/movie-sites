# 개발 안내

JDK 25와 Node.js(정적 코드 검사), FFmpeg/ffprobe(런타임 검증)를 사용한다. Node.js는 서비스 운영에 필요하지 않다.

```bash
./mvnw verify
bash scripts/check.sh
```

Maven Wrapper는 프로젝트의 `.cache/`에 도구·의존성·임시 파일을 보관한다. `movie/`는 `.gitkeep`만 추적한다. `runtime/`, `.cache/`, `.codex-work/`, `target/`, `.env`는 추적하지 않는다. 빌드는 미디어 루트를 리소스로 읽지 않으며 패키지 검사에서 영상 파일 포함 여부를 확인한다.

| 경로 | 역할 |
|---|---|
| `src/main/java/io/moviesites/` | 탐색·스트리밍·변환·설정 API |
| `src/main/resources/static/js/` | 공통 탐색·캐시·재생 동작 |
| `src/main/resources/static/css/app.css` | 접근성·준비 화면 등 공통 기능 스타일 |
| `src/main/resources/static/themes/` | 테마 카탈로그·문구·CSS·SVG·미리보기 |
| `src/test/` | 서버 동작·경계·자원 제한 회귀 검사 |
| `scripts/` | 프런트엔드·패키지·런타임 검사 |
| `docs/` | 현재 설계·명세·검증·기여 안내 |

`scripts/validate-themes.mjs`는 테마 19개, 그룹별 개수, 허용 목록, 매니페스트, 참조 자원과 안전하지 않은 미리보기 값을 검사한다. `scripts/check.sh`는 이 검사와 JavaScript 문법·캐시 테스트, 콘텐츠 해시, Git/JAR 데이터 분리를 함께 확인한다. UI 변경 후에는 모바일·데스크톱에서 실제 레이아웃, 긴 제목, 이동 버튼과 영상 재생도 확인한다. 자동 검사는 시각 품질을 인증하지 않는다.

실행 중인 인스턴스 점검:

```bash
python3 -B scripts/verify-runtime.py http://127.0.0.1:8080
```

여러 검증 인스턴스는 서로 다른 데이터 루트와 포트를 사용해야 한다. 테마 설정은 프로세스 시작 시 읽는다. JAR를 다시 빌드할 필요 없이 재시작으로 등록된 테마를 바꿀 수 있으며 새 테마 파일을 추가했다면 재빌드가 필요하다.

기본 플랫폼·테마 명세는 [spec-platform.json](spec-platform.json)에 있다. 추가된 영상별 썸네일과 단일 재생 대기열의 동작은 [architecture.md](architecture.md)에 기록한다. 과거 특정 화면의 설계·명세·운영 사례는 Git 이력에 보존하고 현재 문서는 범용 플랫폼의 동작으로 통합한다. 검사 결과에는 실행 조건과 미검증 범위를 함께 기록한다.

영상별 썸네일은 외부 미디어 루트에 `clip.mp4.jpg`처럼 전체 파일명 뒤에 `.jpg`를 붙여 지정한다. 640×360 JPEG를 권장하며 원본 비율이 다르면 여백을 두어 주요 내용을 보존한다. 서버는 이미지가 없거나 유효하지 않으면 자동 생성 썸네일로 대체한다. 원본 영상의 재생 키는 이미지 교체에 영향받지 않는다.

단일 재생 대기열의 저장/순서/취소는 `js/playback.js`, 화면과 비디오 이벤트 연결은 `js/app.js`에서 관리한다. `scripts/playback.test.mjs`는 저장·순서·재생 스냅샷을, `scripts/player-ui.test.mjs`는 실제 앱 코드에 DOM/미디어 어댑터를 붙여 연속 종료·팝업 닫기·늦은 응답 취소·자동 재생 거절·없는 파일 복구를 검사한다. 어댑터 검사는 브라우저 디코더 검증을 대체하지 않는다.
