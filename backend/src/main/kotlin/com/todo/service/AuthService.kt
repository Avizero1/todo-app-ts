// backend/src/main/kotlin/com/todo/service/AuthService.kt — логика регистрации, входа и выхода.
// Здесь нужно: регистрация (проверить, что email свободен, сохранить пользователя с хешем пароля, выдать токен),
// вход (сверить пароль с хешем, выдать токен) и выход. В ответе — только безопасные данные пользователя.

package com.todo.service

import com.todo.model.AuthResponse
import com.todo.model.LoginRequest
import com.todo.model.RegisterRequest
import com.todo.model.User
import com.todo.model.UserResponse
import com.todo.repository.UserRepository
import com.todo.security.JwtConfig
import io.ktor.http.HttpStatusCode
import org.mindrot.jbcrypt.BCrypt

/**
 * Ошибка авторизации: несёт код ответа и понятный текст для пользователя.
 * Маршруты ловят её и отдают `{"message": "..."}` с нужным кодом (см. AuthRoutes).
 */
class AuthException(
    val status: HttpStatusCode,
    override val message: String,
) : RuntimeException(message)

/**
 * Логика авторизации. Пароль в открытом виде нигде не хранится: в БД попадает только BCrypt-хеш.
 * Ни одна функция не возвращает хеш пароля наружу — только UserResponse.
 */
class AuthService(
    private val users: UserRepository = UserRepository,
) {

    /** Регистрация: email свободен -> сохраняем пользователя с хешем пароля -> выдаём токен. */
    suspend fun register(request: RegisterRequest): AuthResponse {
        val email = normalizeEmail(request.email)
        validateEmail(email)
        validatePassword(request.password)
        val name = normalizeName(request.name)
        validateName(name)

        if (users.existsByEmail(email)) {
            throw AuthException(HttpStatusCode.Conflict, EMAIL_TAKEN)
        }

        val passwordHash = hashPassword(request.password)

        val created = try {
            users.create(email = email, name = name, passwordHash = passwordHash)
        } catch (e: Exception) {
            // Гонка: между проверкой и вставкой email занял кто-то другой (уникальный индекс в БД).
            // Проверяем ещё раз: если email уже есть — это 409, любую другую причину пробрасываем дальше.
            val duplicateEmail = runCatching { users.existsByEmail(email) }.getOrDefault(false)
            if (duplicateEmail) throw AuthException(HttpStatusCode.Conflict, EMAIL_TAKEN)
            throw e
        }

        return created.toAuthResponse()
    }

    /** Вход: находим пользователя, сверяем пароль с хешем, выдаём токен. */
    suspend fun login(request: LoginRequest): AuthResponse {
        val email = normalizeEmail(request.email)
        val password = request.password

        if (email.isEmpty()) {
            throw AuthException(HttpStatusCode.BadRequest, "Email обязателен")
        }
        if (password.isEmpty()) {
            throw AuthException(HttpStatusCode.BadRequest, "Пароль обязателен")
        }

        val user = users.findByEmail(email)
        // Один и тот же текст для «нет такого email» и «неверный пароль» — не подсказываем,
        // какие email зарегистрированы.
        if (user == null || !matchesPassword(password, user.passwordHash)) {
            throw AuthException(HttpStatusCode.Unauthorized, "Неверный email или пароль")
        }

        return user.toAuthResponse()
    }

    /**
     * Выход. Токен у нас без серверного состояния, поэтому «выход» — это проверка, что токен ещё
     * действителен и его владелец существует; на клиенте токен после этого удаляется.
     */
    suspend fun logout(authorizationHeader: String?): UserResponse {
        val userId = JwtConfig.userIdFromAuthorizationHeader(authorizationHeader)
            ?: throw AuthException(HttpStatusCode.Unauthorized, "Нужен действительный токен")
        val user = users.findById(userId)
            ?: throw AuthException(HttpStatusCode.Unauthorized, "Пользователя с таким токеном больше нет")
        return UserResponse.from(user)
    }

    // ---- вспомогательное ----

    private fun User.toAuthResponse() = AuthResponse(
        token = JwtConfig.generateToken(userId = id, email = email),
        user = UserResponse.from(this),
    )

    private fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(COST))

    private fun matchesPassword(password: String, hash: String): Boolean =
        runCatching { BCrypt.checkpw(password, hash) }.getOrDefault(false) // битый хеш в БД — не 500, а «не подошёл»

    private fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    private fun normalizeName(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun validateEmail(email: String) {
        if (email.isEmpty()) throw AuthException(HttpStatusCode.BadRequest, "Email обязателен")
        if (email.length > MAX_EMAIL) {
            throw AuthException(HttpStatusCode.BadRequest, "Email слишком длинный (максимум $MAX_EMAIL символов)")
        }
        if (!EMAIL_REGEX.matches(email)) throw AuthException(HttpStatusCode.BadRequest, "Некорректный email")
    }

    private fun validateName(name: String?) {
        if (name != null && name.length > MAX_NAME) {
            throw AuthException(HttpStatusCode.BadRequest, "Имя слишком длинное (максимум $MAX_NAME символов)")
        }
    }

    private fun validatePassword(password: String) {
        if (password.isBlank()) throw AuthException(HttpStatusCode.BadRequest, "Пароль обязателен")
        if (password.length < MIN_PASSWORD) {
            throw AuthException(HttpStatusCode.BadRequest, "Пароль должен быть не короче $MIN_PASSWORD символов")
        }
        if (password.length > MAX_PASSWORD) {
            throw AuthException(HttpStatusCode.BadRequest, "Пароль слишком длинный (максимум $MAX_PASSWORD символов)")
        }
    }

    companion object {
        /** Стоимость BCrypt: 12 — разумный компромисс «медленно для атакующего, быстро для сервера». */
        private const val COST = 12

        /** BCrypt использует не больше 72 байт пароля, поэтому длиннее не принимаем. */
        private const val MAX_PASSWORD = 72
        private const val MIN_PASSWORD = 6

        /** Колонка email в БД — varchar(255). */
        private const val MAX_EMAIL = 255

        /** Колонка name в БД — varchar(255). */
        private const val MAX_NAME = 255

        private const val EMAIL_TAKEN = "Пользователь с таким email уже зарегистрирован"

        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

