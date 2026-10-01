import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { Task } from '../types'

type Props = {
  task: Task
  onSave: (data: { title: string; description: string }) => Promise<void>
  onClose: () => void
}

export default function EditTaskModal({ task, onSave, onClose }: Props) {
  const [title, setTitle] = useState(task.title)
  const [description, setDescription] = useState(task.description ?? '')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!title.trim()) {
      setError('Название не может быть пустым')
      return
    }
    setBusy(true)
    setError('')
    try {
      await onSave({ title: title.trim(), description: description.trim() })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось сохранить')
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h2>Редактирование задачи</h2>
        <input value={title} onChange={(e) => setTitle(e.target.value)} aria-label="Название" autoFocus />
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} aria-label="Описание" rows={4} />
        {error && <p className="error">{error}</p>}
        <div className="modal__actions">
          <button type="button" onClick={onClose}>Отмена</button>
          <button type="submit" className="primary" disabled={busy}>
            {busy ? 'Сохраняю…' : 'Сохранить'}
          </button>
        </div>
      </form>
    </div>
  )
}