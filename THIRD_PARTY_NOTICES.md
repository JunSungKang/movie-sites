# Third-party notices

MovieSites source is AGPL-3.0-only. User videos and private runtime data are not licensed or distributed as application source.

- Spring Boot and Spring Framework: Apache License 2.0. Dependency license files remain inside the packaged dependency JARs.
- Jackson: Apache License 2.0. See the notices in the packaged libraries.
- Apache Maven Wrapper: Apache License 2.0. Source: https://github.com/apache/maven-wrapper
- Eclipse Temurin / OpenJDK: GPLv2 with the Classpath Exception, with component-specific notices in the runtime distribution. Source: https://github.com/adoptium/temurin-build
- The container installs Ubuntu's FFmpeg packages, including GPL components such as libx264. Their copyright, license and source-package information is retained under /usr/share/doc in the image. No --enable-nonfree build is selected. Source: https://ffmpeg.org/download.html and https://packages.ubuntu.com/noble/ffmpeg
- Container base-image and Ubuntu package notices remain in the image. FFmpeg is invoked as a separate process. Redistribution must satisfy the actual selected binary packages' license and corresponding-source obligations; the application license does not replace those terms.

The frontend uses original HTML, CSS, JavaScript and icon drawings; it has no external UI library, font service or analytics dependency. Maven test dependencies are not part of the runtime application.
