# MovieSites

폴더에 보관한 영상을 브라우저에서 탐색하고 감상하는 오픈소스 셀프 호스팅 영상 플랫폼입니다. 개인 영상, 가족 기록, 교육 자료, 팀 영상 아카이브처럼 다양한 컬렉션에 사용할 수 있습니다. 폴더 구조가 화면의 분류가 되며, 파일명이 영상 제목이 됩니다.

JDK 25와 Spring Boot 4.1.2로 만든 단일 애플리케이션입니다. 정적 HTML/CSS/JavaScript와 API를 하나의 JAR에서 제공하므로 별도의 프런트엔드 서버나 Node.js 운영 프로세스가 필요하지 않습니다. 데이터베이스, 전체 색인, 재귀 폴더 감시도 사용하지 않습니다.

## 빠른 시작

JDK 25, FFmpeg와 ffprobe를 준비한 뒤 실행합니다.

```bash
./mvnw verify
MOVIESITES_MEDIA_ROOT="/absolute/path/to/videos" \
MOVIESITES_DATA_ROOT="/absolute/path/to/moviesites-data" \
MOVIESITES_THEME="child-toy" \
java -jar target/moviesites.jar
```

브라우저에서 `http://localhost:8080`을 엽니다. 미디어 경로는 미리 존재해야 하며, 데이터 경로와 서로 겹치면 안 됩니다. 상대 경로는 실행한 디렉터리 기준입니다. 운영 환경에서는 절대 경로를 지정하세요. 빌드 도구와 로컬 캐시는 `.cache/`에 저장됩니다.

## Docker 실행

```bash
docker build -t moviesites:local .

docker run -d --init --name moviesites --restart unless-stopped \
  -p 127.0.0.1:8080:8080 --memory=2g --cpus=2 \
  --mount type=bind,source=/absolute/path/to/videos,target=/media,readonly \
  --mount type=bind,source=/absolute/path/to/moviesites-data,target=/data \
  -e MOVIESITES_MEDIA_ROOT=/media \
  -e MOVIESITES_DATA_ROOT=/data \
  -e MOVIESITES_THEME=20s-soft-minimal \
  moviesites:local
```

두 호스트 디렉터리를 먼저 준비하고 데이터 경로를 컨테이너 사용자 `10001:10001`이 쓸 수 있게 설정하세요. 본인 소유 경로에서는 `--user "$(id -u):$(id -g)"`로 실행할 수도 있습니다. NAS는 호스트에 먼저 마운트한 뒤 그 경로를 연결합니다. Docker Desktop에서는 호스트 경로 공유를 허용해야 합니다.

컨테이너에는 Java와 FFmpeg가 포함됩니다. 원본 영상과 생성 데이터는 외부 마운트에 남으며 Git, JAR, Docker 이미지에 포함되지 않습니다. 예제의 접속 범위는 로컬 PC입니다. 외부 접속 주소·방화벽·TLS는 운영 환경에서 설정합니다. 현재 서비스는 로그인 없는 읽기 전용 방식으로, 접속자는 지정한 루트의 영상을 탐색·재생할 수 있습니다. 업로드·수정·삭제 API는 없습니다.

## 테마

기존 장난감 스타일인 `child-toy`를 기본으로, 10대부터 60대까지 각 3개씩 총 19개 프리셋을 제공합니다. 나이대는 스타일 분류이며 사용 제한이나 사용자 나이 수집을 의미하지 않습니다. 모든 테마는 같은 미디어·탐색·재생 기능을 사용합니다.

실행 중인 서비스에서 `/themes/preview.html`을 열면 전체 목록과 실제 화면을 볼 수 있습니다. 미리보기는 해당 탭에만 적용됩니다. 기본 테마는 `MOVIESITES_THEME`를 변경하고 프로세스 또는 컨테이너를 다시 만들어 적용합니다. 등록되지 않은 ID는 시작 오류로 안내합니다.

