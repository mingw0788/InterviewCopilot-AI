import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { existsSync, mkdirSync, mkdtempSync } from 'node:fs'
import { createServer } from 'node:net'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const base = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173/'
assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(new URL(base).hostname), '仅允许验收本机页面。')
assert.equal(typeof WebSocket, 'function', '浏览器验收需要 Node.js 22.12+。')
const executable = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find((path) => path && existsSync(path))
assert.ok(executable, '请通过 CHROME_PATH 指定已安装的 Chrome 或 Edge。')
mkdirSync(join(root, '.tmp'), { recursive: true })
const profile = mkdtempSync(join(root, '.tmp', 'browser-acceptance-'))
const portServer = createServer()
await new Promise((resolve) => portServer.listen(0, '127.0.0.1', resolve))
const port = portServer.address().port
await new Promise((resolve) => portServer.close(resolve))
const browser = spawn(executable, ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-address=127.0.0.1', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' })
let browserStartError
browser.on('error', (error) => { browserStartError = error })
let socket
let sequence = 0
const pending = new Map()
const errors = []
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function cdp(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`浏览器操作超时：${method}`)) }, 30_000)
    timer.unref()
    pending.set(id, { resolve: (value) => { clearTimeout(timer); resolve(value) }, reject: (reason) => { clearTimeout(timer); reject(reason) } })
    socket.send(JSON.stringify({ id, method, params }))
  })
}

async function evaluate(expression) {
  const result = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description ?? result.exceptionDetails.text)
  return result.result.value
}

async function waitFor(expression) {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return
    await delay(100)
  }
  throw new Error(`页面等待超时：${expression}；页面：${await evaluate('document.body.innerText.slice(-700)')}`)
}

async function click(text) {
  await evaluate(`(() => { const button = [...document.querySelectorAll('button')].find(b => b.textContent.includes(${JSON.stringify(text)})); if (!button || button.disabled) throw new Error('按钮不可用：'+${JSON.stringify(text)}); button.click(); })()`)
}

async function fill(selector, value) {
  await evaluate(`(() => { const input = document.querySelector(${JSON.stringify(selector)}); if (!input) throw new Error('找不到输入框'); input.value = ${JSON.stringify(value)}; input.dispatchEvent(new Event('input', {bubbles:true})); input.dispatchEvent(new Event('change', {bubbles:true})); })()`)
}

try {
  let targets
  for (let attempt = 0; attempt < 100; attempt++) {
    if (browserStartError) throw browserStartError
    try { targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); break } catch { await delay(100) }
  }
  assert.ok(targets?.length, '浏览器未能启动。')
  socket = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }) })
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const promise = pending.get(message.id)
      pending.delete(message.id)
      if (message.error) promise.reject(new Error(message.error.message))
      else promise.resolve(message.result)
    }
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.exception?.description ?? message.params.exceptionDetails.text)
    if (message.method === 'Runtime.consoleAPICalled' && message.params.type === 'error') errors.push(message.params.args.map((arg) => arg.value ?? arg.description).join(' '))
  })
  await cdp('Runtime.enable')
  await cdp('Page.enable')
  await cdp('Page.navigate', { url: base })
  await waitFor("document.body.innerText.includes('进入工作区')")
  await click('立即注册')
  const username = `browser_${randomUUID().replaceAll('-', '').slice(0, 20)}`
  const password = `Browser-${randomUUID()}`
  await fill('input[autocomplete="username"]', username)
  await fill('input[type="password"]', password)
  await click('创建账号')
  await waitFor("document.body.innerText.includes('账号创建成功')")
  await fill('input[type="password"]', password)
  await click('进入工作区')
  await waitFor("document.body.innerText.includes('暂无面试记录')")
  await fill('.setup-card input', '浏览器验收工程师')
  await fill('.setup-card input[placeholder]', 'Java，MySQL、并发')
  await fill('.field-row label:last-child select', '3')
  await click('创建并开始面试')
  await waitFor("document.querySelector('textarea') !== null")
  const draft = '这是一份刷新后需要恢复的回答草稿。'
  await fill('textarea', draft)
  await cdp('Page.reload')
  await waitFor("document.body.innerText.includes('最近的面试') && document.body.innerText.includes('继续')")
  await click('继续')
  await waitFor("document.querySelector('textarea') !== null")
  assert.equal(await evaluate("document.querySelector('textarea').value"), draft)
  for (let number = 1; number <= 3; number++) {
    await fill('textarea', '我会明确事务边界和隔离级别，用幂等键处理重复提交，通过监控、异常恢复和并发测试验证业务的一致性。')
    await click('提交回答')
    if (number < 3) await waitFor(`document.body.innerText.includes('第 ${number + 1} 题 / 共 3 题') && !document.body.innerText.includes('评估中…')`)
    else await waitFor("document.body.innerText.includes('综合得分')")
  }
  await click('生成报告')
  await waitFor("document.body.innerText.includes('总体评价')")
  await click('工作区')
  await waitFor("document.body.innerText.includes('查看报告')")
  await click('查看报告')
  await waitFor("document.body.innerText.includes('总体评价')")
  await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  assert.ok(await evaluate('document.documentElement.scrollWidth <= window.innerWidth'), '手机页面出现横向溢出。')
  await click('退出登录')
  await waitFor("document.body.innerText.includes('进入工作区')")
  assert.deepEqual(errors, [], '浏览器出现运行时错误。')
  console.log('浏览器验收通过：中文注册登录、历史渲染、中文技能分隔、答题草稿刷新恢复、三题面试、报告生成和回看、手机布局及退出登录；无运行时错误。')
} catch (error) {
  console.error(`浏览器验收失败：${error.message}`)
  process.exitCode = 1
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try { await cdp('Browser.close') } catch { }
    socket.close()
  } else browser.kill()
}
