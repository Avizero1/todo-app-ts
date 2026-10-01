import type { Task } from '../types'
import TaskItem from './TaskItem'

type Props = {
  tasks: Task[]
  loading: boolean
  onToggle: (task: Task) => void
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
}

export default function TaskList({ tasks, loading, onToggle, onEdit, onDelete }: Props) {
  if (loading) return <p className="state">Загрузка…</p>
  if (tasks.length === 0) return <p className="state">Задач пока нет. Добавьте первую!</p>

  return (
    <ul className="task-list">
      {tasks.map((task) => (
        <TaskItem key={task.id} task={task} onToggle={onToggle} onEdit={onEdit} onDelete={onDelete} />
      ))}
    </ul>
  )
}