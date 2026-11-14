variable "JAVA_BUILD_IMAGE" { default = "moviesites-java-build:25-ubuntu26.04" }
variable "JAVA_RUNTIME_IMAGE" { default = "moviesites-java-runtime:25-ubuntu26.04" }
variable "MOVIESITES_IMAGE" { default = "moviesites:ubuntu26.04" }
group "default" { targets = ["moviesites"] }
group "fallback-bases" { targets = ["java-build", "java-runtime"] }
target "java-build" {
  context = "."
  dockerfile = "docker/java-base.Dockerfile"
  target = "java-build"
  tags = ["moviesites-java-build:25-ubuntu26.04"]
}
target "java-runtime" {
  context = "."
  dockerfile = "docker/java-base.Dockerfile"
  target = "java-runtime"
  tags = ["moviesites-java-runtime:25-ubuntu26.04"]
}
target "moviesites" {
  context = "."
  dockerfile = "Dockerfile"
  target = "runtime"
  tags = [MOVIESITES_IMAGE]
  args = {
    JAVA_BUILD_IMAGE = JAVA_BUILD_IMAGE
    JAVA_RUNTIME_IMAGE = JAVA_RUNTIME_IMAGE
  }
}
