// backend/src/main/kotlin/com/todo/repository/UserRepository.kt — запросы к таблице пользователей.
// Здесь нужно: найти пользователя по email, найти по id, создать нового пользователя.
// Других обращений к таблице пользователей в проекте быть не должно — только здесь.

package com.todo.repository

import com.todo.model.User
import com.todo.model.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
// Импорты выше — для Exposed 0.x. Для Exposed 1.0 замени их по таблице в model/User.kt
// (Table/ResultRow -> v1.core.*, selectAll/insert/newSuspendedTransaction -> v1.jdbc.*).

/**
 * Все обращения к таблице `users` живут здесь.
 * Функции `suspend`, потому что маршруты Ktor асинхронные, а обращение к БД — через
 * `newSuspendedTransaction` (Exposed). Подключение к БД настраивает участник 2 в DatabaseFactory.
 */
object UserRepository {

    /** Найти пользователя по email (email уже приведён к нижнему регистру сервисом). */
    suspend fun findByEmail(email: String): User? = newSuspendedTransaction {
        Users.selectAll()
            .where { Users.email eq email }
            .limit(1)
            .firstOrNull()
            ?.toUser()
    }

    /** Найти пользователя по id. Нужно, например, чтобы проверить, что токен живой. */
    suspend fun findById(id: Int): User? = newSuspendedTransaction {
        Users.selectAll()
            .where { Users.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toUser()
    }

    /** Есть ли уже пользователь с таким email (проверка занятости при регистрации). */
    suspend fun existsByEmail(email: String): Boolean = newSuspendedTransaction {
        !Users.selectAll().where { Users.email eq email }.limit(1).empty()
    }

    /** Создать пользователя. Пароль сюда приходит уже в виде хеша. */
    suspend fun create(
        email: String,
        name: String?,
        passwordHash: String,
        createdAtMillis: Long = System.currentTimeMillis(),
    ): User = newSuspendedTransaction {
        val newId = Users.insert {
            it[Users.email] = email
            it[Users.name] = name
            it[Users.passwordHash] = passwordHash
            it[Users.createdAt] = createdAtMillis
        } get Users.id

        User(
            id = newId,
            email = email,
            name = name,
            passwordHash = passwordHash,
            createdAtMillis = createdAtMillis,
        )
    }

    private fun ResultRow.toUser() = User(
        id = this[Users.id],
        email = this[Users.email],
        name = this[Users.name],
        passwordHash = this[Users.passwordHash],
        createdAtMillis = this[Users.createdAt],
    )
}

