// backend/src/main/kotlin/com/todo/model/User.kt — пользователь: как хранится в БД и что отдаём в ответе.
// Здесь нужно: поля пользователя и отдельные структуры для запроса регистрации/входа и для ответа
// (в ответе пароль и его хеш быть не должно).
//
// СИНХРОНИЗАЦИЯ С ФРОНТОМ (frontend/src/types.ts):
//   types.ts: User = { id: number; name: string; email: string }
//   здесь:    UserResponse(id, name, email) — совпадает поле в поле, порядок как в types.ts.
//   Следствия: created_at пользователя НЕ отдаём (во фронтовом типе его нет), а `name` в ответе
//   всегда строка — если имя не указали, вернём "" (в types.ts это `name: string`, не nullable).
//   Тела запросов: RegisterRequest { name, email, password }, LoginRequest { email, password } — как в types.ts.

package com.todo.model

// ВЕРСИЯ EXPOSED. Импорты ниже — для Exposed 0.x (`org.jetbrains.exposed.sql.*`), самой
// распространённой версии. Если участник 2 в backend/build.gradle.kts возьмёт Exposed 1.0,
// пакеты там переименованы — замени в этом файле и в repository/UserRepository.kt:
//   org.jetbrains.exposed.sql.Table        -> org.jetbrains.exposed.v1.core.Table
//   org.jetbrains.exposed.sql.ResultRow    -> org.jetbrains.exposed.v1.core.ResultRow
//   org.jetbrains.exposed.sql.selectAll    -> org.jetbrains.exposed.v1.jdbc.selectAll
//   org.jetbrains.exposed.sql.insert       -> org.jetbrains.exposed.v1.jdbc.insert
//   org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
//       -> org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

// ---------------------------------------------------------------------------
// ВАЖНО ДЛЯ УЧАСТНИКА 2 (сборка/БД).
// Таблица `users` объявлена здесь (в зоне Миши), потому что она относится к модулю авторизации.
// В файле database/Tables.kt таблицу пользователей повторно объявлять НЕ нужно — импортируй
// `com.todo.model.Users` и используй её для внешнего ключа задачи:
//     val userId = integer("user_id").references(Users.id)
// В DatabaseFactory.kt таблицу нужно создать вместе с остальными:
//     SchemaUtils.create(Users, Tasks)
//
// Зависимости, которые нужны этому модулю (добавляет участник 2 в backend/build.gradle.kts):
//   implementation("io.ktor:ktor-server-auth-jwt:<версия Ktor>") // внутри уже есть com.auth0:java-jwt
//   implementation("org.mindrot:jbcrypt:0.4")                    // хеширование паролей
//
// ОБЩИЕ ТИПЫ ОТВЕТОВ ТОЖЕ ЗДЕСЬ: `ErrorResponse` и `MessageResponse` объявлены в этом файле,
// в пакете com.todo.model. Повторно их объявлять (например, в model/Task.kt) НЕ нужно — импортируй:
//   import com.todo.model.ErrorResponse      // {"message": "..."} — единый формат ошибки
//   import com.todo.model.MessageResponse    // {"message": "..."} — короткий успешный ответ
// ---------------------------------------------------------------------------

/**
 * Таблица пользователей.
 * email уникальный, пароль хранится ТОЛЬКО в виде хеша (колонка `password_hash`).
 * `created_at` храним как epoch-миллисекунды (UTC) — так модулю не нужна библиотека типов дат.
 * Это служебное поле БД: в JSON пользователя (тип `User` в frontend/src/types.ts) даты не входят.
 */
object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255).nullable()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
    // PrimaryKey — вложенный класс Table, отдельный импорт ему не нужен.
    // В очень свежих версиях Exposed конструктор принимает массив: PrimaryKey(arrayOf(id)).
}

/** Строка таблицы `users` (внутренняя модель для репозитория). В JSON не уходит напрямую. */
data class User(
    val id: Int,
    val email: String,
    val name: String?,          // в БД может быть NULL: имя при регистрации необязательно
    val passwordHash: String,
    val createdAtMillis: Long,  // служебное поле БД, фронту не отдаём (см. UserResponse)
)

/**
 * То, что отдаём фронтенду — ровно тип `User` из frontend/src/types.ts: `{ id, name, email }`.
 * Пароля и его хеша тут быть не должно (в types.ts их тоже нет).
 * `name` — обязательная строка: в types.ts это `name: string`, поэтому NULL из БД отдаём как "".
 */
@Serializable
data class UserResponse(
    val id: Int,
    val name: String,
    val email: String,
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id,
            name = user.name ?: "",
            email = user.email,
        )
    }
}

// ---- запросы авторизации (то, что присылает фронт) ----

/**
 * POST /api/auth/register — соответствует `RegisterRequest` из frontend/src/types.ts.
 * `name` объявлен необязательным намеренно: фронт (по типам) присылает его всегда, но бэк не должен
 * отвечать 400, если поля нет — тогда имя сохраняется как NULL, а в ответе придёт "".
 */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null,
)

/** POST /api/auth/login. */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

// ---- ответы авторизации ----

/**
 * Ответ регистрации и входа: `{ "token": "...", "user": { ... } }`, где `user` — тип `User` из types.ts.
 * В frontend/src/types.ts такого типа пока нет — участник 1 добавляет его туда (см. docs/api.md, «Сверка»).
 */
@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse,
)

/** Единый формат ошибки всего бэкенда. */
@Serializable
data class ErrorResponse(
    val message: String,
)

/** Короткий текстовый ответ (выход). */
@Serializable
data class MessageResponse(
    val message: String,
)

// created_at пользователя в JSON не отдаём: в типе `User` из frontend/src/types.ts дат нет
// (для задач конвертацию epoch -> ISO-8601 делает участник 2 в model/Task.kt).
