export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type InterviewStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'FAILED'
export type ReportStatus = 'PENDING' | 'GENERATING' | 'AVAILABLE' | 'FAILED_RETRYABLE'

export interface User {
  id: string
  login_identifier: string
  status: 'ACTIVE' | 'LOCKED'
  created_at?: string
}

export interface InterviewListItem {
  id: string
  target_position: string
  difficulty: Difficulty
  question_count: number
  status: InterviewStatus
  interview_overall_score: number | null
  created_at: string
  completed_at?: string | null
}

export interface Interview extends InterviewListItem {
  skills: string[]
  current_question_number: number | null
}

export interface InterviewPage {
  items: InterviewListItem[]
  page: number
  page_size: number
  total: number
}

export interface PublicQuestion {
  id: string
  question_number: number
  question: string
  topic: string
  difficulty: Difficulty
  question_type: 'CONCEPTUAL' | 'PRACTICAL' | 'SCENARIO' | 'DESIGN'
}

export interface CurrentQuestion {
  interview_id: string
  interview_status: 'IN_PROGRESS' | 'COMPLETED'
  question_number: number | null
  total_question_count: number
  question: {
    id: string
    question: string
    topic: string
    difficulty: Difficulty
    question_type: PublicQuestion['question_type']
  } | null
  answer_status: 'WAITING_FOR_ANSWER' | 'EVALUATION_PENDING' | 'EVALUATED' | null
}

export interface Evaluation {
  accuracy: number
  completeness: number
  depth: number
  clarity: number
  answer_overall_score: number
  strengths: string[]
  missing_points: string[]
  feedback: string
}

export interface SubmitResult {
  answer: { id: string; question_id: string; submitted_at: string }
  evaluation: Evaluation
  next_question: PublicQuestion | null
  interview_status: 'IN_PROGRESS' | 'COMPLETED'
  report_status?: ReportStatus
}

export interface Report {
  interview_id: string
  report_status: ReportStatus
  interview_overall_score: number
  question_count: number
  completed_question_count: number
  average_accuracy: number
  average_completeness: number
  average_depth: number
  average_clarity: number
  duration_seconds: number
  strength_summary: string | null
  weakness_summary: string | null
  improvement_suggestions: string[] | null
  overall_comment: string | null
  published_at: string | null
}

interface Envelope<T> {
  data: T
  request_id: string
  timestamp: string
}

interface ApiErrorBody {
  code?: string
  message?: string
}

const errorMessages: Record<string, string> = {
  INVALID_REQUEST: '请求参数不正确，请检查输入内容。',
  VALIDATION_FAILED: '输入内容不符合要求，请检查用户名、密码和面试设置。',
  UNAUTHORIZED: '登录已过期或账号密码不正确，请重新登录。',
  FORBIDDEN: '你没有权限访问这条记录。',
  RESOURCE_NOT_FOUND: '记录或接口不存在，请刷新页面后重试。',
  ILLEGAL_INTERVIEW_STATE: '面试状态已改变，请刷新记录后继续。',
  INTERVIEW_NOT_READY: '面试尚未开始，请先开始面试。',
  INTERVIEW_NOT_COMPLETED: '面试尚未完成，完成答题后即可查看报告。',
  DUPLICATE_SUBMISSION: '这道题已提交，请刷新记录后继续。',
  IDEMPOTENCY_KEY_REUSED: '请求内容已改变，请刷新页面后重试。',
  OPERATION_IN_PROGRESS: '上一次操作仍在处理中，请稍后使用相同内容重试。',
  REPORT_NOT_READY: '报告尚未准备好，请稍后重试。',
  AI_TIMEOUT: 'AI 处理超时，你的输入已保留，请稍后重试。',
  LLM_TIMEOUT: 'AI 处理超时，你的输入已保留，请稍后重试。',
  AI_SERVICE_UNAVAILABLE: 'AI 服务暂时不可用，请检查本地服务是否已启动。',
  LLM_PROVIDER_ERROR: 'AI 服务暂时不可用，请稍后重试。',
  RATE_LIMITED: '操作过于频繁，请稍后重试。',
  INVALID_AI_RESPONSE: 'AI 返回的内容不完整，请重试。',
  SCHEMA_VALIDATION_FAILED: 'AI 返回的内容不完整，请重试。',
  NETWORK_ERROR: '无法连接服务，请确认本地服务已启动后重试。',
  INTERNAL_ERROR: '服务暂时出现问题，请稍后重试。',
}

