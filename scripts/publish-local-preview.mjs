// Run only against the isolated local preview after seed-local-preview.sql.
import { readFileSync } from 'node:fs'

if (process.argv[2] !== '--confirm-local-preview') {
  throw new Error('Usage: node scripts/publish-local-preview.mjs --confirm-local-preview [video.mp4]')
}
const base = 'http://localhost:8080/api/v1'
const video = readFileSync(process.argv[3] ?? new URL('../target/preview-lesson.mp4', import.meta.url))
if (!video.length || video.length >= 10_000_000) {
  throw new Error('Demo video must be smaller than 10 MB')
}

async function login(role) {
  const response = await fetch(base + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: role + '@demo.local', password: '123456' }),
  })
  if (!response.ok) throw new Error(role + ' login failed: ' + response.status)
  return (await response.json()).accessToken
}

async function api(token, path, method = 'GET', body) {
  const response = await fetch(base + path, {
    method,
    headers: { Authorization: 'Bearer ' + token, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) {
    throw new Error(method + ' ' + path + ': ' + response.status + ' ' + await response.text())
  }
  return response.status === 204 || response.headers.get('content-length') === '0'
    ? undefined : response.json()
}

const lecturer = await login('lecture')
const leader = await login('leader')
const student = await login('student')
const demoSlugs = new Set([
  'java-tu-con-so-khong', 'react-va-typescript', 'spring-boot-api',
  'ung-dung-ai-trong-lap-trinh', 'docker-cho-lap-trinh-vien', 'clean-architecture',
])
const courses = await api(lecturer, '/instructor/courses')
for (const course of courses.filter(item => demoSlugs.has(item.slug))) {
  if (course.status === 'DRAFT') {
    const workspace = await api(lecturer, '/instructor/courses/' + course.id)
    for (const lesson of workspace.lessons) {
      if (lesson.contentUrl) continue
      const form = new FormData()
      form.set('file', new Blob([video], { type: 'video/mp4' }), 'preview-lesson.mp4')
      const upload = await fetch(base + '/instructor/courses/' + course.slug + '/lessons/' + lesson.id + '/media', {
        method: 'PUT',
        headers: { Authorization: 'Bearer ' + lecturer },
        body: form,
      })
      if (!upload.ok) {
        throw new Error('Upload ' + course.slug + '/' + lesson.id + ': ' + upload.status + ' ' + await upload.text())
      }
    }
    await api(lecturer, '/instructor/courses/' + course.id + '/status', 'PATCH', { status: 'PENDING_REVIEW' })
  }
  if (course.status === 'DRAFT' || course.status === 'PENDING_REVIEW') {
    await api(leader, '/instructor/courses/' + course.id + '/status', 'PATCH', { status: 'PUBLISHED' })
    process.stdout.write('Published demo course ' + course.slug + '\n')
  }
}
await api(student, '/courses/java-tu-con-so-khong/enrollments', 'POST')
process.stdout.write('Enrolled demo student in Java course\n')
