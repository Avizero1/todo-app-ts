import { useState } from 'react'
import type { FormEvent } from 'react'

type Props = {
  onSubmit: (data: { title: string; description: string }) => Promise<void>
}

export default function TaskForm({ onSubmit }: Props) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!title.trim()) {
      setError('Введите название задачи')
      return
    }
    setBusy(true)
    setError('')
    try {
      await onSubmit({ title: title.trim(), description: description.trim() })
      setTitle('')
      setDescription('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось добавить задачу')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="task-form" onSubmit={handleSubmit}>
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Название задачи"
        aria-label="Название задачи"
      />
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Описание (необязательно)"
        aria-label="Описание"
        rows={2}
      />
      {error && <p className="error">{error}</p>}
      <button type="submit" className="primary" disabled={busy}>
        {busy ? 'Добавляю…' : 'Добавить'}
      </button>
    </form>
  )
}