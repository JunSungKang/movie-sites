# 미궁 포털 화면 검증 (2026-11-07)

대상은 `spec-liminal.json`의 v5-liminal이며, 기존 v4 검증을 대체하지 않고 화면 변경과 연결 지점만 재검증한다.

## 자동 검사 (C01)

- JDK 25.0.3에서 `bash ./mvnw -B -ntp verify` 성공: 기존 JUnit 18개 통과.
- `bash scripts/check.sh` 성공: JavaScript 구문, 캐시·페이지 이력 Node 테스트 5개, CSS/JS 내용 해시, 영상 Git 제외 및 JAR 미포함 검사 통과.
- HTML ID 중복 없음, JavaScript에서 참조하는 모든 UI ID가 실제 HTML에 존재함을 확인했다.
- Java, API, 캐시 모듈, 의존성, Dockerfile과 기존 테스트를 변경하지 않았다.

## 화면과 상호작용 (C02)

- 실제 브라우저에서 320·375 CSS px 모바일과 1265 CSS px PC를 확인했다. 폴더 카드·긴 한글 이름·브레드크럼·영상 갤러리에 가로 넘침이 없다.
- PC 폴더 3열과 높이가 엇갈린 배치, 모바일 좌우 교차 카드, 문틀·별빛 배경을 시각 검토했다. 모바일 영상은 2열이다.
- 실제 루트의 폴더 4개, 중첩 경로의 하위 폴더, 샘플 영상 3개가 표시된다. 폴더를 열 때 위로 이동하며 브레드크럼과 상위 폴더 복귀가 동작한다.
- 조회했던 루트 재방문 시 캐시 안내가 보이고, 새로고침은 새 목록 조회 안내로 돌아온다.
- 썸네일 3개의 실제 이미지 로딩을 확인했다. 폴더 장식은 CSS이며 하위 표지 검색이나 이미지 요청이 없다.
- 없는 폴더는 빈 폴더로 표시하지 않고 이동·삭제 안내와 재시도 버튼을 표시한다. 상위 폴더로 복귀하면 목록이 다시 나타난다.
- 최종 이미지에서 샘플 MP4가 duration 8초, readyState 4로 로딩됐다. native 재생 버튼을 눌러 paused=false, currentTime>0, error=null을 확인했다. 닫으면 기존 재생 정리 흐름을 사용한다.
- 정상 탐색·재생 중 브라우저 JavaScript 오류가 없다. 오류 폴더 테스트의 예상 HTTP 404는 별도로 구분한다.

## 컨테이너와 서비스 (C03)

- Docker Desktop의 제공 BuildKit 플러그인으로 기존 Dockerfile 빌드 성공. 구형 빌더가 COPY --chmod를 지원하지 않은 첫 시도는 실패했으며 BuildKit으로 해결했다.
- 최종 이미지를 로컬 QA 포트에서 실행한 후 기존 공개 포트 `18081`에 적용했다. 공개 IP 주소의 health는 HTTP 200/UP이며 HTML/CSS/JS 응답 바이트가 현재 소스와 일치한다.
- 공개 IP 페이지에서도 실제 모바일 폴더 이동과 native 재생을 확인했다: duration 8초, readyState 4, paused=false, currentTime 0.163658초, error=null.
- 기존 원본 read-only mount와 생성 데이터 mount, 사용자 501:20, read-only rootfs, 2 GiB 메모리, 2 CPU, PID 128, unless-stopped 재시작 설정을 유지한다.
- 이전 컨테이너는 중지된 `moviesites-before-liminal`로 보존해 복구에 사용한다. 영상과 생성 데이터는 그대로 유지된다.
- 최종 화면: `runtime/design-preview/public/assets/liminal-service-mobile.jpg`, `liminal-service-desktop.jpg` (런타임 파일이며 Git에 포함하지 않음).

## 검증 한계

이번 화면 변경은 사용 가능한 내장 브라우저에서 검증했다. 실제 Edge 및 별도 Safari 앱, 실제 NAS 장애, 대량 동시 재생을 새로 시험하지 않았다. 공개 IP 접속은 이 호스트의 HTTP 클라이언트와 브라우저로 확인했으며 별도 외부망 측정은 반복하지 않았다. 서비스 가용성은 기존처럼 호스트·Docker·연결 저장소에 의존한다.
