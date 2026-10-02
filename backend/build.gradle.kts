// backend/build.gradle.kts — сборка Kotlin-бэкенда: версии, плагины, зависимости, запуск.
//
// ОБЩАЯ ЗОНА — заполнено по ролям (roles-now.md, шаг «Миша»): добавлены зависимости модуля
// авторизации (`ktor-server-auth-jwt`, `jbcrypt`) и остальные зависимости, без которых модуль
// не соберётся. Всё, что относится к сборке/БД/задачам, взято в минимально-рабочем виде,
// чтобы участник 2 мог просто дописать своё и не переделывать.
//
// Версии подобраны ПОД УЖЕ НАПИСАННЫЙ модуль авторизации (см. model/User.kt, repository/UserRepository.kt):
//   * Ktor 2.3.x    — маршруты, JSON, CORS, проверка JWT;
//   * Exposed 0.x   — импорты в User.kt/UserRepository.kt идут из `org.jetbrains.exposed.sql.*`;
//   * jbcrypt 0.4   — хеширование паролей в service/AuthService.kt.
// ВАЖНО: если участник 2 возьмёт Exposed 1.0, пакеты переименованы — таблица замен указана
// комментарием в model/User.kt (и в repository/UserRepository.kt). Файлы Миши правит только Миша.

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Версии держим в одном месте, чтобы участник 2 менял их в одном месте.
val ktorVersion = "2.3.12"      // Ktor + плагин io.ktor.plugin (версии совпадают)
val kotlinVersion = "1.9.25"    // Kotlin + плагин kotlinx.serialization
val exposedVersion = "0.50.1"   // Exposed 0.x — под импорты модуля авторизации
val hikariVersion = "5.1.0"     // пул соединений с PostgreSQL
val postgresVersion = "42.7.4"  // JDBC-драйвер PostgreSQL
val jbcryptVersion = "0.4"      // хеширование паролей (AuthService)
val logbackVersion = "1.4.14"   // логи (logback.xml)

plugins {
    kotlin("jvm") version "1.9.25"
    // kotlinx.serialization: @Serializable в model/User.kt (Миша) и model/Task.kt (участник 2)
    kotlin("plugin.serialization") version "1.9.25"
    // Плагин Ktor: даёт задачи `run`, `buildFatJar` (готовый jar для Docker) и `runFatJar`
    id("io.ktor.plugin") version "2.3.12"
    // Запуск приложения как обычной JVM-программы (`gradle run`)
    application
}

group = "com.todo"
version = "1.0.0"

repositories {
    mavenCentral()
}

application {
    // Точка входа — Application.kt (зона участника 2). Для файла Application.kt класс называется ApplicationKt.
    mainClass.set("com.todo.ApplicationKt")
}

ktor {
    fatJar {
        // Имя готового jar для Docker (участник 5): build/libs/todo-backend-all.jar
        archiveFileName.set("todo-backend-all.jar")
    }
}

dependencies {
    // --- Ktor: сервер и JSON ---
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    // --- Ktor: плагины сервера (нужны Plugins.kt участника 2) ---
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")         // фронт с другого адреса
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")  // логи запросов
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")  // единый ответ на ошибки

    // --- Проверка токена: нужно модулю авторизации Миши (security/JwtConfig.kt, Plugins.kt) ---
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")      // внутри уже com.auth0:java-jwt

    // --- БД: Exposed + пул соединений + драйвер PostgreSQL (database/*, repository/*) ---
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    // --- Хеширование паролей: нужно модулю авторизации Миши (service/AuthService.kt) ---
    implementation("org.mindrot:jbcrypt:$jbcryptVersion")

    // --- Логи: logback-classic читает resources/logback.xml ---
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // --- Тесты ---
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
}

// Бэкенд собираем под Java 17 (стандарт для Ktor 2.3). В Docker используем тот же JDK 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
