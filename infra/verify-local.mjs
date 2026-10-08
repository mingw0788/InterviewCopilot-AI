import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const infraDirectory = dirname(fileURLToPath(import.meta.url))
const projectDirectory = dirname(infraDirectory)
const environmentFile = join(projectDirectory, '.env')
const keepRunning = process.argv.includes('--keep-running')

function runDockerCompose(args, captureOutput = false) {
  const result = spawnSync(
    'docker',
    ['compose', '--project-directory', projectDirectory, '--env-file', environmentFile, ...args],
    {
      cwd: projectDirectory,
      encoding: 'utf8',
      stdio: captureOutput ? 'pipe' : 'inherit',
    },
  )

  if (result.error) {
    throw new Error(`Unable to run Docker Compose: ${result.error.message}`)
  }
  if (result.status !== 0) {
    const detail = captureOutput ? `\n${result.stderr || result.stdout}` : ''
    throw new Error(`Docker Compose exited with code ${result.status}.${detail}`)
  }

  return result.stdout
}

async function expectHealthy(url, label) {
  const response = await fetch(url, { signal: AbortSignal.timeout(5_000) })
  if (!response.ok) {
    throw new Error(`${label} returned HTTP ${response.status}.`)
  }
}

function getPublishedAddress(service, containerPort) {
  const output = runDockerCompose(['port', service, String(containerPort)], true).trim()
  const addresses = output.split(/\r?\n/).filter(Boolean)
  if (addresses.length !== 1) {
    throw new Error(`Expected one published address for ${service}, received: ${output}`)
  }
  return addresses[0]
}

if (!existsSync(environmentFile)) {
  throw new Error('Missing .env. Copy .env.example to .env and set all required secrets.')
}

runDockerCompose(['version'], true)

let startupAttempted = false
let environmentVerified = false
let primaryError
try {
  startupAttempted = true
  runDockerCompose(['up', '--build', '--detach', '--wait', '--wait-timeout', '300'])

  const businessAddress = getPublishedAddress('business-service', 8080)
  const frontendAddress = getPublishedAddress('frontend', 5173)
  await expectHealthy(
    `http://${businessAddress}/actuator/health/readiness`,
    'Business service readiness endpoint',
  )
  await expectHealthy(`http://${frontendAddress}`, 'Frontend')

  console.log('Local environment is healthy: MySQL and all three application containers are ready.')
  environmentVerified = true
} catch (error) {
  primaryError = error
  throw error
} finally {
  if (startupAttempted && (!keepRunning || !environmentVerified)) {
    try {
      runDockerCompose(['down'])
    } catch (cleanupError) {
      if (!primaryError) {
        throw cleanupError
      }
      console.error(`Cleanup also failed: ${cleanupError.message}`)
    }
  }
}
