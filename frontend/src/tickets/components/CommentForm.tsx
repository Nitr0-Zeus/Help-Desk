import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useI18n } from '../../i18n/I18nProvider'
import { usePostComment } from '../hooks/useTicket'
import { validateFiles } from './attachment-validation'

const ALLOWED_ATTR = ['image/png', 'image/jpeg', 'image/gif', 'application/pdf', 'text/plain', 'application/zip'].join(',')

export function CommentForm({ ticketId }: { ticketId: number }) {
  const { t } = useI18n()
  const postComment = usePostComment(ticketId)
  const [body, setBody] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [issues, setIssues] = useState<string[]>([])
  const fileInput = useRef<HTMLInputElement>(null)

  function addFiles(selected: FileList | null) {
    if (!selected) return
    const next = [...files, ...Array.from(selected)]
    const found = validateFiles(next)
    if (found.length > 0) {
      setIssues(
        found.map((issue) =>
          `${t(issue.kind === 'tooBig' ? 'comment.error.tooBig' : 'comment.error.badType')} ${issue.filename}`,
        ),
      )
      setFiles(next.filter((file) => validateFiles([file]).length === 0))
      if (fileInput.current) fileInput.current.value = ''
      return
    }
    setIssues([])
    setFiles(next)
    if (fileInput.current) fileInput.current.value = ''
  }

  function removeFile(index: number) {
    setFiles(files.filter((_, i) => i !== index))
    setIssues([])
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!body.trim()) return
    postComment.mutate(
      { body: body.trim(), files },
      {
        onSuccess: () => {
          setBody('')
          setFiles([])
          setIssues([])
          if (fileInput.current) fileInput.current.value = ''
        },
      },
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-2.5 rounded-lg border border-[var(--border)] bg-[var(--surface)] p-4"
    >
      <label htmlFor="comment-body" className="sr-only">
        {t('comment.placeholder')}
      </label>
      <textarea
        id="comment-body"
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder={t('comment.placeholder')}
        rows={3}
        maxLength={5000}
        className="w-full resize-y rounded-md border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--text)] placeholder:text-[var(--text-muted)] transition-colors duration-150 hover:border-slate-400 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]"
      />

      {files.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {files.map((file, index) => (
            <li
              key={`${file.name}-${index}`}
              className="flex items-center gap-1.5 rounded-md border border-[var(--border)] bg-[var(--bg)] py-1 pr-1 pl-2.5 text-xs"
            >
              <span className="max-w-40 truncate">{file.name}</span>
              <button
                type="button"
                onClick={() => removeFile(index)}
                aria-label={`${t('comment.removeFile')} ${file.name}`}
                className="flex h-5 w-5 items-center justify-center rounded text-[var(--text-muted)] transition-colors duration-150 hover:bg-[var(--hover)] hover:text-[var(--text)]"
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}

      {issues.length > 0 && (
        <ul role="alert" className="space-y-1 text-xs text-red-600 dark:text-red-400">
          {issues.map((issue) => (
            <li key={issue}>{issue}</li>
          ))}
        </ul>
      )}
      {postComment.isError && (
        <p role="alert" className="text-xs text-red-600 dark:text-red-400">
          {t('comment.error.generic')}
        </p>
      )}

      <div className="flex items-center gap-2.5">
        <button
          type="button"
          onClick={() => fileInput.current?.click()}
          className="h-9 rounded-md border border-[var(--border)] px-3 text-sm font-medium transition-colors duration-150 hover:bg-[var(--hover)]"
        >
          {t('comment.attach')}
        </button>
        <input
          ref={fileInput}
          type="file"
          multiple
          accept={ALLOWED_ATTR}
          onChange={(e) => addFiles(e.target.files)}
          className="hidden"
          aria-label={t('comment.attach')}
        />
        <button
          type="submit"
          disabled={postComment.isPending || !body.trim()}
          className="ml-auto h-9 rounded-md bg-[var(--accent)] px-4 text-sm font-medium text-white transition-opacity duration-150 hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {postComment.isPending ? '…' : t('comment.submit')}
        </button>
      </div>
    </form>
  )
}
