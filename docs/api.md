# API — договор между фронтом и бэком

Сначала договариваемся о формате данных, потом пишем код. Если формат меняется — правим этот файл.

Готовый контракт в формате OpenAPI 3.0 (Swagger): [swagger.yaml](swagger.yaml) — открой в Swagger UI
(например, editor.swagger.io → Import) или подключи в Postman. Обновлять его нужно вместе с этим файлом.

## Что нужно согласовать в первую очередь

- [x] точный формат ответа для регистрации и входа (какие поля, как называется токен)
- [x] точные поля задачи в JSON (в задании: `id`, `title`, `description`, `completed`, `created_at`, `updated_at`, `user_id`)
- [x] единый формат ошибки (например: `{ "message": "текст" }`) и когда какой код ответа
- [x] как передаётся токен (заголовок `Authorization`)

### Договорились (решения по авторизации)

| Вопрос | Решение |
|---|---|
| Поле с токеном | `token` (одно поле, в ответе `register` и `login`) |
| Формат ответа register/login | `{ "token": "...", "user": { ... } }` |
| Поля `user` | `id`, `name`, `email` — ровно как тип `User` в `frontend/src/types.ts`, **без** пароля, хеша и без `created_at` (во фронтовом типе даты не входят) |
| Пустое имя | в ответе всегда строка: если `name` не прислали, отдаём `""` (в `types.ts` это `name: string`, а не `string \| null`) |
| Как передаётся токен | заголовок `Authorization: Bearer <token>` |
| Формат ошибки | `{ "message": "текст на русском" }` — для всех ошибок бэкенда |
| `name` при регистрации | необязательно; пустое/пробелы → сохраняем `null`, а в ответе отдаём `""` |
| Регистр email | приводим к нижнему регистру и обрезаем пробелы (чтобы `A@b.c` и `a@b.c` были одним аккаунтом) |
| Занятый email | `409` (не 500 и не 400) |
| Неверный пароль при входе | `401` с текстом «Неверный email или пароль» (одинаковым для «нет email» и «не тот пароль») |
| Даты в JSON | строки ISO-8601 в UTC, например `2026-01-01T12:00:00Z` — это `created_at`/`updated_at` задачи; у пользователя дат в JSON нет |
| Пароль в БД | только BCrypt-хеш (`password_hash`), сам пароль не хранится и не логируется |

## Эндпоинты из задания

| Метод | Путь | Что приходит | Что делаем |
|---|---|---|---|
| POST | `/api/auth/register` | email, password, name (имя необязательно) | создаём пользователя, возвращаем токен |
| POST | `/api/auth/login` | email, password | проверяем данные, возвращаем токен |
| POST | `/api/auth/logout` | токен в заголовке | выход |
| GET | `/api/tasks` | токен в заголовке | список задач текущего пользователя |
| POST | `/api/tasks` | title (обязательно), description | создаём задачу текущему пользователю |
| PUT | `/api/tasks/:id` | title, description | редактируем свою задачу |
| PATCH | `/api/tasks/:id/complete` | ничего | переключаем `completed` у своей задачи |
| DELETE | `/api/tasks/:id` | токен в заголовке | удаляем свою задачу |

## Коды ответов

| Код | Когда |
|---|---|
| 200 / 201 | успех (201 — регистрация и создание задачи) |
| 400 | неверные данные (пустой заголовок, плохой email, короткий пароль, битый JSON) |
| 401 | нет токена или токен неверный/истёк; неверный email/пароль при входе |
| 403 | задача принадлежит другому пользователю |
| 404 | задачи с таким id нет |
| 409 | регистрация на уже занятый email |
| 500 | что-то неожиданное на сервере (в теле всё равно `{ "message": ... }`, не пустой экран) |

## Правила проверки данных при регистрации и входе

| Поле | Правило | Текст ошибки (400) |
|---|---|---|
| `email` | обязателен, до 255 символов, должен содержать `@` и домен | «Email обязателен» / «Некорректный email» |
| `password` | обязателен, от 6 до 72 символов | «Пароль должен быть не короче 6 символов» |
| `name` | необязательно, до 255 символов, пробелы обрезаются | «Имя слишком длинное (максимум 255 символов)» |

