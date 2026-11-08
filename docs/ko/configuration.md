# 설정

[English](../configuration.md) · [문서 목록](../../README.ko.md#문서)

프로세스 시작 또는 컨테이너 생성 전에 환경변수를 설정합니다. 변경 후에는 재시작 또는 컨테이너 교체가 필요합니다. 아래는 영어 설정 참조와 함께 관리하는 한국어 표입니다.

| 변수 | JAR 기본값 | 의미와 범위 |
|---|---|---|
| `MOVIESITES_MEDIA_ROOT` | `./movie` | 미리 존재하는 원본 루트. 가능하면 읽기 전용 마운트 사용 |
| `MOVIESITES_DATA_ROOT` | `./runtime` | 다른 인스턴스·원본과 분리된 쓰기 가능한 생성 데이터 루트 |
| `MOVIESITES_THEME` | `child-toy` | 대소문자를 구분하는 등록 테마 ID. 미등록 값은 시작 거부 |
| `MOVIESITES_FOLDER_LABELS` | `{}` | 폴더 이름을 화면 표시 문구에 연결하는 JSON 객체 |
| `MOVIESITES_BROWSER_CACHE_TTL_SECONDS` | `120` | 목록 응답 메모리 캐시 수명. 60~180초 |
| `MOVIESITES_PAGE_SIZE` | `48` | 페이지당 항목. 1~100개 |
| `MOVIESITES_MAX_STREAMS` | `4` | 동시 영상 전송. 1~16개 |
| `MOVIESITES_MAX_CACHE_BYTES` | `10737418240` | 생성 데이터 예산. 최소 1048576바이트, 기본 10 GiB |
| `MOVIESITES_MIN_FREE_BYTES` | `536870912` | 새 처리에 필요한 최소 여유 공간. 0 이상 바이트 |
| `MOVIESITES_FFMPEG` | `ffmpeg` | FFmpeg 실행 파일 이름 또는 경로 |
| `MOVIESITES_FFPROBE` | `ffprobe` | ffprobe 실행 파일 이름 또는 경로 |
| `PORT` | `8080` | HTTP 포트. 컨테이너 공개 매핑도 일치시켜야 함 |

Docker 기본 루트는 `/media`와 `/data`입니다. 이는 컨테이너 안의 경로이며 호스트 원본 경로는 별개입니다. [환경변수 예시](../../.env.example)를 `--env-file`로 전달하세요. JAR는 `.env`를 자동으로 읽지 않습니다. 호스트 JAR에는 호스트 경로를 설정하고 컨테이너용 예시를 그대로 source하지 마세요.

## 표시 문구

```bash
export MOVIESITES_FOLDER_LABELS='{"Tutorials":"Learning","Interviews":"Conversations"}'
```

폴더 이름·API 경로·실제 파일은 바뀌지 않으며 길이를 계산하는 기능도 아닙니다. 같은 폴더 이름에는 깊이에 관계없이 같은 문구를 씁니다. JSON은 8192자·32개 항목 이내이며 이름과 값은 제어 문자 없는 1~255자 문자열이어야 합니다. 이름에는 경로 구분자를 넣을 수 없습니다. 잘못되거나 과도한 값은 시작을 거부합니다. 길이·분류 설명은 운영자가 실제 내용과 일치시켜야 합니다.

## 자원 동작

디렉토리 세션·읽기/분석 대기열·변환 작업에도 고정 제한이 있습니다. [아키텍처](architecture.md)를 참고하세요. 캐시 바이트 예산은 전체 메모리 제한이 아닙니다. JVM·FFmpeg·브라우저 디코더·GPU 사용량은 별개이므로 실제 저장소와 동시 사용 조건으로 시험해야 합니다.
