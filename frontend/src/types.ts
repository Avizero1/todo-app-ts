// frontend/src/types.ts — типы фронтенда, описывающие данные из API.
// Здесь нужно (участник 1): типы пользователя и задачи (id, title, description, completed, created_at, updated_at, user_id),
// типы тел запросов на регистрацию, вход, создание и редактирование задачи.

export type Task = {
    id: number;
    title: string;
    description: string;
    completed: boolean;
    created_at: string;
    updated_at: string;
    user_id: number;
}

export type User = {
    id: number;
    name: string;
    email: string;
}

export type RegisterRequest = {
    name: string;
    email: string;
    password: string;
}

export type LoginRequest = {
    email: string;
    password: string;
}

export type CreateTaskRequest = {
    title: string;
    description?: string;
}

export type UpdateTaskRequest = {
    title: string;
    description?: string;
}