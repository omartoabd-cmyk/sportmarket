// Optional maintainer tool: node scripts/capture-screenshots.cjs <Playwright module path> <isolated demo URL>
// Creates a fictional account/listings: use only a disposable demo database.
const { chromium } = require(process.argv[2]);
const { randomBytes } = require('node:crypto');
const { mkdirSync } = require('node:fs');
const base = process.argv[3];
if (!base) throw new Error('An explicit isolated demo URL is required.');
(async () => {
  mkdirSync('docs/screenshots', { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const ready = async () => {
      await page.waitForLoadState('networkidle');
      await page.locator('img').evaluateAll(images => Promise.all(images
        .filter(image => image.complete || image.loading !== 'lazy')
        .map(image => image.decode().catch(() => {}))));
    };
    await page.goto(base + '/?sort=oldest');
    await page.getByRole('heading', { name: 'Match-day soccer ball', exact: true }).waitFor();
    await ready();
    await page.screenshot({ path: 'docs/screenshots/marketplace.png', fullPage: true });
    await page.getByRole('heading', { name: 'Match-day soccer ball', exact: true }).click();
    await page.getByRole('heading', { name: 'Match-day soccer ball', exact: true, level: 1 }).waitFor();
    await ready();
    await page.screenshot({ path: 'docs/screenshots/listing-details.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 950 });
    await page.goto(base + '/?sort=oldest');
    await page.getByRole('heading', { name: 'Match-day soccer ball', exact: true }).waitFor();
    await ready();
    await page.screenshot({ path: 'docs/screenshots/mobile.png' });
    await page.setViewportSize({ width: 1440, height: 1080 });
    const email = 'screenshot-' + randomBytes(8).toString('hex') + '@example.test';
    const password = randomBytes(24).toString('base64url');
    const registration = await page.request.post(base + '/api/auth/register', {
      data: { fullName: 'Jamie Parker', email, password }
    });
    if (!registration.ok()) throw new Error('Screenshot account registration failed.');
    const login = await page.request.post(base + '/api/auth/login', { data: { email, password } });
    const token = (await login.json()).accessToken;
    const headers = { Authorization: 'Bearer ' + token };
    const catalogue = await (await page.request.get(base + '/api/listings?sort=oldest&size=3')).json();
    for (const source of catalogue.content) {
      const created = await page.request.post(base + '/api/listings', { headers, data: {
        title: source.title, description: source.description, price: source.price,
        condition: source.condition, categoryId: source.category.id
      }});
      if (!created.ok()) throw new Error('Screenshot listing creation failed.');
      const listing = await created.json();
      const photo = await page.request.get(base + source.images[0].url);
      const uploaded = await page.request.post(base + `/api/listings/${listing.id}/images`, {
        headers, multipart: { file: { name: 'demo.png', mimeType: 'image/png', buffer: await photo.body() } }
      });
      if (!uploaded.ok()) throw new Error('Screenshot photo upload failed.');
    }
    await page.goto(base + '/login');
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Login', exact: true }).click();
    await page.getByRole('link', { name: 'My listings', exact: true }).click();
    await page.getByRole('heading', { name: 'My listings.' }).waitFor();
    await page.getByRole('link', { name: 'Edit', exact: true }).first().waitFor();
    await ready();
    await page.screenshot({ path: 'docs/screenshots/my-listings.png', fullPage: true });
    if (errors.length) throw new Error(errors.join('; '));
    console.log('Captured desktop, mobile, details, and owner dashboard from the real isolated backend.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