## Примеры запросов и ответов

### 1. Регистрация — `POST /api/auth/register`

Запрос:

```json
{ "name": "Миша", "email": "misha@example.com", "password": "secret123" }
```

Ответ `201`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsImVtYWlsIjoibWlzaGFAZXhhbXBsZS5jb20ifQ.signature",
  "user": { "id": 1, "name": "Миша", "email": "misha@example.com" }
}
```

Тот же email второй раз — `409`:

```json
{ "message": "Пользователь с таким email уже зарегистрирован" }
```

### 2. Вход — `POST /api/auth/login`

Запрос:

```json
{ "email": "misha@example.com", "password": "secret123" }
```

Ответ `200` — такой же, как у регистрации (`token` + `user`).

Неверный пароль или незарегистрированный email — `401`:

```json
{ "message": "Неверный email или пароль" }
```

### 3. Выход — `POST /api/auth/logout`

Запрос без тела, только заголовок:

```
Authorization: Bearer <token>
```

Ответ `200`:

```json
{ "message": "Выход выполнен" }
```

Без токена или с просроченным токеном — `401`:

```json
{ "message": "Нужен действительный токен" }
```

> JWT хранит состояние на клиенте: после `logout` фронт просто удаляет токен из хранилища.

### 4. Список задач — `GET /api/tasks`

Заголовок `Authorization: Bearer <token>`. Ответ `200` — только задачи этого пользователя:

```json
[
  {
    "id": 10,
    "title": "Сделать модуль авторизации",
    "description": "регистрация, вход, JWT",
    "completed": false,
    "created_at": "2026-01-01T12:05:00Z",
    "updated_at": "2026-01-01T12:05:00Z",
    "user_id": 1
  }
]
```

Без токена — `401`:

```json
{ "message": "Нужен действительный токен" }
```

### 5. Создание задачи — `POST /api/tasks`

Запрос (описание необязательно):

```json
{ "title": "Проверить сценарии", "description": "чужая задача должна давать 403" }
```

Ответ `201` — созданная задача, `user_id` берётся из токена (в теле его нет).
`description` в JSON-ответе — **всегда строка**: если поле не передали, отдаём `""` (в `types.ts` это `description: string`).

`title` пустой — `400`:

```json
{ "message": "Заголовок задачи обязателен" }
```

### 6. Редактирование / статус / удаление

- `PUT /api/tasks/:id` — своя задача: `200` с обновлённой задачей; чужая: `403 { "message": "Это задача другого пользователя" }`; нет такой: `404 { "message": "Задача не найдена" }`
- `PATCH /api/tasks/:id/complete` — переключает `completed`, ответ `200` с задачей
- `DELETE /api/tasks/:id` — `200 { "message": "Задача удалена" }`

### 7. Как бэк берёт пользователя из токена

Токен — JWT (HS256), внутри: `iss`, `sub` (= id пользователя), `userId`, `email`, `iat`, `exp`.
Бэк читает `userId` из токена и по нему работает с задачами — **id пользователя никогда не приходит в теле запроса**.

## Сверка с `frontend/src/types.ts`

`frontend/src/types.ts` — источник правды по форматам данных. Что уже совпадает с бэком:

| Тип во фронте | Что отдаёт / принимает бэк |
|---|---|
| `User` = `{ id: number; name: string; email: string }` | `{ "id": 1, "name": "Миша", "email": "misha@example.com" }` — поле в поле |
| `Task` = `{ id, title, description: string, completed, created_at, updated_at, user_id }` | то же; даты — строки ISO-8601, `user_id` берётся из токена (в теле запроса не приходит) |
| `RegisterRequest` = `{ name, email, password }` | принимаем `{ name, email, password }`; если `name` нет — не падаем, в ответе будет `"name": ""` |
| `LoginRequest` = `{ email, password }` | принимаем `{ email, password }` |
| `CreateTaskRequest` / `UpdateTaskRequest` = `{ title, description? }` | `title` обязателен (пустой — 400), `description` необязателен (в ответе `""`, если не передали) |
| — | ответы авторизации: `{ "token": "...", "user": { ... } }` и `{ "message": "..." }` |

Пароль и его хеш в ответах не приходят: в `User` из `types.ts` их нет, и бэк отдаёт ровно `{ id, name, email }`.

Чего в `types.ts` пока нет (добавляет участник 1 вместе с `api.ts`):

```ts
export type AuthResponse = { token: string; user: User };
export type MessageResponse = { message: string };
export type ErrorResponse = { message: string };
```

Бэкенд уже отдаёт ровно это: имена полей — `token`, `user`, `message`.

## Переменные окружения (модуль авторизации)

Эти переменные нужны бэкенду; их перечисляет участник 5 в `.env.example` и `.env`.

| Переменная | Обязательна | По умолчанию | Зачем |
|---|---|---|---|
| `JWT_SECRET` | **да** | — | секрет подписи токена (в репозиторий не попадает) |
| `JWT_ISSUER` | нет | `todo-app` | поле `iss` в токене |
| `JWT_REALM` | нет | `todo-app` | realm для Ktor (заголовок `WWW-Authenticate` при 401) |
| `JWT_EXPIRES_MINUTES` | нет | `1440` | срок жизни токена в минутах (1440 = сутки) |

Если `JWT_SECRET` не задан, бэкенд не стартует и пишет понятную ошибку — это специально, чтобы секрет не оказался в коде.

### Переменные окружения базы данных (подключение)

Их читает `backend/src/main/kotlin/com/todo/database/DatabaseFactory.kt`. Имя проверяется в указанном порядке
(например, `DB_NAME`, затем `POSTGRES_DB`) — достаточно задать любой из вариантов.

| Переменная | Обязательна | По умолчанию | Зачем |
|---|---|---|---|
| `DB_URL` / `DATABASE_URL` | нет | собирается из `DB_HOST`/`DB_PORT`/`DB_NAME` | готовый JDBC-адрес (`jdbc:postgresql://postgres:5432/todo`), имеет приоритет |
| `DB_HOST` | нет | `localhost` | хост базы (в compose — имя сервиса `postgres`) |
| `DB_PORT` | нет | `5432` | порт базы |
| `DB_NAME` / `POSTGRES_DB` | нет | `todo` | имя базы |
| `DB_USER` / `POSTGRES_USER` | нет | `todo` | пользователь базы |
| `DB_PASSWORD` / `POSTGRES_PASSWORD` | **да в Docker** | пусто | пароль базы (в репозиторий не попадает) |
| `DB_MAX_POOL_SIZE` | нет | `10` | размер пула соединений HikariCP |

