import { describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../api/client'
import { postComment } from '../api/TicketsApi'
import { MAX_ATTACHMENT_BYTES, validateFiles } from '../components/attachment-validation'

describe('validateFiles (mirrors API limits)', () => {
  it('rejects files above 10 MB', () => {
    const big = new File([new ArrayBuffer(1)], 'huge.bin', { type: 'text/plain' })
    Object.defineProperty(big, 'size', { value: MAX_ATTACHMENT_BYTES + 1 })
    expect(validateFiles([big])).toEqual([{ kind: 'tooBig', filename: 'huge.bin' }])
  })

  it('rejects MIME types outside the backend whitelist', () => {
    const exe = new File(['MZ'], 'virus.exe', { type: 'application/x-msdownload' })
    expect(validateFiles([exe])).toEqual([{ kind: 'badType', filename: 'virus.exe' }])
  })

  it('accepts allowed files', () => {
    const ok = [
      new File(['log'], 'log.txt', { type: 'text/plain' }),
      new File(['png'], 'shot.png', { type: 'image/png' }),
      new File(['pdf'], 'doc.pdf', { type: 'application/pdf' }),
    ]
    expect(validateFiles(ok)).toEqual([])
  })
})

describe('postComment', () => {
  it('sends multipart FormData with body and files', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          id: 1,
          type: 'COMMENT',
          eventType: null,
          authorId: 1,
          authorName: 'Ana',
          body: 'Segue o log',
          payload: null,
          createdAt: new Date().toISOString(),
          attachments: [],
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    await postComment(7, 'Segue o log', [new File(['data'], 'log.txt', { type: 'text/plain' })])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('/api/tickets/7/comments')
    expect(init.body).toBeInstanceOf(FormData)
    const form = init.body as FormData
    expect(form.get('body')).toBe('Segue o log')
    const files = form.getAll('files') as File[]
    expect(files).toHaveLength(1)
    expect(files[0].name).toBe('log.txt')
    // Content-Type must be left to the browser (boundary)
    expect(new Headers(init.headers).has('Content-Type')).toBe(false)

    vi.unstubAllGlobals()
  })

  it('posts without files when the list is empty', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ id: 2, attachments: [] }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await postComment(7, 'sem arquivo', [])
    const secondCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const form = secondCall[1].body as FormData
    expect(form.getAll('files')).toHaveLength(0)

    vi.unstubAllGlobals()
  })
})

describe('apiFetch FormData handling', () => {
  it('does not set a manual Content-Type for FormData bodies', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/x', { method: 'POST', body: new FormData() })
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = new Headers(call[1].headers)
    expect(headers.has('Content-Type')).toBe(false)

    vi.unstubAllGlobals()
  })
})
