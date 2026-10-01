import { useState } from 'react'
import type { Task } from '../types'

type Props = {
  task: Task
  onToggle: (task: Task) => void
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('ru-RU', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function TaskItem({ task, onToggle, onEdit, onDelete }: Props) {
  const [confirmDelete, setConfirmDelete] = useState(false)

  const created = formatDate(task.created_at)
  const updated = formatDate(task.updated_at)

  return (
    <li className={task.completed ? 'task task--done' : 'task'}>
      <input
        type="checkbox"
        className="task__check"
        checked={task.completed}
        onChange={() => onToggle(task)}
        aria-label="Отметить выполненной"
      />
      <div className="task__body">
        <h3 className="task__title">{task.title}</h3>
        {task.description && <p className="task__desc">{task.description}</p>}
        <p className="task__meta">
          Создана {created}
          {updated !== created && ` · изменена ${updated}`}
        </p>
      </div>
      <div className="task__actions">
        <button type="button" onClick={() => onEdit(task)}>
          Изменить
        </button>
        {confirmDelete ? (
          <button
            type="button"
            className="danger"
            onClick={() => onDelete(task)}
            onBlur={() => setConfirmDelete(false)}
            autoFocus
          >
            Точно?
          </button>
        ) : (
          <button type="button" className="danger" onClick={() => setConfirmDelete(true)}>
            Удалить
          </button>
        )}
      </div>
    </li>
  )
}