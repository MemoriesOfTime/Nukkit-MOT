# syntax=docker/dockerfile:1

# 运行时 Java 主版本（构建阶段始终用 JDK 17，与 maven.compiler.target 一致）。
# Runtime Java major version (build stage always uses JDK 17, matching maven.compiler.target).
# 字节码是 Java 17，可跑在任何 ≥17 的 JRE 上，因此只需切换 runtime 镜像。
# Bytecode targets Java 17 and runs on any JRE ≥17, so only the runtime image needs to switch.
ARG JAVA_VERSION=17

# ---------- Build stage ----------
# 构建阶段：固定 JDK 17（pom.xml 中 maven.compiler.target=17）。
# Build stage: pinned to JDK 17 (pom.xml sets maven.compiler.target=17).
# 用更高版本 JDK 构建没有收益，反而会引入 annotation processor / shading 的环境差异；
# A newer build JDK brings no benefit and only adds annotation-processor / shading variance;
# 统一用 17 构建可保证不同 runtime 变体的 jar 除 git.properties 外逐字节一致。
# building with 17 keeps the jar byte-identical across runtime variants (modulo git.properties).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 可选注入 git 元数据：本地 docker build 没传时用 unknown 占位，
# CI 通过 build-args 传入真实分支与提交，让容器内 JAR 能识别自身版本/分支
# Optional git metadata injection: defaults to "unknown" for local docker build;
# CI passes real branch/commit via build-args so the in-container JAR can self-identify.
ARG GIT_BRANCH=unknown
ARG GIT_COMMIT=unknown

# 注意：不能用 dependency:go-offline 做依赖缓存层，
# netty native 分类器依赖 os.detected.* 属性，go-offline 会解析失败（普通 package 不受影响）
# Note: no dependency:go-offline cache layer — it fails to resolve netty native
# classifiers (${os.detected.*}); a plain package build is unaffected.
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# Docker build context 不含 .git（见 .dockerignore），git-commit-id-plugin 无法生成 git.properties。
# 这里手工写一份 stub 并注入到 shaded JAR 中，格式与插件生成的 properties 文件一致，
# 让 Nukkit.GIT_INFO 能读到 git.branch / git.commit.id.abbrev 等键。
# Build context excludes .git (see .dockerignore), so git-commit-id-plugin can't emit
# git.properties. We write a stub here and inject it into the shaded JAR, matching the
# plugin's properties format so Nukkit.GIT_INFO can read git.branch / git.commit.id.abbrev.
#
# 仅当传入的值是有效 git 标识时才写入对应键：本地 docker build 不传 build-args 时
# GIT_BRANCH/GIT_COMMIT 为 "unknown"，此时省略该键，Nukkit.getVersion()/getBranch()
# 会像无 git.properties 一样返回 "git-null" / "null"，行为与未注入时一致、不产生误导。
# Keys are written only when the arg is a valid git identifier: a local `docker build`
# without build-args leaves them as "unknown", so the keys are omitted and
# Nukkit.getVersion()/getBranch() fall back to "git-null"/"null" exactly as if no
# git.properties existed — no misleading partial state.
RUN set -eu; \
    mkdir -p /tmp/gitinfo && cd /tmp/gitinfo; \
    : > git.properties; \
    [ "$GIT_BRANCH" != "unknown" ] && printf 'git.branch=%s\n' "$GIT_BRANCH" >> git.properties || true; \
    abbrev="$(expr "$GIT_COMMIT" : '\([0-9a-fA-F]\{7\}\)')"; \
    if [ -n "$abbrev" ]; then \
        printf 'git.commit.id.abbrev=%s\n' "$abbrev" >> git.properties; \
        printf 'git.commit.id=%s\n' "$GIT_COMMIT" >> git.properties; \
        printf 'git.commit.id.describe=%s\n' "$GIT_COMMIT" >> git.properties; \
    fi; \
    jar -uf /build/target/Nukkit-MOT-SNAPSHOT.jar git.properties

# ---------- Runtime stage ----------
# 运行阶段：精简 JRE 镜像，版本由 ARG JAVA_VERSION 控制（默认 17，可传 25 等）。
# Runtime stage: slim JRE image; version controlled by ARG JAVA_VERSION (default 17, e.g. 25).
# ARG 重复声明是因为每个 FROM 开启新阶段，前面的 ARG 不跨阶段保留。
# ARG is re-declared because each FROM starts a fresh stage and prior ARGs don't carry over.
ARG JAVA_VERSION=17
FROM eclipse-temurin:${JAVA_VERSION}-jre

LABEL org.opencontainers.image.title="Nukkit-MOT" \
      org.opencontainers.image.description="Minecraft Bedrock Edition server" \
      org.opencontainers.image.source="https://github.com/MemoriesOfTime/Nukkit-MOT"
# image.version / image.revision 由 docker/metadata-action 在构建时注入

# 服务器数据目录（worlds/ plugins/ players/ server.properties 等均在此生成）
# Server data directory (worlds/, plugins/, players/, server.properties, etc.)
WORKDIR /data

COPY --from=build /build/target/Nukkit-MOT-SNAPSHOT.jar /opt/nukkit-mot/Nukkit-MOT-SNAPSHOT.jar

# 以非 root 用户运行（不显式指定 uid，避免与基础镜像已有用户冲突）
# Run as non-root user (no explicit uid to avoid clashing with base image users)
RUN useradd -r -m nukkit && chown -R nukkit:nukkit /data
USER nukkit

# Bedrock 默认端口 / Bedrock default port
EXPOSE 19132/udp

VOLUME ["/data"]

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/nukkit-mot/Nukkit-MOT-SNAPSHOT.jar"]
