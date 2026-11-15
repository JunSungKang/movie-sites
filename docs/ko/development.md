# 개발

[English](../development.md) · [문서 목록](../../README.ko.md#문서)

## 준비와 검사

전체 검사에는 JDK 25·Node.js 24·Python 3.14.x를 사용합니다. 로컬 미디어 처리를 시험하려면 FFmpeg/ffprobe를 설치하거나 Docker 이미지를 사용하세요. 전역 Gradle·프론트엔드 개발 서버·데이터베이스는 필요하지 않습니다.

fork/clone 후 작업 브랜치에서 실행합니다.

```bash
./gradlew clean build
bash scripts/check.sh
python3 -B scripts/check-docs.py
```

실행 파일은 `build/libs/moviesites.jar`, Java 결과는 `build/test-results/test/`, HTML 보고서는 `build/reports/tests/test/`입니다. 서버만 검사하려면 `./gradlew test`, UI 언어·PWA를 포함한 JavaScript 전체 검사는 `node --test scripts/*.test.mjs`를 사용합니다. 패키지 검사는 최신 JAR가 필요합니다. [기여 안내](../../CONTRIBUTING.md)를 참고하세요.

## 소스 구조

| 경로 | 역할 |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/` | Kotlin DSL·프로젝트 이름·검증된 Wrapper |
| `src/main/java/xyz/gg_jskang/moviesites/` | 애플리케이션 진입점·기능 공개 API·구현·HTTP 어댑터 |
| `src/main/resources/application.yml` | 환경변수 설정 |
| `src/main/resources/static/js/{catalog,player,queue,random-playback,installation}/` | 기능별 모델·프레젠터·뷰와 공개 `index.js` 진입점 |
| `src/main/resources/static/js/shared/` | API·브라우저·캐시·UI·번역 공통 어댑터 |
| `src/main/resources/static/js/app.js` | 기능 생성과 콜백 연결 |
| `src/main/resources/static/css/app.css` | 공통 기능·접근성 스타일 |
| `src/main/resources/static/themes/` | 테마 카탈로그·문구·스타일·SVG·미리 보기 |
| `src/test/java/xyz/gg_jskang/moviesites/` | 서버 회귀·컴파일된 의존성 구조 검사 |
| `scripts/` | UI·테마·문서·패키지·실행 검사 |
| `docs/` | 루트 README가 연결하는 상세 안내 |

Java/Gradle 표준 소스 구성과 실행 JAR 하나를 유지합니다. 기능 패키지 루트에 지원하는 공개 타입, `internal`에 구현 세부사항, `web`에 HTTP 어댑터를 둡니다. 다른 기능에서는 공개 타입을 사용하고 해당 기능의 `internal`·`web`을 참조하지 않습니다. 구체적 역할과 강제 범위는 [코드 경계](architecture.md#코드-경계)를 참고하세요.

브라우저 기능은 각 `index.js`에서 팩토리를 공개합니다. 모델은 상태·정책, 프레젠터는 주입된 API·뷰 조정, 뷰는 DOM 출력과 UI 이벤트 전달을 맡습니다. 기능끼리 직접 import하지 않고 `app.js`의 콜백으로 연결합니다. 공통 그룹은 `shared/<group>/index.js`를 공개하고 테마도 이 진입점을 사용합니다. 폴더 이름만으로 규칙을 강제할 수 없으므로 가시성·명시적 진입점·구조 검사를 함께 사용합니다.

## 실행과 변경 검증

[시작하기](getting-started.md)의 JAR 또는 명시적 원본·데이터 환경변수와 `./gradlew bootRun`을 사용합니다. 동시 실행본은 포트·데이터 루트를 분리하세요. 테스트는 프로젝트 안의 임시 파일을 정리합니다. 실제 개인 영상은 테스트 fixture로 커밋하지 않습니다.

`scripts/check.sh`는 JS 구문·의존성 경계·캐시/대기열/플레이어·테마·콘텐츠 해시·Git 제외·패키지 자원의 바이트 일치·JAR/데이터 분리를 확인합니다. 문서 검사기는 링크·설정 예시·번역 대응·빌드 참조를 확인합니다. 화면 변경은 실제 브라우저 검사도 필요합니다. 미디어 어댑터·단위 테스트는 기기별 코덱·NAS 장애·부하 시험을 대신하지 않습니다.

버전 관리하는 UI 자원을 바꾸면 다음을 실행합니다.

```bash
node scripts/update-assets.mjs
node scripts/update-assets.mjs --check
node --test scripts/architecture.test.mjs scripts/assets.test.mjs
```

갱신기는 기본 모듈의 import·재공개 의존성을 따라 콘텐츠 해시를 언어별 HTML 진입점까지 전파하고 제한된 오프라인 캐시 버전도 갱신합니다. `--check`는 쓰지 않고 오래된 참조를 확인합니다. 의존성 그래프를 검사할 수 있도록 모듈 경로는 리터럴을 사용하며 변수 기반 동적 import는 거부합니다. 공통 설정·자원을 바꾸면 언어별 manifest·미리 보기도 함께 맞춥니다. 원본·생성 데이터·빌드 출력·인증정보는 추적하지 않습니다. 정리 작업으로 경로 경계·스트림 검증을 바꾸지 마세요.

Java 구조 검사는 컴파일된 의존성에서 금지된 기능 간 참조를 확인합니다. JavaScript 구조 검사는 패키지 모듈 전체의 공개 진입점·순환 의존성·프레젠터 DOM 접근·뷰의 네트워크 접근·조립기의 직접 입출력을 확인합니다. import 추출 결과는 Node의 V8 모듈 파서와 대조하고 의도적인 위반 예제로 검사가 동작함을 확인합니다. 선언된 구조 규칙을 검사하는 것이며 모든 런타임 동작이나 업무 정책 오류를 증명하는 검사는 아닙니다.

실행 중인 별도 검증 인스턴스에는 다음을 사용할 수 있습니다.

```bash
python3 -B scripts/verify-runtime.py http://127.0.0.1:8080
```

Gradle 캐시는 기본 사용자 경로를 사용합니다. 작업영역 제한 자동화에서는 `GRADLE_USER_HOME=$PWD/.cache/gradle`, 생성한 `$PWD/.cache/tmp`, `JAVA_TOOL_OPTIONS="-XX:-UsePerfData -Djava.io.tmpdir=$PWD/.cache/tmp"`를 지정할 수 있습니다. 기여자 인증 절차는 아닙니다.

## 테마와 문서

[테마 기여](themes.md#테마-추가)를 참고하세요. 영어가 문서 기준이며 루트 `README.ko.md`와 `docs/ko/`의 동일 파일명을 같은 PR에서 갱신합니다. 화면 언어는 `MOVIESITES_UI_LOCALE=en` 또는 `ko`로 선택합니다. 기본 UI 문구는 `static/js/shared/i18n/index.js`에 두며 미디어 이름과 운영자 태그는 일반 텍스트로 유지합니다. 릴리스 책임은 [유지관리 안내](maintainers.md)를 참고하세요. 내부 계획과 운영 증적은 공개 문서에 포함하지 않습니다.

운영·검증 스크립트는 Python 3.14, JavaScript 검사는 Node 24를 사용합니다. 애플리케이션 이미지에는 Python·Node 런타임을 추가하지 않습니다.

## PWA 변경

`src/main/resources/` 아래 `static/js/installation/`과 `static/js/pwa.js` 부트스트랩·`static/service-worker.js`·`static/manifest.webmanifest`에서 설치 동작을 관리합니다. `static/pwa/`는 브랜드 아이콘과 오프라인 전용 자원입니다. `scripts/pwa.test.mjs`는 실제 설치 이벤트·독립 실행/비보안 환경·서비스 워커 네트워크/개인정보 경계를 확인합니다. 서버 PWA 검사는 HTTP 형식·캐시 재검증·아이콘 크기/불투명도·워커 범위를 확인합니다.

오프라인 자원을 바꾸면 `offline.html`·`pwa/offline.css`·`pwa/icon-192.png` 순서로 바이트를 이어 붙인 SHA-256의 앞16자에 맞춰 워커 캐시 접미사를 갱신합니다. 목록·미디어·토큰은 이 캐시에 추가하지 마세요. 패키지 검사는 이 버전과 JAR 내부 PWA 바이트를 확인합니다. 마스크 아이콘의 도형은 안전 영역에 배치하며 `pwa/icon.svg`를 편집 가능한 벡터 원본으로 사용합니다.

localhost 또는 신뢰 HTTPS 시험 인스턴스를 실제 브라우저에서 확인합니다. manifest·설치 조건, 설치/기기 안내, 독립 실행, 영상 재생·Range 탐색을 검사하고 시험 인스턴스만 연결을 끊어 일반 오프라인 안내·재연결을 확인하세요. 좁은 화면도 검사합니다. iOS 설치·코덱은 지원하는 실제 기기에서 별도 확인해야 하며 데스크톱 에뮬레이션을 실제 아이폰 검사로 표시하지 않습니다.
