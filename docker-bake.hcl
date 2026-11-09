variable "JAVA_BUILD_IMAGE" { default = "movie-kids-java-build:25-ubuntu26.04" }
variable "JAVA_RUNTIME_IMAGE" { default = "movie-kids-java-runtime:25-ubuntu26.04" }
variable "MOVIESITES_IMAGE" { default = "movie-kids:ubuntu26.04" }
group "default" { targets = ["movie-kids"] }
group "fallback-bases" { targets = ["java-build", "java-runtime"] }
target "java-build" {
  context = "."
  dockerfile = "docker/java-base.Dockerfile"
  target = "java-build"
  tags = ["movie-kids-java-build:25-ubuntu26.04"]
}
target "java-runtime" {
  context = "."
  dockerfile = "docker/java-base.Dockerfile"
  target = "java-runtime"
  tags = ["movie-kids-java-runtime:25-ubuntu26.04"]
}
target "movie-kids" {
  context = "."
  dockerfile = "Dockerfile"
  target = "runtime"
  tags = [MOVIESITES_IMAGE]
  args = {
    JAVA_BUILD_IMAGE = JAVA_BUILD_IMAGE
    JAVA_RUNTIME_IMAGE = JAVA_RUNTIME_IMAGE
  }
}
