# syntax=docker/dockerfile:1

# ---------- Build stage ----------
# 构建阶段：使用 Maven 编译 shaded JAR
# Build stage: compile the shaded JAR with Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 注意：不能用 dependency:go-offline 做依赖缓存层，
# netty native 分类器依赖 os.detected.* 属性，go-offline 会解析失败（普通 package 不受影响）
# Note: no dependency:go-offline cache layer — it fails to resolve netty native
# classifiers (${os.detected.*}); a plain package build is unaffected.
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Runtime stage ----------
# 运行阶段：精简 JRE 镜像
# Runtime stage: slim JRE image
FROM eclipse-temurin:17-jre

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
