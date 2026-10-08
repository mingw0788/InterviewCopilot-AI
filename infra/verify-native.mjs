import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

const frontend = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'
const business = process.env.BUSINESS_BASE_URL ?? 'http://127.0.0.1:8080'
const ai = process.env.AI_BASE_URL ?? 'http://127.0.0.1:8001'
for (const address of [frontend, business, ai]) {
  const url = new URL(address)
  assert.ok(url.protocol === 'http:' && ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname), '验证仅允许访问本机服务。')
}
let checks = 0
let token

async function checkHealth(url, json = false) {
  const response = await fetch(url, { signal: AbortSignal.timeout(5000) })
  assert.equal(response.status, 200, `健康检查失败：${url}`)
  if (json) assert.equal((await response.json()).status, 'UP')
  checks++
}

async function request(path, { method = 'GET', body, key, status = 200, auth = token, code } = {}) {
  const headers = { Accept: 'application/json', 'X-Request-Id': randomUUID() }
  if (auth) headers.Authorization = `Bearer ${auth}`
  if (key) headers['Idempotency-Key'] = key
  if (body) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${frontend}/api/v1${path}`, {
    method, headers, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30_000),
  })
  const payload = await response.json()
  assert.equal(response.status, status, `${method} ${path}：${payload.code ?? '响应不符'}`)
  if (code) assert.equal(payload.code, code)
  else {
    assert.ok('data' in payload, `${path} 缺少 data`)
    assert.ok(payload.request_id && payload.timestamp, `${path} 缺少响应元数据`)
  }
  checks++
  return payload.data
}

async function createUser() {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 20)
  const credentials = { login_identifier: `verify_${suffix}`, password: `Verify-${randomUUID()}` }
  await request('/auth/register', { method: 'POST', body: credentials, status: 201, auth: null })
  return request('/auth/login', { method: 'POST', body: credentials, auth: null })
}

try {
  await checkHealth(`${frontend}/`)
  await checkHealth(`${business}/actuator/health/readiness`, true)
  await checkHealth(`${ai}/health/ready`, true)
  const login = await createUser()
  token = login.access_token
  assert.ok(token)
  await request('/users/me')
  await request('/users/me', { auth: 'expired-or-invalid', status: 401, code: 'UNAUTHORIZED' })

  const createInput = { target_position: '原生流程验收工程师', skills: ['Java', 'MySQL', '并发'], difficulty: 'MEDIUM', question_count: 3 }
  const createKey = `verify-create-${randomUUID()}`
  const interview = await request('/interviews', { method: 'POST', body: createInput, key: createKey, status: 201 })
  const replay = await request('/interviews', { method: 'POST', body: createInput, key: createKey })
  assert.equal(replay.id, interview.id)
  await request('/interviews', { method: 'POST', body: { ...createInput, question_count: 5 }, key: createKey, status: 409, code: 'IDEMPOTENCY_KEY_REUSED' })
  const path = `/interviews/${interview.id}`
  await request(`${path}/current-question`, { status: 409, code: 'INTERVIEW_NOT_READY' })
  await request(`${path}/answers`, { method: 'POST', body: { question_id: randomUUID(), answer_content: '尚未开始' }, key: `verify-${randomUUID()}`, status: 409, code: 'ILLEGAL_INTERVIEW_STATE' })
  const startKey = `verify-start-${randomUUID()}`
  const started = await request(`${path}/start`, { method: 'POST', key: startKey })
  const startReplay = await request(`${path}/start`, { method: 'POST', key: startKey })
  assert.equal(startReplay.current_question.id, started.current_question.id)
  let question = started.current_question
  let firstSubmission
  let firstInput
  let firstKey
  for (let number = 1; number <= 3; number++) {
    const input = { question_id: question.id, answer_content: '我会明确业务边界和事务隔离级别，使用幂等键处理重复请求，通过监控和测试验证失败恢复与并发行为。' }
    const key = `verify-answer-${randomUUID()}`
    const result = await request(`${path}/answers`, { method: 'POST', body: input, key, status: 201 })
    assert.ok(result.evaluation.answer_overall_score >= 0 && result.evaluation.answer_overall_score <= 100)
    const replayed = await request(`${path}/answers`, { method: 'POST', body: input, key })
    assert.equal(replayed.answer.id, result.answer.id)
    if (number === 1) { firstSubmission = result; firstInput = input; firstKey = key }
    question = result.next_question
    assert.equal(result.interview_status, number === 3 ? 'COMPLETED' : 'IN_PROGRESS')
  }
  assert.equal((await request(`${path}/current-question`)).question, null)
  assert.equal((await request(`${path}/start`, { method: 'POST', key: startKey })).current_question.id, started.current_question.id)
  assert.equal((await request(`${path}/answers`, { method: 'POST', body: firstInput, key: firstKey })).answer.id, firstSubmission.answer.id)
  assert.equal((await request(`${path}/report`)).report_status, 'PENDING')
  const reportKey = `verify-report-${randomUUID()}`
  const report = await request(`${path}/report/retry`, { method: 'POST', key: reportKey })
  assert.equal(report.report_status, 'AVAILABLE')
  assert.equal(Date.parse((await request(`${path}/report/retry`, { method: 'POST', key: reportKey })).published_at), Date.parse(report.published_at))
  assert.equal((await request(`${path}/report`)).interview_overall_score, report.interview_overall_score)
  const page = await request('/interviews?page=1&page_size=1')
  assert.equal(page.total, 1)
  assert.equal(page.items[0].id, interview.id)
  assert.equal(page.items[0].status, 'COMPLETED')
  assert.equal((await request('/interviews?page=2&page_size=1')).items.length, 0)

  const cancelled = await request('/interviews', { method: 'POST', body: createInput, key: `verify-${randomUUID()}`, status: 201 })
  const cancelPath = `/interviews/${cancelled.id}`
  const cancelKey = `verify-cancel-${randomUUID()}`
  await request(`${cancelPath}/cancel`, { method: 'POST', key: cancelKey })
  await request(`${cancelPath}/cancel`, { method: 'POST', key: cancelKey })
  await request(`${cancelPath}/current-question`, { status: 409, code: 'ILLEGAL_INTERVIEW_STATE' })
  await request(`${cancelPath}/report`, { status: 409, code: 'INTERVIEW_NOT_COMPLETED' })

  const other = await createUser()
  for (const suffix of ['', '/report', '/current-question']) await request(`${path}${suffix}`, { auth: other.access_token, status: 403, code: 'FORBIDDEN' })
  assert.equal((await request('/interviews', { auth: other.access_token })).total, 0)
  console.log(`原生端到端验证通过：${checks} 项检查；注册、登录、创建、启动、三次答题、报告、分页、取消、权限隔离和幂等重放正常。`)
  console.log('验收记录已保留在独立 verify_ 测试账号下，不修改已有账号或面试。')
} catch (error) {
  console.error(`原生验证失败：${error.message}`)
  process.exitCode = 1
}
