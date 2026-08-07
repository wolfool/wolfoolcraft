plugins {
    java
}

group = "com.wolfool.workbench"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
}

/*
 * 숙련도는 Mastery 가 들고 있다. Maven 저장소에 없는 직접 만든 플러그인이라
 * 서버 플러그인 폴더의 jar 에 컴파일한다.
 */
val serverDir = (project.findProperty("serverDir") as String?)
    ?: "C:/MinecraftServer/QWER/servers/fgh"

fun locate(prefix: String): File? {
    file("libs").listFiles()?.firstOrNull {
        it.name.startsWith(prefix) && it.extension == "jar"
    }?.let { return it }
    return File("$serverDir/plugins").listFiles()?.firstOrNull {
        it.name.startsWith(prefix) && it.extension == "jar"
    }
}

val masteryJar = locate("Mastery")

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.momirealms:craft-engine-core:26.7")
    compileOnly("net.momirealms:craft-engine-bukkit:26.7")
    if (masteryJar != null) compileOnly(files(masteryJar))
}

tasks.named<JavaCompile>("compileJava") {
    doFirst {
        if (masteryJar == null) {
            throw GradleException(
                "Mastery jar 를 못 찾았다 ($serverDir/plugins). " +
                    "-PserverDir=<서버경로> 로 지정하거나 libs/ 에 직접 넣어라."
            )
        }
        logger.lifecycle("Mastery: ${masteryJar.name}")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