## Как это связано с кодом (для участников 2, 1 и 4)

- Таблица `users` объявлена в `model/User.kt` (`object Users`) — участник 2 импортирует её в `database/Tables.kt` для связи задач и создаёт в `DatabaseFactory.kt`.
- Маршруты подключаются одной строкой в `routing { authRoutes() }` (`routes/AuthRoutes.kt`).
- Проверка токена в Ktor: `install(Authentication) { jwt(JwtConfig.NAME) { realm = JwtConfig.realm; verifier(JwtConfig.verifier); ... } }`, затем `authenticate(JwtConfig.NAME) { ... }` в маршрутах задач.
- id текущего пользователя: `call.principal<JWTPrincipal>()?.payload?.getClaim(JwtConfig.USER_ID_CLAIM)?.asInt()`.
- Нужные зависимости (участник 2 в `backend/build.gradle.kts`): `io.ktor:ktor-server-auth-jwt` (внутри `com.auth0:java-jwt`) и `org.mindrot:jbcrypt:0.4`.
- ВНИМАНИЕ про Exposed: код авторизации написан под пакеты `org.jetbrains.exposed.sql.*` (Exposed 0.x). Если взять Exposed 1.0, там пакеты переименованы в `org.jetbrains.exposed.v1.core.*` / `org.jetbrains.exposed.v1.jdbc.*` — таблица замен указана комментарием в `model/User.kt`.
- Фронт (участники 1 и 4): объект `user` — `{ id, name, email }` (как тип `User` в `frontend/src/types.ts`), токен — поле `token`, ошибка — `{ "message": "..." }`.
- Практическая шпаргалка «запуск и проверка из консоли» + готовые вызовы для фронта: [`handoff-misha.md`](handoff-misha.md).

