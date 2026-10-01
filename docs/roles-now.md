# Где проект сейчас и кто что делает

Статус на 02.10.2026. `main` = два мержа: `auth-misha` (Миша) и `frontend-tasks` (участник 4).
Ничего лишнего в `main` нет — только код участников.

## Проект в двух словах

Todo-приложение с авторизацией. Регистрация (имя/email/пароль), вход, выход; каждый видит
только свои задачи: создать, изменить, отметить «выполнено», удалить.

- **Фронт:** TypeScript + React + Vite, роутинг `react-router-dom`, стили одним файлом.
- **Бэкенд:** Kotlin, Ktor + Exposed, база PostgreSQL.
- **Запуск:** Docker, одной командой `docker compose up` (фронт + бэк + база).
- Эндпоинты: `POST /api/auth/register|login|logout`, `GET|POST /api/tasks`,
  `PUT|PATCH /api/tasks/:id`, `DELETE /api/tasks/:id`.
- Страницы: `/login`, `/register`, `/` (список задач), окно редактирования.
- Требования задания — в PDF (`docs/JUTGKb-Zadanie_07_05.pdf`), договор API — `docs/api.md`.

## Кто что делает сейчас

| Кто | Роль | Что у него сейчас | Что обязан сделать дальше |
|---|---|---|---|
| **Участник 1** | фронт: каркас и авторизация | заготовки `App.tsx`, `main.tsx`, `api.ts`, `auth.tsx`, `types.ts`, `pages/LoginPage.tsx`, `pages/RegisterPage.tsx` — пустые (комментарии-инструкции) | заполнить `package.json` (скрипты + зависимости), `index.html`, `vite.config.ts`, `tsconfig.json`; роутинг `/login` `/register` `/`; запросы к API, хранение токена, защита страниц, показ ошибок |
| **Участник 2** | бэкенд: сборка, база, задачи | заготовки `build.gradle.kts`, `settings.gradle.kts`, `Application.kt`, `Plugins.kt`, `DatabaseFactory.kt`, `Tables.kt`, `TaskRepository.kt`, `TaskService.kt`, `TaskRoutes.kt`, `application.conf`, `logback.xml` — пустые | собрать проект (`gradle build`), подключить PostgreSQL и создать таблицы `users`+`tasks`, JSON+CORS+единые ошибки, включить проверку токена, все 5 эндпоинтов задач, свои задачи только у своего пользователя (чужая — 403) |
| **Миша (участник 3)** | fullstack: авторизация | **код готов и слит в `main`**, разработан и проверен отдельно (см. `docs/handoff-misha.md`) | дописать свою часть в общий бэк: `build.gradle.kts` (зависимости `ktor-server-auth-jwt`, `jbcrypt`) и `DatabaseFactory` (`SchemaUtils.create(Users, Tasks)`) — без этого бэк не стартует; прогнать сценарии из задания |
| **Участник 4** | фронт: экран задач | **код готов и слит в `main`**: `TasksPage.tsx`, `TaskList.tsx`, `TaskItem.tsx`, `TaskForm.tsx`, `EditTaskModal.tsx`, `styles.css`, тёмная тема | дождаться каркаса участника 1, подключить компоненты к реальным запросам API, проверить адаптив |
| **Участник 5** | Docker + PostgreSQL | заготовки `docker-compose.yml`, `.env.example`, `Dockerfile`-ы, `nginx.conf`, `README.md` — пустые | дописать compose (3 сервиса: postgres, backend, frontend), `.env.example` со всеми переменными, `README.md` с инструкцией запуска, проверить `docker compose up` на чистой машине |

## Правила

1. Каждый работает в своей ветке (`frontend-tasks`, `backend-kotlin`, `auth-misha`, `docker-team`),
   в `main` напрямую не коммитим, слияние — через pull request.
2. Чужие файлы не правим — пишем владельцу.
3. `.env`, пароли, токены в репозиторий не попадают.
4. Один фронт, один бэк, одна база — никаких вторых сервисов.

## Проверено (02.10.2026, до отката)

Код авторизации Миши прогнан в Docker: register `201`, дубль email `409`, login `200`,
неверный пароль `401`, кривой email `400`, короткий пароль `400`, logout `200`/`401`.
`GET /api/tasks` → `404` — маршруты задач ещё не написаны (участник 2).

## Следующий шаг команды

1. Участник 2 и Миша договариваются, кто заполняет `build.gradle.kts` и `DatabaseFactory` (общая зона).
2. Участник 1 заполняет конфиги фронта — только после этого он запускается.
3. Участник 5 дописывает Docker и добивается `docker compose up`.
4. Общий чек-лист — в конце `docs/plan.md`.
