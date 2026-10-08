import { describe, expect, it } from 'vitest'

import { appMetadata } from './appMetadata'

describe('appMetadata', () => {
  it('identifies the application foundation', () => {
    expect(appMetadata).toEqual({
      name: 'InterviewCopilot AI',
      stage: 'Engineering Foundation',
    })
  })
})
