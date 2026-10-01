// backend/src/main/kotlin/com/todo/routes/AuthRoutes.kt — адреса авторизации (/api/auth).
// Здесь нужно: регистрация, вход и выход — принять данные, проверить их, вызвать сервис авторизации
// и вернуть корректный код и тело ответа.

package com.todo.routes

import com.todo.model.ErrorResponse
import com.todo.model.LoginRequest
import com.todo.model.MessageResponse
import com.todo.model.RegisterRequest
import com.todo.service.AuthException
import com.todo.service.AuthService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

// ---------------------------------------------------------------------------
// КАК ПОДКЛЮЧИТЬ (участник 2, Plugins.kt):
//   routing {
//       authRoutes()   // <- /api/auth/register, /api/auth/login, /api/auth/logout
//       taskRoutes()   // <- участник 2
//   }
// Нужен установленный ContentNegotiation с JSON: маршруты отдают объекты, а Ktor их сериализует.
// ---------------------------------------------------------------------------

/** JSON для чтения тел запросов. Лишние поля игнорируем — фронт может прислать больше, чем надо. */
private val AuthJson = Json { ignoreUnknownKeys = true }

/** Логгер через slf4j — не зависит от версии Ktor, его же настраивает logback.xml участника 2. */
private val logger = LoggerFactory.getLogger("com.todo.routes.AuthRoutes")

/** Один экземпляр сервиса на всё приложение — состояние он не хранит, можно переиспользовать. */
private val authService = AuthService()

/**
 * Маршруты авторизации. Все ошибки отдаём единым форматом `{"message": "..."}`:
 * 400 — неверные данные, 401 — нет/плохой пароль или токен, 409 — email занят.
 */
fun Route.authRoutes() {
    route("/api/auth") {

        // POST /api/auth/register  { email, password, name? } -> 201 { token, user }
        post("/register") {
            call.handleAuthErrors {
                val request = call.receiveJson { AuthJson.decodeFromString<RegisterRequest>(it) }
                call.respond(HttpStatusCode.Created, authService.register(request))
            }
        }

        // POST /api/auth/login  { email, password } -> 200 { token, user }
        post("/login") {
            call.handleAuthErrors {
                val request = call.receiveJson { AuthJson.decodeFromString<LoginRequest>(it) }
                call.respond(HttpStatusCode.OK, authService.login(request))
            }
        }

        // POST /api/auth/logout  (Authorization: Bearer <токен>) -> 200 { message }
        post("/logout") {
            call.handleAuthErrors {
                authService.logout(call.request.headers[HttpHeaders.Authorization])
                call.respond(HttpStatusCode.OK, MessageResponse("Выход выполнен"))
            }
        }
    }
}

/** Общая обработка ошибок авторизации, чтобы каждый маршрут не повторял try/catch. */
private suspend fun ApplicationCall.handleAuthErrors(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: AuthException) {
        respond(e.status, ErrorResponse(e.message))
    } catch (e: CancellationException) {
        throw e // отмену корутины (например, клиент отключился) не превращаем в 500
    } catch (e: Exception) {
        // Неожиданное — не отдаём пользователю белый экран: пишем в лог и отвечаем понятным текстом.
        logger.error("Непредвиденная ошибка в модуле авторизации", e)
        respond(HttpStatusCode.InternalServerError, ErrorResponse("Внутренняя ошибка сервера"))
    }
}

/** Прочитать тело запроса и разобрать как JSON; битый JSON или нехватка полей — это 400, а не 500. */
private suspend fun <T> ApplicationCall.receiveJson(decode: (String) -> T): T {
    val body = receiveText()
    return try {
        decode(body)
    } catch (e: Exception) {
        throw AuthException(HttpStatusCode.BadRequest, "Некорректный JSON или не хватает обязательных полей")
    }
}

