# 시작하기

[English](../getting-started.md) · [문서 목록](../../README.ko.md#문서)

MovieSites는 원본을 패키지에 복사하지 않고 외부 폴더에서 제공하는 단일 애플리케이션입니다.

## 실행 방식 선택

- Docker: Linux 컨테이너 엔진을 사용합니다. Java·FFmpeg·ffprobe가 이미지에 포함됩니다. [Docker 안내](docker.md)를 참고하세요.
- JAR: 서버에 JDK 25와 FFmpeg/ffprobe를 설치합니다. 저장소의 Gradle Wrapper가 고정된 빌드 도구를 내려받습니다. 첫 빌드에는 Gradle 배포 서버·Plugin Portal·Maven Central 연결이 필요합니다.

네이티브 서버는 Linux/macOS를 대상으로 합니다. 실제 원본 저장소는 Java `SecureDirectoryStream`을 지원해야 합니다. 모든 마운트가 이를 제공하지는 않습니다. Windows 호스트에서는 Linux 컨테이너와 Docker Desktop 경로 공유를 확인하세요. `gradlew.bat`은 표준 Windows 빌드 진입점이며 네이티브 Windows 저장소 지원을 보증하지 않습니다.

## JAR 빌드와 실행

```bash
git clone https://github.com/JunSungKang/movie-sites.git
cd movie-sites
./gradlew clean build
mkdir -p movie runtime
MOVIESITES_MEDIA_ROOT="$PWD/movie" \
MOVIESITES_DATA_ROOT="$PWD/runtime" \
java -jar build/libs/moviesites.jar
```

[localhost:8080](http://localhost:8080)을 엽니다. 원본과 생성 데이터는 서로 겹치지 않는 별도 디렉토리여야 하며 원본 디렉토리는 미리 존재해야 합니다. 운영에서는 절대 경로를 권장합니다. 상대 경로는 프로세스의 작업 디렉토리를 기준으로 합니다. 데이터 루트 하나는 인스턴스 하나만 사용합니다.

## 첫 영상 추가

제공 권한이 있는 영상을 `movie/Tutorials/Introduction.mp4`처럼 저장하고 해당 컬렉션을 엽니다. 확장자를 제외한 파일명이 제목이 됩니다. 추가·이동 후 목록을 새로고침하세요. 새 clone은 `movie/.gitkeep`만 포함하므로 빈 목록이 정상입니다.

지원 조건에 맞는 MP4/H.264/yuv420p/AAC는 원본으로 재생하며, 다른 디코딩 가능한 입력은 FFmpeg 재생본 준비가 필요할 수 있습니다. 첫 변환은 시간이 걸립니다. [미디어 안내](media-library.md)와 [문제 해결](troubleshooting.md)을 참고하세요.

## 다음 단계

[설정](configuration.md)에서 테마와 제한을 확인하세요. 현재 서비스는 로그인 없이 읽기 전용으로 제공하므로 접근 가능한 누구나 원본 루트의 지원 영상을 볼 수 있습니다. 공개 전 접근 정책을 구성하세요. 실제 검사 범위는 [검증 기록](verification.md)에 있습니다.
