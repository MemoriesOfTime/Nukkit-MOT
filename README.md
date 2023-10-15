![Nukkit-MOT](.github/images/banner.png)  

## Introduction
Nukkit-MOT is a special version of [Nukkit](https://github.com/CloudburstMC/Nukkit) Minecraft Bedrock Edition server software.  
It is developed based on the last open source version of [NukkitPetteriM1Edition](https://github.com/PetteriM1/NukkitPetteriM1Edition)

note: if you need higher version features, please use [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX).

### What's new in Nukkit-MOT?
1. Support for 1.2 – 1.20.30 version (you can set the minimum protocol in the config)
2. Supports most entities with AI
3. Support for the nether world and The Еnd
4. Generation of dungeons and caves
5. Support for vanilla commands

## How to install?
1. Install java 17 or higher
3. Download the .jar file from the links below
4. Write a command to run: `java -jar file.jar` (change `file` to the name of the file you downloaded)

## Creating Plugins
1. Add our Maven repository
```xml
<repositories>
    <repository>
        <id>repo-lanink-cn</id>
        <url>https://repo.lanink.cn/repository/maven-public/</url>
    </repository>
</repositories>
```

2. Add Nukkit-MOT to your dependencies
```xml
<dependencies>
    <dependency>
        <groupId>cn.nukkit</groupId>
        <artifactId>Nukkit</artifactId>
        <version>MOT-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

* __[Click here to see an example plugin for Nukkit](https://github.com/Nukkit/ExamplePlugin)__

## Links
- __🌐 Download: [Jenkins](https://ci.lanink.cn/job/Nukkit-MOT/) / [GitHub Actions](https://github.com/MemoriesOfTime/Nukkit-MOT/actions/workflows/maven.yml?query=branch%3Amaster)__
- __💬 [Discord](https://discord.gg/pJjQDQC)__
- __🔌 [Nukkit Plugins](https://cloudburstmc.org/resources/categories/nukkit-plugins.1/)__
- __🐞 [Report a Bug](https://github.com/MemoriesOfTime/Nukkit-MOT/issues/new/choose)__

## Credits
[Nukkit](https://github.com/CloudburstMC/Nukkit)  
[NukkitPetteriM1Edition](https://github.com/PetteriM1/NukkitPetteriM1Edition)  
[PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX)

This project is based on [Nukkit](https://github.com/CloudburstMC/Nukkit), so you should abide by [Nukkit](https://github.com/CloudburstMC/Nukkit)'s License
