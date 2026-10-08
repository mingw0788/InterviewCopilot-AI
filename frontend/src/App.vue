<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { ApiError, businessApi, type Difficulty, type Evaluation, type Interview, type InterviewListItem, type PublicQuestion, type Report, type User } from './api'
import { readDraft, saveDraft } from './drafts'

type Screen = 'auth' | 'dashboard' | 'interview' | 'report'

const screen = ref<Screen>('auth')
const authMode = ref<'login' | 'register'>('login')
const user = ref<User | null>(null)
const interviews = ref<InterviewListItem[]>([])
const historyPage = ref(1)
const historyTotal = ref(0)
const historyPageSize = 10
const historyLoaded = ref(false)
const activeInterview = ref<Interview | null>(null)
const currentQuestion = ref<PublicQuestion | null>(null)
const answer = ref('')
const lastEvaluation = ref<Evaluation | null>(null)
const report = ref<Report | null>(null)
const loading = ref(false)
const booting = ref(true)
const error = ref('')
const notice = ref('')

const authForm = ref({ loginIdentifier: '', password: '' })
const setupForm = ref({ targetPosition: 'Java 后端工程师', skills: 'Java, Spring Boot, MySQL', difficulty: 'MEDIUM' as Difficulty, questionCount: 5 })

const isAuthenticated = computed(() => user.value !== null)
const historyPages = computed(() => Math.max(1, Math.ceil(historyTotal.value / historyPageSize)))
const progressLabel = computed(() => {
  if (!activeInterview.value || !currentQuestion.value) return ''
  return `第 ${currentQuestion.value.question_number} 题 / 共 ${activeInterview.value.question_count} 题`
})

function clearMessages() {
  error.value = ''
  notice.value = ''
}

function showError(reason: unknown) {
  if (reason instanceof ApiError && reason.status === 401 && user.value) signOut()
  error.value = reason instanceof ApiError ? reason.message : '发生了一点问题，请稍后重试。'
}

watch(answer, (value) => {
  if (user.value && currentQuestion.value) saveDraft(user.value.id, currentQuestion.value.id, value)
}, { flush: 'sync' })

function setQuestion(question: PublicQuestion | null) {
  currentQuestion.value = null
  answer.value = ''
  currentQuestion.value = question
  answer.value = user.value && question ? readDraft(user.value.id, question.id) : ''
}

function syncHistory(interview: Interview) {
  interviews.value = interviews.value.map((item) => item.id === interview.id ? interview : item)
}