테마 목록과 전용 디렉터리 구조는 [테마 안내](docs/themes.md), 추가 방법은 [테마 디렉터리 문서](src/main/resources/static/themes/README.md)를 참고하세요.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `MOVIESITES_THEME` | `child-toy` | 등록된 테마 ID |
| `MOVIESITES_MEDIA_ROOT` | `./movie` | 원본 영상 경로, 읽기 전용 권장 |
| `MOVIESITES_DATA_ROOT` | `./runtime` | 썸네일·호환 재생본·작업 데이터 |
| `MOVIESITES_FOLDER_LABELS` | `{}` | 폴더 이름 → 표시 문구의 JSON 객체, 최대 32개 |
| `MOVIESITES_BROWSER_CACHE_TTL_SECONDS` | `120` | 목록 캐시 수명, 60~180초 |
| `MOVIESITES_PAGE_SIZE` | `48` | 응답당 항목 수, 1~100 |
| `MOVIESITES_MAX_STREAMS` | `4` | 최대 동시 영상 전송, 1~16 |
| `MOVIESITES_MAX_CACHE_BYTES` | `10737418240` | 생성 데이터 예산, 기본 10 GiB |
| `MOVIESITES_MIN_FREE_BYTES` | `536870912` | 새 작업에 필요한 최소 여유 공간 |
| `MOVIESITES_FFMPEG` | `ffmpeg` | FFmpeg 실행 파일 |
| `MOVIESITES_FFPROBE` | `ffprobe` | ffprobe 실행 파일 |
| `PORT` | `8080` | HTTP 포트 |

표시 문구 예: `MOVIESITES_FOLDER_LABELS='{"강의":"강의 모음","인터뷰":"인터뷰 영상"}'`. 원본 이름·경로는 바뀌지 않습니다. 모든 경로에서 같은 폴더 이름에 동일한 문구가 적용되며, 영상 길이를 자동 집계하는 기능은 아닙니다.

## 미디어 관리

```text
videos/
├── 여행/
│   ├── .folder.jpg
│   └── 바다에서 보낸 하루.mp4
└── 강의/
    └── 시작하기.mp4
```

현재 위치 바로 아래만 조회합니다. 전체 정렬·총개수 집계·전역 검색은 하지 않으며 파일시스템의 열거 순서대로 나눠 표시합니다. 파일을 옮기거나 추가한 뒤에는 새로고침으로 변경을 확인할 수 있습니다. 캐시는 만료 시 자동 폴링하지 않고 다음 조회에서 갱신합니다.

각 폴더에 `.folder.jpg`를 두면 대표 이미지로 사용합니다. JPEG 최대 512 KiB, 한 변 1280 이하, 총 921600픽셀 이하를 지원합니다. 예를 들어 960×540 이미지를 사용할 수 있습니다. 표지가 없으면 기본 그림을 표시하며 하위 폴더를 검색하지 않습니다. 표지와 영상의 사용 권한은 운영자가 관리합니다.

MP4/H.264/yuv420p/AAC 조건에 맞으면 원본을 스트리밍하고, 필요한 경우 FFmpeg로 호환 재생본을 만듭니다. 표준 HTML `<video controls playsinline>`을 사용하며 원본은 수정하지 않습니다. 브라우저·입력 코덱별 실제 검증 범위와 NAS/자원 한계는 [설계](docs/architecture.md), [검증 기록](docs/verification.md)을 확인하세요.

## 개발과 라이선스

```bash
./mvnw verify
bash scripts/check.sh
```

[개발 안내](docs/development.md) · [현재 명세](docs/spec-platform.json) · [기여 절차](docs/governance/initialization.md)

AGPL-3.0-only. 수정 버전을 네트워크로 제공할 때는 해당 버전의 대응 소스를 사용자에게 제공해야 합니다. 영상과 개인 데이터는 애플리케이션 라이선스의 배포 대상이 아닙니다. 의존성과 컨테이너 구성요소는 [서드파티 고지](THIRD_PARTY_NOTICES.md)를 참고하세요.
