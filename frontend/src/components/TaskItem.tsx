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
    minute: '2-digit'
  })
}

export default function TaskItem({ task, onToggle, onEdit, onDelete }: Props) {
  const [confirmDelete, setConfirmDelete] = useState(false)

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
          {formatDate(task.created_at)}
          {task.updated_at !== task.created_at && ` (изм. ${formatDate(task.updated_at)})`}
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
            onMouseLeave={() => setConfirmDelete(false)}
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