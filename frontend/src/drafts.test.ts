import { afterEach, describe, expect, it, vi } from 'vitest'
import { readDraft, saveDraft } from './drafts'

afterEach(() => vi.unstubAllGlobals())

describe('answer drafts', () => {
  it('restores answers per user and question and clears a submitted draft', () => {
    const values = new Map<string, string>()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
    })
    saveDraft('user-1', 'question-1', 'unfinished answer')
    expect(readDraft('user-1', 'question-1')).toBe('unfinished answer')
    expect(readDraft('user-2', 'question-1')).toBe('')
    expect(readDraft('user-1', 'question-2')).toBe('')
    saveDraft('user-1', 'question-1', '')
    expect(readDraft('user-1', 'question-1')).toBe('')
  })

  it('keeps answering available when browser storage is blocked', () => {
    vi.stubGlobal('sessionStorage', {
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('quota exceeded') },
    })
    expect(readDraft('u', 'q')).toBe('')
    expect(() => saveDraft('u', 'q', 'answer')).not.toThrow()
  })
})
