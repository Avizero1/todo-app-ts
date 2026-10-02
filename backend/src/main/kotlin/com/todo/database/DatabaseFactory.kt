// backend/src/main/kotlin/com/todo/database/DatabaseFactory.kt — подключение к PostgreSQL и подготовка таблиц.
//
// ОБЩАЯ ЗОНА — заполнено по ролям (roles-now.md, шаг «Миша»): строка создания таблиц
// `SchemaUtils.create(Users, Tasks)`. Пул соединений (HikariCP), чтение настроек из переменных
// окружения и закрытие пула — тоже здесь, чтобы файл был рабочим целиком.
//
// Как пользоваться (участник 2, Application.kt):
//     fun main() {
//         DatabaseFactory.init()              // подключение + создание таблиц
//         embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
//     }
// Настройки берутся из переменных окружения (список — в docs/handoff-misha.md и docs/api.md):
//   DB_URL (или DATABASE_URL) — готовый JDBC-адрес, имеет приоритет;
//   DB_HOST / DB_PORT / DB_NAME (или POSTGRES_DB) — если DB_URL не задан;
//   DB_USER (или POSTGRES_USER), DB_PASSWORD (или POSTGRES_PASSWORD), DB_MAX_POOL_SIZE.

package com.todo.database

import com.todo.model.Users
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

// Импорты выше — для Exposed 0.x (версия зафиксирована в backend/build.gradle.kts).
// Для Exposed 1.0 замени по таблице из model/User.kt (v1.core.* / v1.jdbc.*).

private val logger = LoggerFactory.getLogger("com.todo.database.DatabaseFactory")

/** Подключение к базе и создание таблиц. Один экземпляр на всё приложение — состояние не хранит. */
object DatabaseFactory {

    /** Активный пул соединений: нужен, чтобы аккуратно закрыть его при остановке приложения. */
    @Volatile
    private var dataSource: HikariDataSource? = null

    /**
     * Подключиться к PostgreSQL и создать таблицы `users` и `tasks`, если их ещё нет.
     * Вызывать один раз при старте приложения (см. шапку файла).
     */
    fun init() {
        if (dataSource != null) return // повторный вызов не создаёт второй пул

        logger.info("Подключаюсь к PostgreSQL: {}", jdbcUrl())
        val ds = HikariDataSource(hikariConfig())
        Database.connect(ds)
        dataSource = ds

        transaction {
            // users — таблица из модели Миши (model/User.kt); tasks — из database/Tables.kt (участник 2).
            //
            // ВНИМАНИЕ: пока участник 2 не объявит `Tasks` в database/Tables.kt, эта строка не скомпилируется:
            // заготовка Tables.kt пустая, и без маршрутов задач бэк всё равно не стартует. Это ожидаемо.
            SchemaUtils.create(Users, Tasks)
        }

        logger.info("Таблицы users и tasks готовы (созданы, если их не было)")
    }

    /** Закрыть пул соединений при остановке приложения (участник 2 — в Application.kt, если нужно). */
    fun close() {
        dataSource?.close()
        dataSource = null
    }

    // ------------------------------------------------------------------
    // настройка пула соединений
    // ------------------------------------------------------------------

    private fun hikariConfig(): HikariConfig = HikariConfig().apply {
        jdbcUrl = jdbcUrl()
        username = env("DB_USER") ?: env("POSTGRES_USER") ?: DEFAULT_USER
        password = env("DB_PASSWORD") ?: env("POSTGRES_PASSWORD") ?: DEFAULT_PASSWORD
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = env("DB_MAX_POOL_SIZE")?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_POOL_SIZE
        // autoCommit выключаем: транзакциями управляет Exposed (см. newSuspendedTransaction в репозиториях)
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    /**
     * Адрес базы. Приоритет:
     *   1) DB_URL или DATABASE_URL — готовый JDBC-адрес как есть;
     *   2) иначе собираем из DB_HOST / DB_PORT / DB_NAME (или POSTGRES_DB).
     */
    private fun jdbcUrl(): String =
        env("DB_URL")
            ?: env("DATABASE_URL")
            ?: "jdbc:postgresql://${env("DB_HOST") ?: DEFAULT_HOST}:" +
            "${env("DB_PORT") ?: DEFAULT_PORT}/${env("DB_NAME") ?: env("POSTGRES_DB") ?: DEFAULT_NAME}"

    /** Значение из переменной окружения, а если её нет — одноимённое свойство JVM (удобно в тестах). */
    private fun env(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

    private const val DEFAULT_HOST = "localhost"
    private const val DEFAULT_PORT = "5432"
    private const val DEFAULT_NAME = "todo"
    private const val DEFAULT_USER = "todo"
    private const val DEFAULT_POOL_SIZE = 10

    /**
     * Пароль по умолчанию пустой — настоящие секреты в код и репозиторий не попадают.
     * Локально задаётся переменной DB_PASSWORD, в Docker — через .env (участник 5).
     */
    private const val DEFAULT_PASSWORD = ""
}
