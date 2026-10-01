// backend/src/main/kotlin/com/todo/security/JwtConfig.kt — токен доступа (JWT).
// Здесь нужно: секрет и срок жизни токена из переменных окружения, создание токена для пользователя,
// проверка присланного токена и получение из него id пользователя.

package com.todo.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.Date

// ---------------------------------------------------------------------------
// КАК ЭТИМ ПОЛЬЗУЕТСЯ УЧАСТНИК 2 (Plugins.kt) — включение проверки токена:
//
//   install(Authentication) {
//       jwt(JwtConfig.NAME) {
//           realm = JwtConfig.realm
//           verifier(JwtConfig.verifier)
//           validate { credential ->
//               if (credential.payload.getClaim(JwtConfig.USER_ID_CLAIM).asInt() != null)
//                   JWTPrincipal(credential.payload) else null
//           }
//       }
//   }
//   // в маршрутах задач: authenticate(JwtConfig.NAME) { ... }
//   // id текущего пользователя: call.principal<JWTPrincipal>()
//   //     ?.payload?.getClaim(JwtConfig.USER_ID_CLAIM)?.asInt()
//
// Переменные окружения (их кладёт участник 5 в .env, см. docs/api.md):
//   JWT_SECRET           — обязательный секрет подписи (в репозиторий не попадает!)
//   JWT_ISSUER           — кто выдал токен (по умолчанию todo-app)
//   JWT_REALM            — realm для Ktor (по умолчанию todo-app)
//   JWT_EXPIRES_MINUTES  — срок жизни токена в минутах (по умолчанию 1440 = сутки)
// ---------------------------------------------------------------------------

/** Создание и проверка JWT. Алгоритм — HS256, подпись секретом из переменных окружения. */
object JwtConfig {

    /** Имя провайдера аутентификации для Ktor: `authenticate(JwtConfig.NAME) { ... }`. */
    const val NAME = "auth-jwt"

    /**
     * Имя claim с id пользователя. Именно это поле читает бэк, чтобы понять «кто пришёл»:
     * оно же становится `user_id` в задачах (Task.user_id в frontend/src/types.ts).
     */
    const val USER_ID_CLAIM = "userId"

    /** Имя claim с email — удобно для отладки, id всё равно остаётся главным. */
    const val EMAIL_CLAIM = "email"

    private const val ENV_SECRET = "JWT_SECRET"
    private const val ENV_ISSUER = "JWT_ISSUER"
    private const val ENV_REALM = "JWT_REALM"
    private const val ENV_EXPIRES_MINUTES = "JWT_EXPIRES_MINUTES"

    private const val DEFAULT_ISSUER = "todo-app"
    private const val DEFAULT_REALM = "todo-app"
    private const val DEFAULT_EXPIRES_MINUTES = 1440L

    /** realm — по нему Ktor формирует заголовок WWW-Authenticate при 401. */
    val realm: String = env(ENV_REALM) ?: DEFAULT_REALM

    /** Секрет подписи. Обязателен: пустой секрет — понятная ошибка, а не «тихо работает». */
    val secret: String = env(ENV_SECRET) ?: error(
        "Не задан секрет токена. Задай переменную окружения $ENV_SECRET " +
            "(локально: set $ENV_SECRET=... ; в Docker — в .env / docker-compose).",
    )

    private val issuer: String = env(ENV_ISSUER) ?: DEFAULT_ISSUER

    private val expiresMinutes: Long =
        env(ENV_EXPIRES_MINUTES)?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_EXPIRES_MINUTES

    /** Алгоритм подписи (HS256). */
    private val algorithm: Algorithm by lazy { Algorithm.HMAC256(secret) }

    /** Готовый проверяльщик токенов для плагина Ktor: `verifier(JwtConfig.verifier)`. */
    val verifier: JWTVerifier by lazy {
        JWT.require(algorithm).withIssuer(issuer).build()
    }

    /** Выдать токен пользователю: id кладём и в `sub`, и в claim `userId`. */
    fun generateToken(userId: Int, email: String): String {
        val now = Date()
        val expiresAt = Date(now.time + expiresMinutes * 60_000L)
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(userId.toString())
            .withClaim(USER_ID_CLAIM, userId)
            .withClaim(EMAIL_CLAIM, email)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    /**
     * Проверить токен и вернуть id пользователя.
     * Бросает JWTVerificationException, если токен битый, просрочен или подписан чужим секретом.
     * Проверять результат безопасно через [verifyOrNull] — там исключение не выходит наружу.
     */
    fun verifyAndGetUserId(token: String): Int {
        val decoded: DecodedJWT = verifier.verify(token)
        return decoded.getClaim(USER_ID_CLAIM).asInt()
            ?: decoded.subject?.toIntOrNull()
            ?: throw IllegalArgumentException("В токене нет id пользователя")
    }

    /** То же, но не бросает исключение: null — токен не прошёл проверку. */
    fun verifyOrNull(token: String): Int? = runCatching { verifyAndGetUserId(token) }.getOrNull()

    /**
     * Достать id пользователя из заголовка `Authorization: Bearer <токен>`.
     * null — заголовка нет, он не в формате Bearer или токен недействителен.
     */
    fun userIdFromAuthorizationHeader(header: String?): Int? {
        val token = bearerToken(header) ?: return null
        return verifyOrNull(token)
    }

    /** «Bearer abc.def.ghi» -> «abc.def.ghi». */
    fun bearerToken(header: String?): String? {
        val raw = header?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val parts = raw.split(' ', limit = 2)
        if (parts.size != 2) return null
        if (!parts[0].equals("Bearer", ignoreCase = true)) return null
        return parts[1].trim().takeIf { it.isNotEmpty() }
    }

    /** Переменная окружения, а если её нет — то же имя как свойство JVM (удобно для тестов). */
    private fun env(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
}

