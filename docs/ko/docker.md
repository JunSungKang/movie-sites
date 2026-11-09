# Docker 배포

[English](../docker.md) · [문서 목록](../../README.ko.md#문서)

Ubuntu 26.04·Temurin 25 컨테이너 하나에 FFmpeg·ffprobe·인증서를 구성합니다. Python·Node는 검증 도구이며 앱 실행 이미지에는 포함하지 않습니다. DB·별도 API 컨테이너도 필요하지 않습니다.

## 이미지 빌드

```bash
docker buildx bake -f docker-bake.hcl --load fallback-bases
docker buildx bake -f docker-bake.hcl --load movie-kids
```

첫 명령은 다이제스트로 고정한 공식 Temurin Ubuntu 26.04에서 **프로젝트 전용** `movie-kids-java-build:25-ubuntu26.04`·`movie-kids-java-runtime:25-ubuntu26.04`를 만듭니다. 공통 이미지 태그를 덮어쓰지 않습니다. 앱은 `movie-kids:ubuntu26.04`입니다. 호환 공통 이미지가 있으면 `JAVA_BUILD_IMAGE`·`JAVA_RUNTIME_IMAGE`로 지정합니다. Dockerfile에서 OS·Java 주 버전을 확인하고 Gradle Wrapper로 빌드합니다. 정적 화면에는 Node 빌드 단계가 필요하지 않습니다.

FFmpeg·curl·인증서는 프로젝트 앱 이미지에서 설치합니다. 이미지 기본 UID/GID는 기존 `10001:10001`, 조사한 Compose 운영 설정은 `501:20`입니다. 호스트 소유권에 맞는 숫자를 사용하고 원본을 재귀 chown하거나 전체 쓰기를 허용하지 마세요.

## Compose

Compose 2.30 이상(raw 환경 파일 지원)과 Buildx/Bake를 사용합니다.

```bash
cp platform.env.example .env.platform
# 호스트 경로·UID/GID·공개 주소·이미지를 수정합니다.
# 기존 소유권이 올바른 movie/·runtime/을 먼저 준비합니다.
docker compose --env-file .env.platform -f compose.platform.yaml config
docker compose --env-file .env.platform -f compose.platform.yaml up -d --no-build --no-deps movie-kids-web
```

프로젝트는 `platform`, 서비스는 `movie-kids-web`, 기본 컨테이너 이름은 `platform-movie-kids-web-1`입니다. 기본 공개는 루프백 **20010:8080 하나**입니다. 화면·API가 같은 포트를 쓰므로 웹 나머지 20011~20014·API 20110~20114를 추가 노출하지 않습니다. 다른 기기에 제공할 때 공개 주소를 지정하세요. `MOVIESITES_ENV_FILE`은 JSON 문구·달러 기호를 보존하는 별도의 raw 앱 환경 파일입니다. Compose 내부 경로는 `/media`·`/data`, 포트는 8080으로 고정합니다.

| Compose 입력 | 기본값·역할 |
|---|---|
| `JAVA_BUILD_IMAGE`, `JAVA_RUNTIME_IMAGE` | 프로젝트 대체 베이스, 빌드 시 공통 참조로 변경 가능 |
| `MOVIESITES_IMAGE` | `movie-kids:ubuntu26.04`, 운영은 검증한 불변 이미지 ID 사용 |
| `MOVIESITES_UID`, `MOVIESITES_GID` | `501`·`20`, 기존 숫자 소유권 보존 |
| `MOVIESITES_BIND_ADDRESS`, `MOVIESITES_WEB_PORT` | `127.0.0.1`·`20010` |
| `MOVIESITES_MEDIA_PATH` | `./movie` → `/media`, 읽기 전용 |
| `MOVIESITES_DATA_PATH` | `./runtime` → `/data`, 쓰기 가능·배타 잠금 |
| `MOVIESITES_ENV_FILE` | `.env.example`, 실제 설정은 비공개 앱 파일에 보존 |

Bind 원본은 미리 있어야 하며 `create_host_path: false`로 오타 경로를 자동 생성하지 않습니다. 기존 디렉토리를 익명·이름 있는 데이터 볼륨으로 대체하지 않습니다. 같은 `/data`에 여러 writer를 실행하지 마세요. 메모리 2 GiB·CPU 2·PID 128·init·읽기 전용 루트·local 로그는 기존 실행 설정을 재현합니다. 종료 유예는 Spring의 15초 정상 종료를 감싸도록 30초입니다.

다른 프로젝트 의존성 없이 Compose bridge를 사용합니다. 공유 `platform` 프로젝트 전체를 `down`하지 말고 서비스에 한정한 `stop`·`rm`·`start`·`up`을 사용하세요. `down -v`·전체 prune은 금지합니다.

## 격리 검증과 운영 전환

저장소 검사는 Python 3.14·Node 24로 실행합니다.

```bash
python3 -B scripts/verify-platform.py --image movie-kids:ubuntu26.04
```

`.codex-work/platform-release/` 아래 합성 영상·별도 데이터와 고유 QA Compose 프로젝트를 만들고 예약 루프백 웹 포트 하나만 사용합니다. 원본/변환 재생·썸네일·탐색·FFmpeg·경로 탈출/심볼릭 링크 거부·재생 토큰·미디어/루트 읽기 전용·쓰기 잠금·정상 종료/재시작을 검사합니다. `--keep`은 브라우저 검수를 위해 QA를 남기며 `qa-state.json`에 해당 환경만 정리할 명령을 기록합니다. 검증 스크립트는 운영 데이터를 마운트하지 않습니다.

운영 전환은 동일 이미지·Compose 해시의 통과 보고서를 요구하고 실제 배포 파일 잠금을 잡은 상태에서 기존 컨테이너·설정을 보존하며 실패 시 복구합니다. 실행 전 [전환·복구 안내](platform-migration.md)를 읽으세요. NAS 보안 디렉토리 지원·장애는 별도로 시험해야 하며 HTTP 정상 상태가 전체 저장소 검사는 아닙니다.

## 데이터 배타 소유권

기존 Java FileLock을 유지합니다. Docker Desktop 바인드 마운트의 실제 시험에서 advisory lock만으로는 컨테이너 간 잠금이 분리될 수 있어, 캐시·작업 처리 전에 `/data/.moviesites.owner` 디렉토리를 원자적으로 생성하도록 보강했습니다. 두 번째 인스턴스는 기존 소유자 표시를 지우지 않고 시작 실패합니다. 정상 종료는 소유자 표시·디렉토리와 파일 잠금을 해제합니다.

SIGKILL·호스트 장애 등 비정상 종료에는 소유 디렉토리가 남을 수 있습니다. 느린 writer를 죽었다고 추정하지 않고 안전하게 시작을 거부합니다. 해당 데이터 루트를 쓰는 **모든** 컨테이너·프로세스를 정지하고 공통 배포 잠금을 보유한 상태에서 소유자 증거를 확인한 뒤에만 잔여 디렉토리를 제거하고 하나만 시작하세요. 실행 중일 수 있는 writer가 있으면 지우지 마세요. TTL로 잠금을 빼앗지 않습니다.
