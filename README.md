<p align="center"><a href="https://www.nukkit-mot.com/"><img src="/.github/images/banner.png" alt="Nukkit-MOT"/></a></p>
<h3 align="center">Nukkit-MOT</h3>
<p align="center">
  <a href="https://www.gnu.org/licenses/lgpl-3.0.html"><img alt="License: LGPL v3" src="https://img.shields.io/github/license/MemoriesOfTime/Nukkit-MOT"></a>
    <a href="https://central.sonatype.com/artifact/com.nukkit-mot/nukkit-mot"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/com.nukkit-mot/nukkit-mot?label=maven-central"></a>
    <a href="https://motci.cn/job/Nukkit-MOT/job/master/"><img alt="Jenkins Build" src="https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fmotci.cn%2Fjob%2FNukkit-MOT%2Fjob%2Fmaster%2F&label=jenkins&logo=jenkins"></a>
    <a href="https://discord.gg/pJjQDQC"><img alt="Discord" src="https://img.shields.io/discord/710480168598372354?label=Discord&logo=discord"></a>
<p align="center">
  <a href="/README.md"><img alt="English" src="https://img.shields.io/badge/English-d9d9d9"></a>
  <a href="/docs/README_zh.md"><img alt="中文" src="https://img.shields.io/badge/中文-d9d9d9"></a>
</p>

------

## Introduction
Nukkit-MOT is a fork of [Nukkit](https://github.com/CloudburstMC/Nukkit) that provides multi-version support, compatibility with NetEase clients, and a well-established plugin ecosystem.

Only interested in newer versions? You might want to try [Lumi](https://github.com/KoshakMineDEV/Lumi) or [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX)

### What's new in Nukkit-MOT?
1. Support for 1.2 – 1.26.30 version (you can set the minimum protocol in the config)
2. Supports most entities with AI
3. Support for the nether world and The Еnd
4. Generation of dungeons and caves
5. Support for vanilla commands
6. Support for NetEase clients

## How to install?
1. Install java 17 or higher
2. Download the .jar file from the links below
3. Write a command to run: `java -jar Nukkit-MOT-SNAPSHOT.jar` (change `Nukkit-MOT-SNAPSHOT.jar` to the name of the file you downloaded)

## Links
- __🌐 Download: [Jenkins](https://motci.cn/job/Nukkit-MOT/) / [GitHub Actions](https://github.com/MemoriesOfTime/Nukkit-MOT/actions/workflows/maven.yml?query=branch%3Amaster)__
- __💬 Discuss: [Discord](https://discord.gg/pJjQDQC) / [QQ Group](https://jq.qq.com/?_wv=1027&k=5aIuYMH)__
- __🔌 Plugins: [Nukkit Forum](https://cloudburstmc.org/resources/categories/nukkit-plugins.1/) / [Nukkit-MOT Forum](https://bbs.nukkit-mot.com/resources/)__
- __🐞 [Report a Bug](https://github.com/MemoriesOfTime/Nukkit-MOT/issues/new/choose)__

## Maven
#### Repository:
```xml
<repositories>
    <!-- Release builds come from Maven Central; SNAPSHOT builds require the repo.lanink.cn repo -->
    <repository>
        <id>repo-lanink-cn</id>
        <url>https://repo.lanink.cn/repository/maven-public/</url>
    </repository>
</repositories>
```

#### Dependencies:
```xml
<!-- Release -->
<dependencies>
    <dependency>
        <groupId>com.nukkit-mot</groupId>
        <artifactId>nukkit-mot</artifactId>
        <version>1.26.30-R1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<!-- SNAPSHOT -->
<dependencies>
    <dependency>
        <groupId>cn.nukkit</groupId>
        <artifactId>Nukkit</artifactId>
        <version>MOT-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## Gradle
#### Repository:
```kts
repositories {
    mavenCentral()
    // SNAPSHOT builds require the repo.lanink.cn repo
    maven("https://repo.lanink.cn/repository/maven-public/")
} 
```

#### Dependencies:
```kts
// Release
dependencies {
    compileOnly("com.nukkit-mot:nukkit-mot:1.26.30-R1")
}

// SNAPSHOT
dependencies {
    compileOnly("cn.nukkit:Nukkit:MOT-SNAPSHOT")
}
```

## Credits

### Projects

<table>
  <tr>
    <td align="center" width="20%">
      <a href="https://github.com/CloudburstMC/Nukkit">
        <img src="https://raw.githubusercontent.com/CloudburstMC/Nukkit/master/.github/images/logo.png" width="50"><br>
        <b>Nukkit</b>
      </a>
    </td>
    <td align="center" width="20%">
      <a href="https://github.com/PetteriM1/NukkitPetteriM1Edition">
        <img src="https://avatars.githubusercontent.com/u/26197131?v=4" width="50"><br>
        <b>NukkitPetteriM1</b>
      </a>
    </td>
    <td align="center" width="20%">
      <a href="https://github.com/EaseCation/Nukkit">
        <img src="https://avatars.githubusercontent.com/u/20168691?v=4" width="50"><br>
        <b>EaseCation/Nukkit</b>
      </a>
    </td>
    <td align="center" width="20%">
      <a href="https://github.com/PowerNukkitX/PowerNukkitX">
        <img src="https://avatars.githubusercontent.com/u/99014792?s=200&v=4" width="50"><br>
        <b>PowerNukkitX</b>
      </a>
    </td>
    <td align="center" width="20%">
      <a href="https://github.com/KoshakMineDEV/Lumi">
        <img src="https://avatars.githubusercontent.com/u/122298065?s=200&v=4" width="50"><br>
        <b>Lumi</b>
      </a>
    </td>
  </tr>
</table>

### Sponsors

<table>
  <tr>
    <td width="80" align="center">
      <a href="https://www.yourkit.com/java/profiler/">
        <img src="https://www.yourkit.com/images/yklogo.png" width="60">
      </a>
    </td>
    <td>
      YourKit supports open source projects with innovative and intelligent tools
      for monitoring and profiling Java and .NET applications.<br>
      <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a> ·
      <a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a> ·
      <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>
    </td>
  </tr>
</table>

## Star History

If you like this project, please give us a Star ⭐

<a href="https://www.star-history.com/?repos=MemoriesOfTime%2FNukkit-MOT&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=MemoriesOfTime/Nukkit-MOT&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=MemoriesOfTime/Nukkit-MOT&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=MemoriesOfTime/Nukkit-MOT&type=date&legend=top-left" />
 </picture>
</a>

