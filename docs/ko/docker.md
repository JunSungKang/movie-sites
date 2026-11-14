# Docker 배포

[English](../docker.md) · [문서 목록](../../README.ko.md#문서)

MovieSites는 Ubuntu 26.04·Temurin 25 컨테이너 하나에 FFmpeg·ffprobe·CA 인증서를 구성합니다. 정적 화면·JSON API·영상 스트림은 8080 포트를 공유합니다. Python·Node는 개발·검사 도구이며 앱 실행 런타임이 아닙니다.

## 이미지 빌드

Linux 컨테이너 엔진과 Buildx/Bake를 사용합니다.

```bash
docker buildx bake -f docker-bake.hcl --load fallback-bases
docker buildx bake -f docker-bake.hcl --load moviesites
```

fallback 타깃은 고정된 공식 Java 이미지에서 프로젝트 전용 `moviesites-java-build:25-ubuntu26.04`·`moviesites-java-runtime:25-ubuntu26.04`를 만듭니다. 앱 타깃은 `moviesites:ubuntu26.04`를 빌드합니다. 호환 베이스는 `JAVA_BUILD_IMAGE`·`JAVA_RUNTIME_IMAGE`로 지정할 수 있으며 Dockerfile이 OS·Java 주 버전을 확인합니다. 공유 이미지 태그를 덮어쓰지 않습니다. 저장소의 Gradle Wrapper로 빌드하고 최종 이미지에는 JAR와 실행 구성요소만 포함합니다.

## 외부 저장소와 권한

원본과 생성 데이터는 이미지 밖에 둡니다.

| 호스트 경로 | 컨테이너 경로 | 접근 |
|---|---|---|
| 기존 원본 디렉토리 또는 마운트된 NAS | `/media` | 읽기 전용 |
| 별도의 생성 데이터 디렉토리 | `/data` | 읽기·쓰기, 활성 인스턴스 하나 |

이미지와 Compose 예시의 기본 사용자는 비루트 `10001:10001`입니다. 호스트 권한에 맞춰 `MOVIESITES_UID`·`MOVIESITES_GID`에 적절한 비루트 숫자 사용자·그룹을 지정하세요. 해당 계정이 데이터를 쓸 수 있고 원본을 읽을 수 있어야 합니다. 배포 오류를 해결하려고 원본 소유권을 재귀 변경하거나 모두에게 쓰기를 허용하지 마세요. Windows/macOS Docker Desktop에서는 선택한 호스트 경로 공유도 확인합니다.

예시의 `create_host_path: false`에 따라 마운트 원본 경로는 미리 존재해야 합니다. 컨테이너 환경변수는 호스트 경로 대신 `/media`·`/data`를 사용하며 두 루트는 겹치면 안 됩니다. 영상·메타데이터·썸네일·재생본은 JAR나 이미지에 포함하지 않습니다.

이미지와 Compose 서비스는 `LANG=C.UTF-8`·`LC_ALL=C.UTF-8`을 설정합니다. 한글 파일명과 환경변수를 위해 UTF-8 네이티브 로케일을 유지하세요. Java의 `file.encoding`만으로 실제 경로 디코딩을 보장하지 않으므로 베이스를 바꿀 때는 `native.encoding`·`sun.jnu.encoding`도 확인합니다.

## Compose 예시

raw 환경 파일을 지원하는 Docker Compose 2.30 이상을 사용합니다.

```bash
cp compose.env.example .env.compose
# 이미지·기존 경로·숫자 사용자/그룹을 비공개 파일에서 수정합니다.
docker compose --env-file .env.compose -f compose.yaml config
docker compose --env-file .env.compose -f compose.yaml up -d --no-build moviesites
```

공식 예시는 `moviesites` 서비스 하나이며 `127.0.0.1:8080`을 앱의 8080에 연결합니다. 이미 사용하는 포트라면 호스트 포트를 변경하세요. 다른 기기에 제공할 때는 공개 주소와 접근 정책을 정합니다. `MOVIESITES_ENV_FILE`은 JSON 문구·달러 기호를 보존하는 별도 raw 앱 환경 파일입니다. 앱 설정은 [설정 안내](configuration.md)에 있습니다.

| Compose 입력 | 기본값·용도 |
|---|---|
| `JAVA_BUILD_IMAGE`, `JAVA_RUNTIME_IMAGE` | 호환 빌드·실행 베이스. 기본은 프로젝트 전용 fallback 이미지 |
| `MOVIESITES_IMAGE` | `moviesites:ubuntu26.04`. 교체 시 검증한 불변 참조 사용 |
| `MOVIESITES_UID`, `MOVIESITES_GID` | `10001`·`10001`. 호스트의 비루트 접근 권한에 맞춤 |
| `MOVIESITES_BIND_ADDRESS`, `MOVIESITES_WEB_PORT` | `127.0.0.1`·`8080` |
| `MOVIESITES_MEDIA_PATH` | `./movie` → `/media`, 읽기 전용 |
| `MOVIESITES_DATA_PATH` | `./runtime` → `/data`, 읽기·쓰기 |
| `MOVIESITES_ENV_FILE` | `.env.example`. 운영별 값은 비공개 사본 사용 |

init·읽기 전용 컨테이너 루트·CPU/메모리/PID 제한·로컬 로그·정상 종료 대기 시간을 사용합니다. 이는 설정 가능한 실행 제한이며 처리량 보증이 아닙니다. JVM heap 이외에 FFmpeg와 네이티브 메모리를 위한 여유도 필요합니다.

## 시험과 업데이트

합성 영상·별도 데이터 디렉토리·사용하지 않는 루프백 포트에서 [재현 가능한 검사](verification.md)를 실행하세요. 격리 시험에 운영 `/data`를 연결하지 않습니다. 실제 호스트에서 한글 경로·대표 코덱·부분 전송·권한·데이터 잠금·재시작을 확인합니다. HTTP 정상 상태가 전체 NAS 검사는 아닙니다.

교체 전 현재 불변 이미지 참조·환경변수·숫자 사용자·실제 마운트 경로를 비공개 운영 기록에 보관합니다. 후보를 별도 환경에서 시험한 뒤 기존 writer를 정지하고 종료를 기다린 후 같은 운영 마운트로 교체합니다. 이전 이미지·설정을 복구용으로 유지하고 새 서비스 확인 후 정리합니다. 복구할 때도 후보를 먼저 정지한 뒤 이전 이미지가 데이터 루트를 열도록 합니다.

배포 잠금·복구 세부사항은 운영자의 환경에서 관리합니다. 다른 서비스가 함께 정지되지 않도록 서비스 범위의 Compose 명령을 사용하세요. 일상 교체·복구에 `down -v`·전체 prune을 사용하거나, 시작 오류 해결을 위해 원본·데이터 디렉토리를 삭제하지 않습니다.

## 데이터 루트 단독 소유

Java advisory FileLock과 원자적으로 생성하는 `/data/.moviesites.owner` 디렉토리로 생성 데이터 루트의 다중 writer를 막습니다. 두 번째 인스턴스는 기존 소유자 표시를 지우지 않고 시작을 거부합니다. 정상 종료는 자신의 표시를 제거하고 잠금을 해제합니다. 여러 인스턴스가 같은 `/data`를 사용하는 확장은 지원하지 않습니다.

SIGKILL·호스트 장애·비정상 종료 뒤에는 소유 증적이 남을 수 있습니다. 느린 writer가 죽었다고 추측하지 않고 시작을 거부합니다. 해당 루트를 사용하는 모든 컨테이너·프로세스를 정지하고 배포 잠금으로 복구를 직렬화한 뒤 증적을 확인하세요. 그 다음에만 남은 소유 디렉토리를 제거하고 인스턴스 하나를 시작합니다. advisory 잠금 파일을 삭제·재생성하거나 TTL로 소유권을 빼앗지 마세요. 이전 이미지로 복구할 때도 같은 원칙을 지킵니다.

## 외부 접속과 PWA

앱은 로그인 없는 읽기 전용 서비스이므로 도달 가능한 방문자는 지원 영상을 탐색·재생할 수 있습니다. 외부 공개 전에 네트워크·인증 경계를 구성하세요. PWA 설치를 위해 라이브러리와 API·미디어를 하나의 브라우저 신뢰 HTTPS 출처로 제공합니다. 개발용 localhost는 일반적인 예외입니다. `MOVIESITES_PUBLIC_URL`은 소개 메타데이터를 설정할 뿐 TLS를 켜지 않습니다. 프록시는 Range·206 응답과 루트 manifest·service worker 경로를 보존해야 합니다. [PWA 설정](configuration.md#앱으로-설치-pwa)을 참고하세요.

PWA 오프라인 화면은 연결 끊김을 안내하며 영상·목록·인증 세션을 오프라인 저장하는 기능이 아닙니다. 실제 NAS·브라우저 설치·전체화면·코덱 지원은 대상 기기에서 시험해야 합니다.
