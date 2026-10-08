import { beforeEach, describe, expect, it, vi } from 'vitest'

import { businessApi } from './api'

describe('businessApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    businessApi.setToken(null)
  })

  it('adds authentication and idempotency headers to a write flow', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      data: { id: 'interview-1', status: 'CANCELLED', cancelled_at: '2026-10-08T00:00:00Z' },
      request_id: 'request-1', timestamp: '2026-10-08T00:00:00Z',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    businessApi.setToken('token-1')

    await businessApi.cancelInterview('interview-1')

    const request = fetchMock.mock.calls[0][1] as RequestInit
    const headers = new Headers(request.headers)
    expect(headers.get('Authorization')).toBe('Bearer token-1')
    expect(headers.get('Idempotency-Key')).toMatch(/^CANCEL-/)
    expect(headers.get('X-Request-Id')).toBeTruthy()
  })

  it('keeps the server error code and presents a Chinese message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 'INTERVIEW_NOT_COMPLETED', message: 'Interview is not completed',
    }), { status: 409, headers: { 'Content-Type': 'application/json' } }))

    await expect(businessApi.getReport('interview-1')).rejects.toMatchObject({
      status: 409,
      code: 'INTERVIEW_NOT_COMPLETED',
      message: '面试尚未完成，完成答题后即可查看报告。',
    })
  })

  it('reuses the same idempotency key after a lost response and renews it after success', async () => {
    const ok = () => new Response(JSON.stringify({ data: { id: 'interview-1' } }), { status: 201 })
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockImplementation(async () => ok())
    const input = { target_position: 'Java 工程师', skills: ['Java'], difficulty: 'MEDIUM' as const, question_count: 3 }
    await expect(businessApi.createInterview(input)).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    await businessApi.createInterview(input)
    await businessApi.createInterview(input)
    const keys = fetchMock.mock.calls.map((call) => new Headers(call[1]?.headers).get('Idempotency-Key'))
    expect(keys[0]).toBe(keys[1])
    expect(keys[2]).not.toBe(keys[1])
  })

  it('keeps the key while an operation is in progress, including an uncertain 503 response', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'OPERATION_IN_PROGRESS' }), { status: 409 }))
      .mockResolvedValueOnce(new Response('{}', { status: 503 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: {} }), { status: 200 }))
    await expect(businessApi.submitAnswer('interview-1', 'question-1', 'same answer')).rejects.toMatchObject({ status: 409 })
    await expect(businessApi.submitAnswer('interview-1', 'question-1', 'same answer')).rejects.toMatchObject({ status: 503 })
    await businessApi.submitAnswer('interview-1', 'question-1', 'same answer')
    const keys = fetchMock.mock.calls.map((call) => new Headers(call[1]?.headers).get('Idempotency-Key'))
    expect(new Set(keys).size).toBe(1)
  })

  it('does not reuse an uncertain write key for changed content', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('offline'))
    await expect(businessApi.submitAnswer('i', 'q', 'first answer')).rejects.toThrow()
    await expect(businessApi.submitAnswer('i', 'q', 'changed answer')).rejects.toThrow()
    const keys = fetchMock.mock.calls.map((call) => new Headers(call[1]?.headers).get('Idempotency-Key'))
    expect(keys[0]).not.toBe(keys[1])
  })

  it('clears an expired session, but retains it on a connection failure', async () => {
    businessApi.setToken('expired-token')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('offline'))
    await expect(businessApi.me()).rejects.toThrow()
    expect(businessApi.hasToken()).toBe(true)
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 401 }))
    await expect(businessApi.me()).rejects.toMatchObject({ status: 401 })
    expect(businessApi.hasToken()).toBe(false)
  })

  it('handles timeout and non-JSON success responses without a render error', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new DOMException('timed out', 'TimeoutError'))
    await expect(businessApi.me()).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })
    fetchMock.mockResolvedValueOnce(new Response('<html>wrong proxy</html>', { status: 200 }))
    await expect(businessApi.me()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('preserves page metadata and the list DTO without requiring detail-only fields', async () => {
    const page = { items: [{ id: 'i', target_position: 'Java', difficulty: 'MEDIUM', question_count: 3, status: 'CREATED', interview_overall_score: null, created_at: '2026-10-08T00:00:00Z', completed_at: null }], page: 2, page_size: 10, total: 11 }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: page })))
    expect(await businessApi.listInterviews(2, 10)).toEqual(page)
    expect(fetchMock.mock.calls[0][0]).toContain('page=2&page_size=10')
  })

  it('reports a timeout while reading the response body and retains the write key', async () => {
    const interrupted = new Response('', { status: 200 })
    vi.spyOn(interrupted, 'json').mockRejectedValue(new DOMException('body timed out', 'AbortError'))
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(interrupted)
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: {} }), { status: 200 }))
    await expect(businessApi.cancelInterview('interview-1')).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })
    await businessApi.cancelInterview('interview-1')
    const keys = fetchMock.mock.calls.map((call) => new Headers(call[1]?.headers).get('Idempotency-Key'))
    expect(keys[0]).toBe(keys[1])
  })
})
