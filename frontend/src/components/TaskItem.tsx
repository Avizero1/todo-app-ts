import type { Task } from '../types'

type Props = {
  task: Task
  onToggle: (task: Task) => void
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}

export default function TaskItem({ task, onToggle, onEdit, onDelete }: Props) {
  return (
    <li className={task.completed ? 'task task--done' : 'task'}>
      <input
        type="checkbox"
        className="task__check"
        checked={task.completed}
        onChange={() => onToggle(task)}
        aria-label="Выполнено"
      />
      <div className="task__body">
        <h3 className="task__title">{task.title}</h3>
        {task.description && <p className="task__desc">{task.description}</p>}
        <p className="task__meta">
          {task.completed ? 'Выполнена' : 'В работе'} · создана {formatDate(task.created_at)}
          {task.updated_at !== task.created_at && ` · изменена ${formatDate(task.updated_at)}`}
        </p>
      </div>
      <div className="task__actions">
        <button type="button" onClick={() => onEdit(task)}>Изменить</button>
        <button type="button" className="danger" onClick={() => onDelete(task)}>Удалить</button>
      </div>
    </li>
  )
}