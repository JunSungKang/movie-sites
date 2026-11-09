# 개발

[English](../development.md) · [문서 목록](../../README.ko.md#문서)

## 준비와 검사

전체 검사에는 JDK 25·Node.js 24·Python 3.14.9 이상을 사용합니다. 로컬 미디어 처리를 시험하려면 FFmpeg/ffprobe를 설치하거나 Docker 이미지를 사용하세요. 전역 Gradle·프론트엔드 개발 서버·데이터베이스는 필요하지 않습니다.

fork/clone 후 작업 브랜치에서 실행합니다.

```bash
./gradlew clean build
bash scripts/check.sh
python3 -B scripts/check-docs.py
```

실행 파일은 `build/libs/moviesites.jar`, Java 결과는 `build/test-results/test/`, HTML 보고서는 `build/reports/tests/test/`입니다. 서버만 검사하려면 `./gradlew test`, JavaScript는 `node --test scripts/cache.test.mjs scripts/playback.test.mjs scripts/player-ui.test.mjs scripts/library.test.mjs`를 사용합니다. 패키지 검사는 최신 JAR가 필요합니다. [기여 안내](../../CONTRIBUTING.md)를 참고하세요.

## 소스 구조

| 경로 | 역할 |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/` | Kotlin DSL·프로젝트 이름·검증된 Wrapper |
| `src/main/java/io/moviesites/` | 설정·HTTP·저장소·탐색·미디어 처리 |
| `src/main/resources/application.yml` | 환경변수 설정 |
| `src/main/resources/static/js/` | 탐색·캐시·대기열·재생 공통 동작 |
| `src/main/resources/static/css/app.css` | 공통 기능·접근성 스타일 |
| `src/main/resources/static/themes/` | 테마 카탈로그·문구·스타일·SVG·미리 보기 |
| `src/test/java/io/moviesites/` | 서버 회귀 검사 |
| `scripts/` | UI·테마·문서·패키지·실행 검사 |
| `docs/` | 루트 README가 연결하는 상세 안내 |

Java/Gradle 표준 소스 구성을 따릅니다. 현재 작은 Java 코드베이스는 기본 패키지 하나를 유지하며, 실제 유지관리 필요가 생기면 분리합니다.

## 실행과 변경 검증

[시작하기](getting-started.md)의 JAR 또는 명시적 원본·데이터 환경변수와 `./gradlew bootRun`을 사용합니다. 동시 실행본은 포트·데이터 루트를 분리하세요. 테스트는 프로젝트 안의 임시 파일을 정리합니다. 실제 개인 영상은 테스트 fixture로 커밋하지 않습니다.

`scripts/check.sh`는 JS 구문·캐시/대기열/플레이어·테마·콘텐츠 해시·Git/JAR 데이터 분리를 확인합니다. 문서 검사기는 링크·설정 예시·번역 대응·빌드 참조를 확인합니다. 화면 변경은 실제 브라우저 검사도 필요합니다. 미디어 어댑터·단위 테스트는 기기별 코덱·NAS 장애·부하 시험을 대신하지 않습니다.

버전 관리하는 UI 자원 변경 시 `index.html` 또는 가져오는 모듈의 콘텐츠 해시 쿼리를 갱신하세요. 원본·생성 데이터·빌드 출력·인증정보는 추적하지 않습니다. 정리 작업으로 경로 경계·스트림 검증을 바꾸지 마세요.

실행 중인 별도 검증 인스턴스에는 다음을 사용할 수 있습니다.

```bash
python3 -B scripts/verify-runtime.py http://127.0.0.1:8080
```

Gradle 캐시는 기본 사용자 경로를 사용합니다. 작업영역 제한 자동화에서는 `GRADLE_USER_HOME=$PWD/.cache/gradle`, 생성한 `$PWD/.cache/tmp`, `JAVA_TOOL_OPTIONS="-XX:-UsePerfData -Djava.io.tmpdir=$PWD/.cache/tmp"`를 지정할 수 있습니다. 기여자 인증 절차는 아닙니다.

## 테마와 문서

[테마 기여](themes.md#테마-추가)를 참고하세요. 영어가 문서 기준이며 루트 `README.ko.md`와 `docs/ko/`의 동일 파일명을 같은 PR에서 갱신합니다. 현재 한국어 UI 문구는 문서 번역과 별개입니다. 과거 SDD 메타데이터는 [유지관리 안내](maintainers.md)에 설명합니다.

운영·검증 스크립트는 Python 3.14, JavaScript 검사는 Node 24를 사용합니다. 애플리케이션 이미지에는 Python·Node 런타임을 추가하지 않습니다.
