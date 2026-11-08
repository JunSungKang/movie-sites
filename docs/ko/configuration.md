# 설정

[English](../configuration.md) · [문서 목록](../../README.ko.md#문서)

프로세스 시작 또는 컨테이너 생성 전에 환경변수를 설정합니다. 변경 후에는 재시작 또는 컨테이너 교체가 필요합니다. 아래는 영어 설정 참조와 함께 관리하는 한국어 표입니다.

| 변수 | JAR 기본값 | 의미와 범위 |
|---|---|---|
| `MOVIESITES_MEDIA_ROOT` | `./movie` | 미리 존재하는 원본 루트. 가능하면 읽기 전용 마운트 사용 |
| `MOVIESITES_DATA_ROOT` | `./runtime` | 다른 인스턴스·원본과 분리된 쓰기 가능한 생성 데이터 루트 |
| `MOVIESITES_PUBLIC_URL` | 빈 값 | 공개 HTTP(S) 출처(예: `https://media.example.org`), 사용자 정보·하위 경로·쿼리·fragment 제외 |
| `MOVIESITES_THEME` | `child-toy` | 대소문자를 구분하는 등록 테마 ID. 미등록 값은 시작 거부 |
| `MOVIESITES_FOLDER_LABELS` | `{}` | 폴더 이름을 화면 표시 문구에 연결하는 JSON 객체 |
| `MOVIESITES_BROWSER_CACHE_TTL_SECONDS` | `120` | 서버 스냅샷·브라우저 목록 캐시 최대 유효 시간. 60~180초 |
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

## 검색 노출

검색 노출 대상은 `/about` 소개 페이지입니다. 서버에서 일반적인 소개·설명·Open Graph/Twitter·WebPage JSON-LD를 만들고 영상 목록을 조회하지 않습니다. `MOVIESITES_PUBLIC_URL`로 canonical·Open Graph 주소와 `/sitemap.xml`을 생성합니다. 외부에서 접속하는 출처를 입력하고 기본 포트가 아니면 포트도 포함하세요. 요청의 Host·전달 헤더는 이 주소에 반영하지 않습니다. 잘못된 설정은 시작을 거부하며 빈 값이면 canonical을 생략하고 사이트맵은 설정 전까지 503을 반환합니다. 변경 후 재시작합니다.

`/` 앱·폴더 쿼리 URL·API·이미지·영상 응답에는 `X-Robots-Tag: noindex, nofollow, noimageindex`를 적용하고 앱 HTML에도 robots 메타 태그를 둡니다. 사이트맵에는 `/about`만 포함합니다. 검색엔진이 제외 헤더를 읽을 수 있도록 `robots.txt`는 크롤링을 허용합니다. 검색 제외 요청은 인증이나 기존 색인의 즉시 삭제를 뜻하지 않습니다. 접속 가능한 방문자는 여전히 익명으로 미디어를 볼 수 있습니다. [Google noindex 안내](https://developers.google.com/search/docs/crawling-indexing/block-indexing)를 참고하세요.

## 캐시 정책

| 데이터 | 서버 동작 | HTTP·브라우저 동작 |
|---|---|---|
| 완성된 길이순 목록 | 같은 폴더 스냅샷을 설정된 60~180초 재사용하고 준비 중 요청도 합침 | `private`·ETag·스냅샷의 남은 유효 시간·`must-revalidate` |
| 준비/실패 목록·기존 파일시스템 커서·설정·영상 정보·재생 | 재사용할 목록으로 저장하지 않음 | `no-store`, 목록 캐시에 재생 토큰 저장 안 함 |
| 폴더·영상 JPEG | 공통 payload LRU 128개·32 MiB·동시 읽기 8개. 표지 120초·썸네일 300초 고정 수명 | 검증된 버전 주소는 `private, max-age=86400, immutable`, 버전 없는 표지는 서버 잔여 수명과 `must-revalidate` |
| 패키지 JS/CSS/JSON/SVG/PNG | payload LRU 256개·8 MiB·동시 읽기 32개, 자원 하나 최대 1 MiB | 실제 SHA-256과 일치하는 `v`만 `public, max-age=31536000, immutable`, 누락·불일치는 ETag와 `no-cache` 재검증 |
| 앱·테마 미리 보기 HTML | 현재 패키지에서 제공 | `no-store`, 아카이브 시각으로 변경 판단 안 함 |
| 소개·사이트맵 | 시작 시 생성, 영상 목록 조회 없음 | 공개 5분 캐시, 소개·사이트맵 ETag |

생성 썸네일이 메모리 읽기 상한 2 MiB를 넘으면 payload 캐시에 넣지 않고 디스크에서 전송하며 private 60초·재검증 정책을 적용합니다.

탭 메모리 캐시는 최대 20페이지·2000항목·직렬화 약 10 MiB입니다. 선택적 `sessionStorage` 복원은 약 2.8 MiB 예산의 단일 레코드로 제한하며 원래 만료 시각을 보존하고 루트·서버 인스턴스가 다르면 버립니다. 저장 거부·용량 오류는 메모리로 대체합니다. 영상·재생 권한 대신 제목·경로 등 목록 메타데이터를 해당 기기에 저장합니다. 사이트 저장 공간을 지우면 제거됩니다.

새로고침 버튼은 현재 폴더의 브라우저 목록을 무효화하고 `refresh=true`로 새 서버 스냅샷을 요청합니다. 조회로 만료 시각을 늘리거나 만료 때문에 배경 조회하지 않습니다. 새 목록에는 현재 표지 버전을 넣으며 이미지 버전은 기존 파일 식별자·크기·수정 시각 기준입니다. 이전 버전 주소는 만료 전까지 이전 이미지 바이트를 반환할 수 있습니다. 1024바이트 이상 텍스트·JSON·SVG는 협상 시 gzip 압축하고 영상·JPEG는 제외합니다. 변경 없는 응답은 ETag로 304를 반환해 본문 전송을 줄입니다. [MDN Cache-Control](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control)을 참고하세요.
