function draftKey(userId: string, questionId: string) {
  return `interviewcopilot.draft.${userId}.${questionId}`
}

export function readDraft(userId: string, questionId: string): string {
  try { return globalThis.sessionStorage?.getItem(draftKey(userId, questionId)) ?? '' } catch { return '' }
}

export function saveDraft(userId: string, questionId: string, answer: string) {
  try {
    if (answer) globalThis.sessionStorage?.setItem(draftKey(userId, questionId), answer)
    else globalThis.sessionStorage?.removeItem(draftKey(userId, questionId))
  } catch { /* A blocked or full browser storage must not interrupt answering. */ }
}