function statusLabel(status: string): string {
  return ({ CREATED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消', FAILED: '失败', PENDING: '等待生成', GENERATING: '生成中', AVAILABLE: '已生成', FAILED_RETRYABLE: '可重试' } as Record<string, string>)[status] ?? status
}

function difficultyLabel(difficulty: string): string {
  return ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' } as Record<string, string>)[difficulty] ?? difficulty
}

async function run(action: () => Promise<void>) {
  if (loading.value) return
  clearMessages()
  loading.value = true
  try { await action() } catch (reason) { showError(reason) } finally { loading.value = false }
}

async function loadHistory(page = historyPage.value) {
  const result = await businessApi.listInterviews(page, historyPageSize)
  interviews.value = result.items
  historyPage.value = result.page
  historyTotal.value = result.total
  historyLoaded.value = true
}

async function signIn() {
  await run(async () => {
    const result = await businessApi.login(authForm.value.loginIdentifier.trim(), authForm.value.password)
    user.value = result.user
    authForm.value.password = ''
    screen.value = 'dashboard'
    await loadHistory(1)
  })
}

async function register() {
  await run(async () => {
    await businessApi.register(authForm.value.loginIdentifier.trim(), authForm.value.password)
    authMode.value = 'login'
    authForm.value.password = ''
    notice.value = '账号创建成功，请登录后开始面试。'
  })
}

function signOut() {
  businessApi.setToken(null)
  user.value = null
  screen.value = 'auth'
  interviews.value = []
  historyLoaded.value = false
  historyTotal.value = 0
  historyPage.value = 1
  activeInterview.value = null
  currentQuestion.value = null
  answer.value = ''
  lastEvaluation.value = null
  report.value = null
  authForm.value.password = ''
}

async function createInterview() {
  await run(async () => {
    const skills = [...new Set(setupForm.value.skills.split(/[,，、;；\n]/).map((skill) => skill.trim()).filter(Boolean))]
    if (!setupForm.value.targetPosition.trim()) throw new ApiError('请填写目标职位。', 400)
    if (!skills.length || skills.length > 20 || skills.some((skill) => skill.length > 50)) throw new ApiError('请填写 1–20 项技能，每项不超过 50 个字符。', 400)
    const interview = await businessApi.createInterview({ target_position: setupForm.value.targetPosition.trim(), skills, difficulty: setupForm.value.difficulty, question_count: setupForm.value.questionCount })
    await loadHistory(1)
    await enterInterview(interview)
  })
}

async function beginInterview(item: InterviewListItem) {
  await run(async () => { await enterInterview(await businessApi.getInterview(item.id)) })
}

async function enterInterview(interview: Interview) {
    activeInterview.value = interview
    lastEvaluation.value = null
    setQuestion(null)
    if (interview.status === 'CREATED') {
      const result = await businessApi.startInterview(interview.id)
      setQuestion(result.current_question)
      activeInterview.value = { ...interview, status: 'IN_PROGRESS', current_question_number: result.current_question.question_number }
    } else if (interview.status === 'IN_PROGRESS') {
      const result = await businessApi.currentQuestion(interview.id)
      if (result.interview_status === 'COMPLETED') { await enterReport(interview); return }
      setQuestion(result.question ? { id: result.question.id, question_number: result.question_number ?? 1, question: result.question.question, topic: result.question.topic, difficulty: result.question.difficulty, question_type: result.question.question_type } : null)
    } else if (interview.status === 'COMPLETED') {
      await enterReport(interview)
      return
    } else { throw new ApiError('这场面试已结束，请返回工作区。', 409) }
    syncHistory(activeInterview.value!)
    screen.value = 'interview'
}

async function submitAnswer() {
  if (!activeInterview.value || !currentQuestion.value || !answer.value.trim()) return
  await run(async () => {
    const result = await businessApi.submitAnswer(activeInterview.value!.id, currentQuestion.value!.id, answer.value.trim())
    lastEvaluation.value = result.evaluation
    answer.value = ''
    if (result.interview_status === 'COMPLETED') {
      activeInterview.value = { ...activeInterview.value!, status: 'COMPLETED', current_question_number: null, interview_overall_score: null }
      interviews.value = interviews.value.map((item) => item.id === activeInterview.value!.id ? activeInterview.value! : item)
      setQuestion(null)
      await enterReport(activeInterview.value!)
    } else {
      setQuestion(result.next_question)
      activeInterview.value = { ...activeInterview.value!, current_question_number: result.next_question?.question_number ?? null }
      syncHistory(activeInterview.value)
    }
  })
}

async function cancelInterview() {
  if (!activeInterview.value || !window.confirm('确定取消这场面试吗？当前进度会保留为已取消。')) return
  await run(async () => {
    await businessApi.cancelInterview(activeInterview.value!.id)
    notice.value = '面试已取消。'
    screen.value = 'dashboard'
    activeInterview.value = null
    currentQuestion.value = null
    answer.value = ''
    await loadHistory()
  })
}

async function openReport(item: InterviewListItem) {
  await run(async () => { await enterReport(await businessApi.getInterview(item.id)) })
}

async function enterReport(interview: Interview) {
  activeInterview.value = { ...interview, status: 'COMPLETED' }
  report.value = null
  screen.value = 'report'
  report.value = await businessApi.getReport(interview.id)
  activeInterview.value.interview_overall_score = report.value.interview_overall_score
  syncHistory(activeInterview.value)
}

async function refreshReport() {
  if (!activeInterview.value) return
  await run(async () => { report.value = await businessApi.getReport(activeInterview.value!.id) })
}

async function retryReport() {
  if (!activeInterview.value) return
  await run(async () => { report.value = await businessApi.retryReport(activeInterview.value!.id) })
}

onMounted(async () => {
  if (businessApi.hasToken()) {
    try {
      user.value = await businessApi.me()
      screen.value = 'dashboard'
      await loadHistory()
    } catch (reason) { showError(reason) }
  }
  booting.value = false
})
</script>

<template>
  <main class="app-shell">
    <div class="ambient ambient-one"></div><div class="ambient ambient-two"></div>
    <header class="topbar">
      <a class="brand" href="#" @click.prevent="!loading && (screen = isAuthenticated ? 'dashboard' : 'auth')"><span class="brand-mark">IC</span><span>Interview<span>Copilot</span></span></a>
      <div v-if="isAuthenticated" class="topbar-user"><span class="user-dot"></span>{{ user?.login_identifier }}<button class="text-button" type="button" :disabled="loading" @click="signOut">退出登录</button></div>
    </header>

    <div v-if="booting" class="loading-state">正在恢复工作区…</div>

    <section v-else-if="screen === 'auth'" class="auth-layout">
      <div class="hero-copy"><p class="eyebrow">AI 面试练习</p><h1>让每一次回答，都为下一场面试做好准备。</h1><p class="hero-subtitle">专为后端工程师打造的面试练习流程。当前使用本地模拟题目和评分，可体验逐题问答与报告。</p><div class="hero-points"><span><b>01</b> 分级练习</span><span><b>02</b> 四维度反馈</span><span><b>03</b> 可执行报告</span></div></div>
      <form class="panel auth-card" @submit.prevent="authMode === 'login' ? signIn() : register()"><div class="panel-heading"><p class="eyebrow">{{ authMode === 'login' ? '欢迎回来' : '创建账号' }}</p><h2>{{ authMode === 'login' ? '准备好就开始。' : '开启你的练习流程。' }}</h2></div><label>用户名<input v-model="authForm.loginIdentifier" required minlength="3" maxlength="50" autocomplete="username" placeholder="例如 candidate01" /></label><label>密码<input v-model="authForm.password" required minlength="8" maxlength="128" type="password" :autocomplete="authMode === 'register' ? 'new-password' : 'current-password'" placeholder="至少 8 个字符" /></label><button class="primary-button" :disabled="loading" type="submit">{{ loading ? '处理中…' : authMode === 'login' ? '进入工作区' : '创建账号' }}</button><p class="switch-copy">{{ authMode === 'login' ? '还没有账号？' : '已经有账号？' }} <button class="text-button" type="button" :disabled="loading" @click="clearMessages(); authMode = authMode === 'login' ? 'register' : 'login'">{{ authMode === 'login' ? '立即注册' : '登录' }}</button></p></form>
    </section>

    <section v-else-if="screen === 'dashboard'" class="workspace"><div class="workspace-heading"><div><p class="eyebrow">你的工作区</p><h1>养成高质量回答的习惯。</h1><p class="muted">设定目标职位，逐题完成练习，再查看你的表现反馈。</p></div><span class="status-pill" :class="historyLoaded ? 'active' : 'pending'">{{ historyLoaded ? '记录已加载' : '记录未加载' }}</span></div><div class="dashboard-grid"><form class="panel setup-card" @submit.prevent="createInterview"><div class="card-title"><span class="step-number">01</span><div><p class="eyebrow">新建面试</p><h2>设置你的面试</h2></div></div><label>目标职位<input v-model="setupForm.targetPosition" required maxlength="100" /></label><label>技能 <span class="label-hint">支持中文、英文逗号</span><input v-model="setupForm.skills" required placeholder="Java, Spring Boot, MySQL" /></label><div class="field-row"><label>难度<select v-model="setupForm.difficulty"><option value="EASY">简单</option><option value="MEDIUM">中等</option><option value="HARD">困难</option></select></label><label>题目数量<select v-model.number="setupForm.questionCount"><option :value="3">3 题</option><option :value="5">5 题</option><option :value="7">7 题</option><option :value="10">10 题</option></select></label></div><button class="primary-button" :disabled="loading" type="submit">{{ loading ? '准备中…' : '创建并开始面试' }} <span>→</span></button></form><div class="panel history-card"><div class="card-title"><span class="step-number muted-step">02</span><div><p class="eyebrow">历史记录</p><h2>最近的面试</h2></div><button class="icon-button" type="button" aria-label="刷新面试记录" :disabled="loading" @click="run(() => loadHistory())">↻</button></div><div v-if="interviews.length" class="history-list"><article v-for="item in interviews" :key="item.id" class="history-item"><div class="history-main"><span class="history-icon">{{ item.status === 'COMPLETED' ? '✓' : item.status === 'CANCELLED' ? '—' : '◌' }}</span><div><strong>{{ item.target_position }}</strong><small>{{ difficultyLabel(item.difficulty) }} · {{ item.question_count }} 题 · {{ new Date(item.created_at).toLocaleDateString('zh-CN') }}</small></div></div><div class="history-side"><span class="status-pill" :class="item.status.toLowerCase()">{{ statusLabel(item.status) }}</span><button v-if="item.status === 'CREATED' || item.status === 'IN_PROGRESS'" class="small-button" type="button" :disabled="loading" @click="beginInterview(item)">{{ item.status === 'CREATED' ? '开始' : '继续' }}</button><button v-else-if="item.status === 'COMPLETED'" class="small-button" type="button" :disabled="loading" @click="openReport(item)">查看报告</button></div></article></div><div v-else class="empty-state"><span>✦</span><p>{{ historyLoaded ? '暂无面试记录。' : '面试记录尚未加载。' }}</p><small>{{ historyLoaded ? '你的第一次练习即将开始。' : '点击右上角刷新按钮重试。' }}</small></div><nav v-if="historyTotal > historyPageSize" class="pagination" aria-label="面试记录分页"><button class="small-button" :disabled="loading || historyPage <= 1" @click="run(() => loadHistory(historyPage - 1))">上一页</button><span>第 {{ historyPage }} / {{ historyPages }} 页 · 共 {{ historyTotal }} 场</span><button class="small-button" :disabled="loading || historyPage >= historyPages" @click="run(() => loadHistory(historyPage + 1))">下一页</button></nav></div></div></section>

    <section v-else-if="screen === 'interview'" class="interview-layout"><div class="interview-topline"><button class="back-button" type="button" :disabled="loading" @click="screen = 'dashboard'">← 工作区</button><span class="status-pill active">进行中</span></div><div class="interview-header"><div><p class="eyebrow">{{ activeInterview?.target_position }}</p><h1>{{ progressLabel }}</h1></div><button class="danger-button" type="button" :disabled="loading" @click="cancelInterview">取消面试</button></div><div class="progress-track"><span :style="{ width: `${((currentQuestion?.question_number ?? 1) / (activeInterview?.question_count ?? 1)) * 100}%` }"></span></div><div v-if="currentQuestion" class="question-grid"><article class="question-card"><div class="question-meta"><span>第 {{ currentQuestion.question_number }} 题</span><span>{{ currentQuestion.topic }}</span><span>{{ difficultyLabel(currentQuestion.difficulty) }}</span></div><h2>{{ currentQuestion.question }}</h2><p class="question-hint">先梳理你的思路再作答。具体案例通常比笼统描述更有说服力。</p></article><form class="answer-card" @submit.prevent="submitAnswer"><label>你的回答 <span class="label-hint">草稿自动保存在当前浏览器标签页</span><textarea v-model="answer" required maxlength="8000" rows="10" placeholder="请说明你的思路、权衡和实际案例…"></textarea></label><div class="answer-footer"><span>{{ answer.length }} / 8000</span><button class="primary-button" :disabled="loading || !answer.trim()" type="submit">{{ loading ? '评估中…' : '提交回答' }} <span>→</span></button></div></form></div><div v-if="lastEvaluation" class="evaluation-card"><div class="evaluation-heading"><div><p class="eyebrow">上一题评估结果</p><h2>{{ lastEvaluation.answer_overall_score.toFixed(2) }} <small>/ 100</small></h2></div><div class="score-bars"><span :style="{ '--score': `${lastEvaluation.accuracy}%` }">准确性 <b>{{ lastEvaluation.accuracy }}</b></span><span :style="{ '--score': `${lastEvaluation.completeness}%` }">完整性 <b>{{ lastEvaluation.completeness }}</b></span><span :style="{ '--score': `${lastEvaluation.depth}%` }">深度 <b>{{ lastEvaluation.depth }}</b></span><span :style="{ '--score': `${lastEvaluation.clarity}%` }">表达清晰度 <b>{{ lastEvaluation.clarity }}</b></span></div></div><p class="feedback">{{ lastEvaluation.feedback }}</p><div class="feedback-columns"><div><b>做得好的地方</b><ul><li v-for="item in lastEvaluation.strengths" :key="item">{{ item }}</li></ul></div><div><b>下次可以改进</b><ul><li v-for="item in lastEvaluation.missing_points" :key="item">{{ item }}</li></ul></div></div></div></section>

    <section v-else class="report-layout"><div class="interview-topline"><button class="back-button" type="button" :disabled="loading" @click="screen = 'dashboard'">← 工作区</button><span class="status-pill completed">已完成</span></div><div class="report-heading"><div><p class="eyebrow">面试报告</p><h1>{{ activeInterview?.target_position }}</h1><p class="muted">清晰了解你的回答表现，以及下一步应该提升的方向。</p></div><button v-if="!report || report.report_status !== 'AVAILABLE'" class="small-button" :disabled="loading" type="button" @click="refreshReport">{{ loading ? '加载中…' : report ? '刷新报告状态' : '重新加载报告' }}</button><button v-if="report && report.report_status !== 'AVAILABLE'" class="primary-button" :disabled="loading || report.report_status === 'GENERATING'" type="button" @click="retryReport">{{ loading ? '生成中…' : report.report_status === 'FAILED_RETRYABLE' ? '重试生成报告' : '生成报告' }} <span>→</span></button></div><div v-if="report" class="report-grid"><article class="panel score-card"><p class="eyebrow">综合得分</p><strong>{{ report.interview_overall_score.toFixed(2) }}</strong><span>满分 100</span><div class="metric-list"><div><span>准确性</span><b>{{ report.average_accuracy }}</b></div><div><span>完整性</span><b>{{ report.average_completeness }}</b></div><div><span>深度</span><b>{{ report.average_depth }}</b></div><div><span>表达清晰度</span><b>{{ report.average_clarity }}</b></div></div></article><article class="panel narrative-card"><div class="report-status"><span class="status-pill" :class="report.report_status.toLowerCase()">{{ statusLabel(report.report_status) }}</span><span>{{ report.completed_question_count }} / {{ report.question_count }} 题</span></div><div v-if="report.report_status === 'AVAILABLE'" class="narrative-content"><div><p class="eyebrow">优势</p><p>{{ report.strength_summary }}</p></div><div><p class="eyebrow">改进方向</p><p>{{ report.weakness_summary }}</p></div><div><p class="eyebrow">下一步建议</p><ul><li v-for="item in report.improvement_suggestions ?? []" :key="item">{{ item }}</li></ul></div><div><p class="eyebrow">总体评价</p><p>{{ report.overall_comment }}</p></div></div><div v-else class="pending-report"><span class="pending-icon">◌</span><h2>你的报告正在准备中。</h2><p>确定性分数已经准备好。准备好后点击生成报告，查看详细的 AI 总结。</p></div></article></div></section>

    <div v-if="error" class="toast error-toast" role="alert">{{ error }}<button type="button" @click="error = ''">×</button></div><div v-if="notice" class="toast notice-toast" role="status">{{ notice }}<button type="button" @click="notice = ''">×</button></div><footer><span>InterviewCopilot AI</span><span>本地模拟练习 · 题目和评分为模拟结果，仅用于体验流程</span></footer>
  </main>
</template>
