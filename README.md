# MovieSites

폴더에 보관한 영상을 가로형 저널로 둘러보는 오픈소스 개인 영상 서재입니다. JDK 25와 Spring Boot 4.1.2의 단일 애플리케이션이며, 정적 HTML/CSS/JavaScript를 함께 제공합니다. DB, 전체 색인, 전체 폴더 감시는 사용하지 않습니다.

## 실행

JDK 25로 빌드합니다. Maven은 프로젝트의 wrapper가 내려받으며 캐시는 프로젝트 `.cache/`에 저장됩니다.

```bash
./mvnw verify
MOVIESITES_MEDIA_ROOT="/absolute/path/to/videos" \
MOVIESITES_DATA_ROOT="/absolute/path/to/moviesites-data" \
java -jar target/moviesites.jar
```

직접 JAR로 실행할 때는 `ffmpeg`, `ffprobe`를 설치해야 합니다. 원본과 데이터 경로는 서로 겹치지 않아야 합니다. 미설정 개발 기본값은 `./movie`, `./runtime`이며 상대 경로는 현재 작업 디렉터리를 기준으로 합니다. 운영에서는 절대 경로를 지정하세요. 앱은 원본 디렉터리를 자동 생성하지 않습니다.

## Docker

```bash
docker build -t moviesites:local .

docker run -d --init --name moviesites --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  --memory=2g --cpus=2 \
  --mount type=bind,source=/absolute/path/to/videos,target=/media,readonly \
  --mount type=bind,source=/absolute/path/to/moviesites-data,target=/data \
  -e MOVIESITES_MEDIA_ROOT=/media \
  -e MOVIESITES_DATA_ROOT=/data \
  -e MOVIESITES_BROWSER_CACHE_TTL_SECONDS=120 \
  moviesites:local
```

두 호스트 디렉터리를 먼저 준비하고, 데이터 경로를 컨테이너 사용자 UID/GID `10001:10001`이 쓸 수 있게 설정하세요. Linux/macOS에서 본인 소유 디렉터리를 사용할 때는 `--user "$(id -u):$(id -g)"`로 실행할 수도 있습니다. NAS는 Docker 호스트에 먼저 마운트하고 그 경로를 연결합니다. Docker Desktop에서는 호스트 경로 공유가 허용되어야 합니다.

기본 예시는 이 PC에서만 접속할 수 있습니다. 외부 공개 시 포트 publish 주소와 방화벽을 운영 환경에 맞게 설정하세요. 현재 버전에는 로그인 기능이 없으며, 공개 주소에 접속한 방문자는 지정한 원본 루트 안의 지원 영상을 익명으로 탐색·재생할 수 있습니다. 원본 수정·삭제·업로드 API는 없습니다.

컨테이너에는 Java 런타임, 애플리케이션과 FFmpeg가 포함됩니다. 원본·썸네일·변환 영상은 Git, JAR, 이미지에 포함하지 않습니다. 컨테이너를 교체해도 외부 볼륨의 데이터는 보존됩니다. 위 CPU/메모리 값은 예시이며 영상 해상도·동시 재생 수에 맞춰 검증해야 합니다.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `MOVIESITES_MEDIA_ROOT` | `./movie` | 원본 영상 경로, 읽기 전용 사용 |
| `MOVIESITES_DATA_ROOT` | `./runtime` | 썸네일·재생본·작업 파일 경로 |
| `MOVIESITES_BROWSER_CACHE_TTL_SECONDS` | `120` | 60~180초 |
| `MOVIESITES_PAGE_SIZE` | `48` | 응답당 1~100개 |
| `MOVIESITES_MAX_STREAMS` | `4` | 최대 동시 전송 수, 1~16 |
| `MOVIESITES_MAX_CACHE_BYTES` | `10737418240` | 생성 캐시 예산, 기본 10 GiB |
| `MOVIESITES_MIN_FREE_BYTES` | `536870912` | 새 작업에 필요한 최소 여유 공간 |
| `MOVIESITES_FFMPEG` | `ffmpeg` | 실행 파일 경로 |
| `MOVIESITES_FFPROBE` | `ffprobe` | 실행 파일 경로 |
| `PORT` | `8080` | HTTP 포트 |

## 탐색·재생 동작

- 현재 폴더 바로 아래 항목만 읽습니다. 폴더를 클릭하면 그 폴더를 조회합니다.
- 브라우저는 조회한 목록을 경로·조회 구간별로 캐싱합니다. 재사용해도 유효기간을 연장하지 않으며, 만료 후 다시 열 때만 조회합니다. 목록 새로고침은 캐시를 우회합니다.
- 목록은 파일시스템의 열거 순서입니다. 초대형 폴더의 전체 정렬·총개수·전역 검색은 제공하지 않습니다. 분할 탐색 토큰이 만료되면 목록을 새로고침하세요.
- 심볼릭 링크를 따라가지 않으며, 보안 디렉터리 접근을 지원하는 파일시스템이 필요합니다. Linux/macOS 서버를 검증 대상으로 합니다.
- MP4/H.264/yuv420p/AAC 조건을 충족하면 원본을 재생합니다. 필요한 경우 재포장 또는 최대 1920×1080 범위의 SDR 호환 재생본을 만듭니다. 세로 영상은 비율을 보존합니다. 원본은 수정하지 않습니다.
- 변환은 한 번에 하나, 분석은 최대 두 개입니다. 최초 변환에는 대기 시간이 필요합니다. 손상·암호화·지원하지 않는 입력은 오류로 안내합니다.
- 캐시는 원본 경로·크기·수정 시각·파일 식별 정보로 구분합니다. 파일이 같은 크기·시각·식별 정보로 덮어써진 특수한 경우에는 생성 캐시 정리가 필요할 수 있습니다.
- NAS 오류는 빈 폴더와 구분합니다. 네트워크 파일 호출은 OS 수준에서 멈출 수 있으므로 앱 타임아웃이 물리적 읽기를 즉시 취소한다고 보장하지 않습니다.

캐시는 작업 중 출력까지 용량에 포함하며, 한도에 도달하면 재생 중이지 않은 오래된 생성 파일을 회수합니다. 재시작 시 중단된 임시 파일을 정리합니다. 데이터 경로는 서버 인스턴스마다 따로 지정해야 하며 동일 경로의 동시 실행은 잠금으로 차단됩니다. 목록의 이전 페이지 기록은 최근 20개까지 보관하고 처음 목록으로 돌아갈 수 있습니다.

## 검증

```bash
./mvnw verify
bash scripts/check.sh
```

서버의 `/api/health`는 애플리케이션 상태를 확인하며 NAS 전체를 검사하지 않습니다. 구체적인 실측 결과와 미검증 환경은 `docs/verification.md`에 기록합니다. 설계·요구사항은 `docs/design-v4.md`, `docs/spec-v4.json`에 있습니다.

## 라이선스

AGPL-3.0-only. 실행 중인 수정 버전을 네트워크로 제공할 때는 해당 버전의 대응 소스를 사용자에게 제공해야 합니다. 영상과 개인 데이터는 프로젝트 라이선스 대상이 아닙니다. 의존성·컨테이너 구성요소는 `THIRD_PARTY_NOTICES.md`와 각 배포물의 고지 사항을 확인하세요.
