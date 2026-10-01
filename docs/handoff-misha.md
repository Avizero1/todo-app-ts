# Модуль авторизации — что сделано и как проверять
(ветка `auth-misha`).

| Файл | Что в нём |
|---|---|
| `backend/src/main/kotlin/com/todo/model/User.kt` | таблица `users`, модель пользователя, DTO запросов и ответов |
| `backend/src/main/kotlin/com/todo/repository/UserRepository.kt` | все обращения к таблице `users` |
| `backend/src/main/kotlin/com/todo/security/JwtConfig.kt` | выдача и проверка JWT (HS256) |
| `backend/src/main/kotlin/com/todo/service/AuthService.kt` | регистрация, вход, выход, проверка данных |
| `backend/src/main/kotlin/com/todo/routes/AuthRoutes.kt` | маршруты `/api/auth/*` и коды ответов |

Общий контракт API лежит в [`api.md`](api.md). Этот файл — практическая шпаргалка: не нужно искать по
коду, всё важное для фронта и для ручной проверки собрано в одном месте.

Разделы:

- [Для Иллариона — запуск и проверка из консоли](#для-иллариона--запуск-и-проверка-из-консоли)
- [Для участника 1 — вызовы из frontend/src/api.ts](#для-участника-1--вызовы-из-frontendsrcapits)

## Для Иллариона — запуск и проверка из консоли

### Что готово

Три эндпоинта, все под `/api/auth`:

| Запрос | Код | Тело ответа |
|---|---|---|
| `POST /api/auth/register` | `201` | `{ "token": "...", "user": { ... } }` |
| `POST /api/auth/login` | `200` | `{ "token": "...", "user": { ... } }` |
| `POST /api/auth/logout` | `200` | `{ "message": "Выход выполнен" }` (нужен заголовок `Authorization`) |

`user` в ответе — ровно `{ "id": 1, "name": "Миша", "email": "misha@example.com" }`.
Пароль и его хеш наружу не отдаются никогда.

Коды: `201`/`200` — успех, `400` — плохие данные, `401` — нет или битый токен / неверный пароль,
`409` — email занят, `500` — неожиданное. Формат любой ошибки — `{ "message": "текст" }`.

### Что нужно, чтобы это реально запустилось

1. `JWT_SECRET` — обязателен, без него бэк не стартует (специально, чтобы секрет не оказался в коде).
   Windows PowerShell: `$env:JWT_SECRET="любая-длинная-строка"`.
   Необязательные: `JWT_ISSUER` (`todo-app`), `JWT_REALM` (`todo-app`), `JWT_EXPIRES_MINUTES` (`1440` = сутки).
2. База: таблицу `users` создаёт участник 2 в `DatabaseFactory` (`SchemaUtils.create(Users, Tasks)`).
3. Зависимости в `backend/build.gradle.kts` — тоже участник 2: `io.ktor:ktor-server-auth-jwt`,
   `org.mindrot:jbcrypt:0.4`.
4. Маршруты включаются одной строкой `authRoutes()` внутри `routing { ... }`.

> ВАЖНО: пока `build.gradle.kts` и `DatabaseFactory.kt` не заполнены (зона участника 2), бэк не поднимется —
> это не часть модуля авторизации, но без неё проверки ниже упадут ещё на старте.
> Порт в примерах — `8080`; если участник 2 поставит другой, заменить во всех строках.

### Проверка из консоли (bash, Git Bash, macOS, Linux)

Регистрация:

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Миша","email":"misha@example.com","password":"secret123"}'
# ожидаем 201 и {"token":"...","user":{"id":1,"name":"Миша","email":"misha@example.com"}}
```

Вход:

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"misha@example.com","password":"secret123"}'
# ожидаем 200
```

Выход (токен берём из ответа выше):

```bash
curl -i -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer <TOKEN>"
# ожидаем 200 {"message":"Выход выполнен"}
```

Токен в переменную, чтобы проверять задачи:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"misha@example.com","password":"secret123"}' | jq -r .token)

curl -i http://localhost:8080/api/tasks -H "Authorization: Bearer $TOKEN"
```

То же самое в PowerShell (там `curl` капризный с кавычками, проще через `Invoke-RestMethod`):

```powershell
$body = '{"email":"misha@example.com","password":"secret123"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType 'application/json' -Body $body
```

### Что проверить (сценарии из задания)

| Что делаем | Ожидаемый ответ |
|---|---|
| повторная регистрация на тот же email | `409` `{"message":"Пользователь с таким email уже зарегистрирован"}` |
| вход с неверным паролем | `401` `{"message":"Неверный email или пароль"}` |
| вход с незарегистрированным email | `401` тот же текст, что и при неверном пароле (не подсказываем, какие email есть) |
| email без домена (`test@localhost`) | `400` `{"message":"Некорректный email"}` |
| пароль короче 6 символов | `400` `{"message":"Пароль должен быть не короче 6 символов"}` |
| пустой `password` | `400` `{"message":"Пароль обязателен"}` |
| регистрация email в другом регистре (`A@b.com`) | попадаем в тот же аккаунт: email приводим к нижнему регистру |
| `logout` без заголовка `Authorization` | `401` `{"message":"Нужен действительный токен"}` |
| `logout` с испорченным токеном (убрать один символ) | `401` `{"message":"Нужен действительный токен"}` |
| задачи без токена | `401` (это уже маршруты участника 2) |

### Про токен

JWT, алгоритм HS256. Внутри: `iss`, `sub` (= id пользователя), `userId`, `email`, `iat`, `exp`.
Живёт 1440 минут по умолчанию (`JWT_EXPIRES_MINUTES`). Заголовок строго
`Authorization: Bearer <токен>` — слово `Bearer` читается без учёта регистра, лишние пробелы терпим.

### Чего ещё нет (осознанно, не забыто)

- нет `GET /api/auth/me`: фронт узнаёт пользователя из ответов `register` и `login`;
- `logout` клиентский: сервер токен не «блэклистит», фронт просто удаляет его у себя;
- нет refresh-токенов, нет выхода со всех устройств, нет ограничения частоты попыток входа.

## Для участника 1 — вызовы из frontend/src/api.ts

Всё сверено с `frontend/src/types.ts`.

### Главное по типам

- `User` в `types.ts` — `{ id: number; name: string; email: string }`. Бэк отдаёт ровно это, поле в поле.
- `created_at` у пользователя **нет** (в `types.ts` его тоже нет) — не жди его в ответе.
- `name` **всегда строка**: если имя не прислали, придёт `""`, а не `null` — проверка на `null` не нужна.
- `RegisterRequest { name, email, password }` и `LoginRequest { email, password }` совпадают с `types.ts`.
- Токен приходит полем `token`, уходит в заголовке `Authorization: Bearer <токен>`.

Чего в `types.ts` пока нет — добавь (бэк уже отдаёт именно так):

```ts
export type AuthResponse = { token: string; user: User };
export type MessageResponse = { message: string };
export type ErrorResponse = { message: string };
```

### Запросы и ответы

**1) `POST /api/auth/register`**

```text
заголовки: Content-Type: application/json
тело:      { "name": "Миша", "email": "misha@example.com", "password": "secret123" }
201:       { "token": "eyJ...", "user": { "id": 1, "name": "Миша", "email": "misha@example.com" } }
400:       плохие данные или битый JSON  -> { "message": "..." }
409:       email уже занят               -> { "message": "Пользователь с таким email уже зарегистрирован" }
```

После `201` пользователь сразу авторизован: токен рабочий, отдельный вход не нужен.

**2) `POST /api/auth/login`**

```text
тело:  { "email": "misha@example.com", "password": "secret123" }
200:   { "token": "...", "user": { "id": 1, "name": "Миша", "email": "misha@example.com" } }
401:   { "message": "Неверный email или пароль" }   // один текст и для «нет email», и для «не тот пароль»
```

**3) `POST /api/auth/logout`**

```text
заголовки: Authorization: Bearer <токен>, тело не нужно
200:       { "message": "Выход выполнен" }
401:       { "message": "Нужен действительный токен" }
```

Сервер токен не хранит: после `200` просто удали токен из `localStorage`.

### Полный текст ошибок `400` (показывай пользователю как есть, они по-русски)

```text
Email обязателен
Некорректный email
Email слишком длинный (максимум 255 символов)
Пароль обязателен
Пароль должен быть не короче 6 символов
Пароль слишком длинный (максимум 72 символов)
Имя слишком длинное (максимум 255 символов)
Некорректный JSON или не хватает обязательных полей
```

Прочие коды: `401` — `"Неверный email или пароль"` / `"Нужен действительный токен"`,
`409` — `"Пользователь с таким email уже зарегистрирован"`, `500` — `"Внутренняя ошибка сервера"`.

### Шаблон `api.ts` (можно брать как основу)

```ts
import type { User, RegisterRequest, LoginRequest } from './types';

export type AuthResponse = { token: string; user: User };
export type MessageResponse = { message: string };

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
const TOKEN_KEY = 'todo_token';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const saveToken = (t: string) => localStorage.setItem(TOKEN_KEY, t);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

/** Ошибка с сервера всегда { message: "..." } -> показываем текст как есть. */
class ApiError extends Error {}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers ?? {}),
    },
  });
  if (!res.ok) {
    let text = 'Ошибка сервера';
    try {
      text = (await res.json()).message ?? text;
    } catch {
      /* тело не JSON */
    }
    throw new ApiError(text);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export async function register(body: RegisterRequest) {
  const r = await request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  saveToken(r.token);
  return r.user;
}

export async function login(body: LoginRequest) {
  const r = await request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  saveToken(r.token);
  return r.user;
}

export async function logout() {
  try {
    await request<MessageResponse>('/api/auth/logout', { method: 'POST' });
  } finally {
    clearToken(); // токен удаляем в любом случае
  }
}

// Для остальных запросов (задачи) — тот же request(): он сам подставит Bearer.
```

### Мелочи, на которых легко споткнуться

- `Content-Type: application/json` обязателен, иначе получишь `400 "Некорректный JSON..."`.
- Заголовок именно `Bearer <токен>` (слово `Bearer` и пробел). Без него `/logout` и задачи — `401`.
- `getToken`/`clearToken` держи в одном модуле: иначе после выхода где-то останется старый токен.
- `GET /api/auth/me` нет, поэтому если при перезагрузке страницы нужен пользователь на экране — сохрани
  объект `user` в `localStorage` рядом с токеном. Либо скажи — добавлю `/me` на бэк, это ~10 строк.
- Задача в JSON (зона участника 2): `description` — всегда строка (`""`, если пусто), даты
  `created_at`/`updated_at` — строка ISO-8601 UTC, `user_id` приходит с сервера, в теле запроса его **не передавай**.
- Ожидаемые коды задач: `403` — чужая задача, `404` — нет такой, `400` — пустой `title`, `200`/`201` — успех.

