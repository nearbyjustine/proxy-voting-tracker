// Run with the full stack up (docker compose --profile full up -d):  cd e2e && npm install && npm test
import puppeteer from 'puppeteer-core'
const shots = process.argv[2]
const csv = process.argv[3]
const BASE = 'http://localhost:8091'
const browser = await puppeteer.launch({ executablePath: process.env.CHROME_PATH ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new' })
let page
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
async function login(user) {
  const ctx = await browser.createBrowserContext(); page = await ctx.newPage(); await page.setViewport({ width: 1280, height: 900 })
  await page.goto(BASE + '/', { waitUntil: 'networkidle0' })
  await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle0' }), page.click('main button')])
  await page.type('#username', user); await page.type('#password', user + '123')
  await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle0' }), page.click('#kc-login')])
  await page.waitForFunction(() => document.querySelector('h1')?.textContent?.includes('Welcome'))
  await sleep(400)
  console.log(user, '|', await page.$eval('main', (e) => e.innerText.split('\n').slice(0, 2).join(' / ')))
}
const nav = () => page.$$eval('.topbar nav a', (a) => a.map((x) => x.textContent))

await login('sam')
console.log('sam nav:', await nav())
await page.goto(BASE + '/meetings', { waitUntil: 'networkidle0' }); await page.waitForSelector('tbody tr')
console.log('meetings:', await page.$$eval('tbody tr', (r) => r.map((x) => x.innerText.split('\t')[0] + ' ' + x.innerText.split('\t')[3])))
await page.screenshot({ path: shots + '/meetings.png', fullPage: true })
const link = await page.$$eval('tbody tr', (rows) => rows.find((r) => r.innerText.includes('ORCH')).querySelector('a').getAttribute('href'))
await page.goto(BASE + link, { waitUntil: 'networkidle0' }); await page.waitForSelector('.proposal')
const firstVote = await page.$$('.proposal .votes button'); await firstVote[1].click()   // AGAINST on proposal 1
// look at the vote column only (a cached summary also has a <small> label, so don't grab the first one)
await page.waitForFunction(() => [...document.querySelector('.proposal .grid3').querySelectorAll('small')].some((s) => s.textContent.includes('sam')))
const sumBtn = await page.$('.proposal button.ghost.small'); if (sumBtn) { await sumBtn.click(); await page.waitForSelector('.summary') }
console.log('ORCH proposal 1:', await page.$eval('.proposal', (e) => e.innerText.replace(/\s+/g, ' ').slice(0, 330)))
await page.screenshot({ path: shots + '/meeting-detail.png', fullPage: true })
await page.goto(BASE + '/policy', { waitUntil: 'networkidle0' }); await page.waitForSelector('.rule')
await page.screenshot({ path: shots + '/policy.png', fullPage: true })
await page.goto(BASE + '/ingest', { waitUntil: 'networkidle0' })
const input = await page.$('input[type=file]'); await input.uploadFile(csv)
await page.click('main button'); await page.waitForSelector('.banner.ok')
console.log('ingest:', await page.$eval('.banner.ok', (e) => e.textContent))
let found = false
for (let i = 0; i < 20 && !found; i++) { await sleep(3000); await page.goto(BASE + '/meetings', { waitUntil: 'networkidle0' }); found = (await page.content()).includes('Harbor') }
console.log('uploaded meeting visible in dashboard:', found)
await page.goto(BASE + '/audit', { waitUntil: 'networkidle0' }); await page.waitForSelector('tbody tr')
console.log('audit top:', await page.$$eval('tbody tr', (r) => r.slice(0, 2).map((x) => x.innerText.split('\t').slice(1, 3).join(' '))))
await page.screenshot({ path: shots + '/audit.png', fullPage: true })
await page.browserContext().close()

await login('vic')
console.log('vic nav:', await nav())
await page.select('select.lang', 'fr'); await sleep(300)
await page.goto(BASE + '/meetings', { waitUntil: 'networkidle0' }); await page.waitForSelector('tbody tr')
console.log('vic fr heading:', await page.$eval('h1', (e) => e.textContent), '| first status:', await page.$eval('tbody tr .badge', (e) => e.textContent))
await page.goto(BASE + '/policy', { waitUntil: 'networkidle0' }); console.log('vic /policy ends at', new URL(page.url()).pathname)
await page.browserContext().close()

await login('gina')
await page.goto(BASE + link, { waitUntil: 'networkidle0' }); await page.waitForSelector('.proposal')
console.log('gina sees ORCH p1 vote by STEWARD?', (await page.$eval('.proposal', (e) => e.innerText)).includes('sam'))
await browser.close()