function errorMessage(status: number, body: ApiErrorBody): string {
  if (body.code && errorMessages[body.code]) return errorMessages[body.code]!
  if (status === 401) return errorMessages.UNAUTHORIZED!
  if (status === 404) return errorMessages.RESOURCE_NOT_FOUND!
  if (status === 429) return errorMessages.RATE_LIMITED!
  if (status >= 500) return '服务暂时不可用，请检查本地服务是否已启动。'
  return '请求未成功，请检查输入内容后重试。'
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const apiBaseUrl = (import.meta.env.VITE_BUSINESS_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

function requestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `web-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function idempotencyKey(operation: string): string {
  return `${operation}-${requestId()}`
}

function storedToken(): string | null {
  try { return globalThis.localStorage?.getItem('interviewcopilot.access_token') ?? null } catch { return null }
}

export class BusinessApi {
  private token: string | null = storedToken()
  private pendingWrites = new Map<string, string>()

  setToken(token: string | null) {
    if (token !== this.token || token === null) this.pendingWrites.clear()
    this.token = token
    try {
      if (token) globalThis.localStorage?.setItem('interviewcopilot.access_token', token)
      else globalThis.localStorage?.removeItem('interviewcopilot.access_token')
    } catch { /* Storage can be unavailable in private browsing; keep the in-memory session. */ }
  }

  hasToken(): boolean {
    return this.token !== null
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<Envelope<T>> {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Content-Type', 'application/json')
    headers.set('X-Request-Id', requestId())
    if (this.token) headers.set('Authorization', `Bearer ${this.token}`)

    let response: Response
    let body: Envelope<T> & ApiErrorBody
    try {
      response = await fetch(`${apiBaseUrl}/api/v1${path}`, { ...init, headers, signal: AbortSignal.timeout(30_000) })
      const parsed: unknown = await response.json().catch((reason: unknown) => {
        if (reason instanceof Error && (reason.name === 'TimeoutError' || reason.name === 'AbortError')) throw reason
        return {}
      })
      body = (parsed && typeof parsed === 'object' ? parsed : {}) as Envelope<T> & ApiErrorBody
    } catch (reason) {
      const timedOut = reason instanceof Error && (reason.name === 'TimeoutError' || reason.name === 'AbortError')
      throw new ApiError(timedOut ? '请求超时，你的输入已保留，请使用相同内容重试。' : errorMessages.NETWORK_ERROR!, 0, timedOut ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR')
    }
    if (!response.ok) {
      if (response.status === 401 && !path.startsWith('/auth/')) this.setToken(null)
      throw new ApiError(errorMessage(response.status, body), response.status, body.code)
    }
    if (!body || typeof body !== 'object' || !('data' in body)) throw new ApiError('服务返回了无效内容，请检查接口地址后重试。', 502, 'INVALID_RESPONSE')
    return body
  }

  private async write<T>(path: string, operation: string, input?: unknown): Promise<T> {
    const body = input === undefined ? undefined : JSON.stringify(input)
    const identity = `${path}:${body ?? ''}`
    const key = this.pendingWrites.get(identity) ?? idempotencyKey(operation)
    this.pendingWrites.set(identity, key)
    try {
      const result = await this.request<T>(path, { method: 'POST', headers: { 'Idempotency-Key': key }, body })
      this.pendingWrites.delete(identity)
      return result.data
    } catch (reason) {
      // A lost response can hide a committed write. Reuse its key on retry.
      if (reason instanceof ApiError && reason.status >= 400 && reason.status < 500
        && ![408, 429].includes(reason.status) && reason.code !== 'OPERATION_IN_PROGRESS') this.pendingWrites.delete(identity)
      throw reason
    }
  }

  async register(loginIdentifier: string, password: string): Promise<User> {
    const result = await this.request<{ user: User }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ login_identifier: loginIdentifier, password }),
    })
    return result.data.user
  }

  async login(loginIdentifier: string, password: string): Promise<{ token: string; user: User }> {
    const result = await this.request<{ access_token: string; user: User }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ login_identifier: loginIdentifier, password }),
    })
    this.setToken(result.data.access_token)
    return { token: result.data.access_token, user: result.data.user }
  }

  async me(): Promise<User> {
    const result = await this.request<User>('/users/me')
    return result.data
  }

  async listInterviews(page = 1, pageSize = 10): Promise<InterviewPage> {
    const result = await this.request<InterviewPage>(`/interviews?page=${page}&page_size=${pageSize}`)
    return result.data
  }

  async getInterview(interviewId: string): Promise<Interview> {
    return (await this.request<Interview>(`/interviews/${interviewId}`)).data
  }

  async createInterview(input: { target_position: string; skills: string[]; difficulty: Difficulty; question_count: number }) {
    return this.write<Interview>('/interviews', 'CREATE', input)
  }

  async startInterview(interviewId: string): Promise<{ interview: Pick<Interview, 'id' | 'status' | 'current_question_number'>; current_question: PublicQuestion }> {
    return this.write(`/interviews/${interviewId}/start`, 'START')
  }

  async currentQuestion(interviewId: string): Promise<CurrentQuestion> {
    const result = await this.request<CurrentQuestion>(`/interviews/${interviewId}/current-question`)
    return result.data
  }

  async submitAnswer(interviewId: string, questionId: string, answerContent: string): Promise<SubmitResult> {
    return this.write<SubmitResult>(`/interviews/${interviewId}/answers`, 'ANSWER', { question_id: questionId, answer_content: answerContent })
  }

  async cancelInterview(interviewId: string) {
    return this.write<{ id: string; status: 'CANCELLED'; cancelled_at: string }>(`/interviews/${interviewId}/cancel`, 'CANCEL')
  }

  async getReport(interviewId: string): Promise<Report> {
    const result = await this.request<Report>(`/interviews/${interviewId}/report`)
    return result.data
  }

  async retryReport(interviewId: string): Promise<Report> {
    return this.write<Report>(`/interviews/${interviewId}/report/retry`, 'REPORT')
  }
}

export const businessApi = new BusinessApi()
