# Compose 전환과 복구

[English](../platform-migration.md) · [Docker 안내](docker.md)

MovieSites(`movie-kids-web`)만 교체합니다. 원본·생성 데이터·직전 이미지·기존 컨테이너를 보존합니다. 격리 시험에 운영 `/data`를 쓰지 않으며 `down -v`·전체 prune·다른 플랫폼 서비스 정지는 금지합니다.

## 기존 docker run → Compose 대응

| 기존 설정 | 새 설정 |
|---|---|
| `moviesites` | 프로젝트 `platform`·서비스 `movie-kids-web`, 기본 `platform-movie-kids-web-1` |
| Ubuntu 24.04 Temurin 25 | 프로젝트 전용 Ubuntu 26.04 Temurin 25 JDK 빌드·JRE 실행 베이스 |
| 호스트 웹 18081 → 8080 | 예약 웹 20010 → 8080, 화면·API는 같은 앱 |
| 지정 호스트 공개 주소 | `MOVIESITES_BIND_ADDRESS`로 같은 주소 보존 |
| UID/GID `501:20` | `MOVIESITES_UID=501`·`MOVIESITES_GID=20` |
| `movie/` → `/media` 읽기 전용 | 실제 bind 원본·읽기 전용 보존 |
| `runtime/` → `/data` 쓰기 가능 | 실제 bind 원본·파일 잠금·원자적 소유권 보강 |
| init·읽기 전용 루트 | `init: true`·`read_only: true` |
| 메모리 2 GiB·CPU 2·PID 128 | `mem_limit: 2g`·`cpus: 2`·`pids_limit: 128` |
| unless-stopped·local 로그 | 같은 재시작 정책·드라이버 |
| 엔진 기본 종료 시간 | SIGTERM·30초 유예로 Spring 정상 종료 수용 |
| 엔진 bridge | Compose 관리 bridge, 다른 프로젝트 의존성 추가 없음 |
| 테마·문구·캐시·JVM 설정 | 비공개 raw 환경 파일로 운영 값 보존 |
| 기존 공개 URL | 새 웹 매핑에 맞춰 호스트 포트만 갱신 |

[프로젝트 베이스·앱 빌드](docker.md#이미지-빌드) 후 격리 검증합니다. `JAVA_BUILD_IMAGE`·`JAVA_RUNTIME_IMAGE`에 호환 공통 이미지를 넣을 수 있으나 공통 규격 부재는 프로젝트 대체 빌드를 막지 않습니다. 공유 태그는 덮어쓰지 않습니다. FFmpeg·인증서는 프로젝트 앱 이미지에 두며 DB·Python·Node 실행 런타임은 추가하지 않습니다.

## 직렬화한 운영 교체

1. Python 3.14를 사용합니다. 기존 이미지 ID·전체 inspect·숫자 사용자·마운트를 확보하고 데이터는 그대로 둡니다. 정지 전에 새 Compose 모델을 검사합니다.
2. 합의한 공통 `../operations/run/deploy.lock`에 실제 `fcntl.flock(LOCK_EX)`를 보유합니다. 허가된 경우 없는 부모·파일을 생성하며 해제하려고 잠금 inode를 삭제/재생성하지 않습니다. 도구가 경쟁 프로세스의 획득 실패도 확인합니다.
3. 확인된 기존 컨테이너를 정상 종료하고 완전한 종료를 기다린 뒤 기록된 `moviesites-before-ubuntu26-*` 이름으로 보존합니다. 그 뒤에만 같은 데이터 루트를 새 서비스로 엽니다.
4. 검증한 불변 이미지로 `platform/movie-kids-web`만 시작합니다. 실제 마운트·사용자·제한·앱 설정은 보존하고 Java 버전 같은 이미지 메타데이터는 새 베이스에서 상속합니다. 상태·하위 목록·썸네일·부분 재생·토큰 거부·실제 미디어 FFmpeg 디코드/인코드를 검사합니다.
5. 서비스만 stop/start해 재시작 후 검사를 반복하고 healthy·원본 해시를 확인합니다. 직전 컨테이너/이미지·비공개 복구 기록은 보관합니다.

```bash
python3.14 -B scripts/deploy-platform.py \
  --image movie-kids:ubuntu26.04 \
  --qa-report .codex-work/platform-release/qa-EXACT/verification.json \
  --lock ../operations/run/deploy.lock
```

보고서는 불변 이미지 ID·Compose 해시가 모두 일치해야 합니다. 기존 `moviesites`가 있는지 검사하는 최초 수동 실행→Compose 전환 도구이며 일반 무인 업데이트 도구는 아닙니다. `.codex-work/platform-release/`에 `production-before.json`, 앱/Compose 환경 파일, `deployment.json`을 남깁니다. 설정 파일은 0600이며 Git에 넣지 않습니다. 공통 잠금은 정지·이름 처리·시작·검사·재시작·자동 복구 전체를 감쌉니다.

## 복구

실패 시 자신의 후보만 정지/제거하고 기존 이름을 되돌린 뒤 원래 이미지·설정으로 시작해 예전 웹 주소를 확인한 후 잠금을 해제합니다. 데이터 볼륨·원본은 삭제하지 않습니다. 보존한 기존 컨테이너가 전체 실행 설정의 복구 원본입니다.

나중에 수동 복구할 때도 같은 잠금을 먼저 확보하세요. `deployment.json`의 정확한 후보 ID·이전 컨테이너 이름을 읽고 후보만 정지·완전 종료·제거한 뒤 보존 컨테이너를 `moviesites`로 되돌려 시작하고 이전 주소를 확인합니다. 이전 이미지를 재빌드/덮어쓰거나 마운트를 바꾸지 마세요. 잔여 `.moviesites.owner` 정리는 [소유권 안내](docker.md#데이터-배타-소유권)처럼 모든 writer가 정지한 뒤에만 합니다. 보강 전 이미지는 새 디렉토리 잠금을 검사하지 않으므로 신·구 서비스를 함께 실행하면 안 됩니다.
