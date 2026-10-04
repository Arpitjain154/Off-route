import { chromium, expect } from '@playwright/test';
import assert from 'node:assert/strict';

// Uses the existing Vite server. Every account operation stays inside this mock.
const origin = 'http://127.0.0.1:5173';
const fixture = `<!doctype html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body><div id="root"></div><script type="module">
import RefreshRuntime from '/@react-refresh';
RefreshRuntime.injectIntoGlobalHook(window);
window.$RefreshReg$ = () => {};
window.$RefreshSig$ = () => type => type;
window.__vite_plugin_react_preamble_installed__ = true;
const { Capacitor, registerPlugin } = await import('/node_modules/.vite/deps/@capacitor_core.js');
const harness = window.cloudAuthTest = { calls: [], refreshes: 0 };
registerPlugin('CloudPlugin', { web: async () => ({
  async signIn(credentials) {
    harness.calls.push({ action: 'signIn', ...credentials });
    throw new Error('Incorrect email or password. Choose Create account first.');
  },
  async signUp(credentials) {
    harness.calls.push({ action: 'signUp', ...credentials });
    return new Promise(resolve => {
      harness.finishSignup = () => resolve({ message: 'Check your email to confirm the account, then sign in.' });
    });
  },
}) });
Capacitor.isNativePlatform = () => true;
const [{ default: React }, { default: ReactDOM }, { default: CloudSettings }, { initialCloud }] = await Promise.all([
  import('/node_modules/.vite/deps/react.js'),
  import('/node_modules/.vite/deps/react-dom_client.js'),
  import('/src/CloudSettings.tsx'),
  import('/src/cloud.ts'),
]);
ReactDOM.createRoot(document.getElementById('root')).render(React.createElement(CloudSettings, {
  status: { ...initialCloud, keyConfigured: true },
  refresh: async () => { harness.refreshes++; },
  error: '',
  canApply: false,
  onApply: async () => {},
}));
</script></body></html>`;

const browser = await chromium.launch({ headless: true, channel: 'msedge' });
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const pageErrors = [];
  const blockedRequests = [];
  page.on('pageerror', error => { pageErrors.push(error.message); console.error('Fixture error:', error.message); });
  page.on('requestfailed', request => console.error('Fixture request failed:', request.url(), request.failure()?.errorText));
  page.on('response', response => { if (response.status() >= 400) console.error('Fixture response:', response.status(), response.url()); });
  await page.route('**/*', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() !== 'GET' || url.origin !== origin) {
      blockedRequests.push(`${request.method()} ${url.origin}${url.pathname}`);
      await route.abort();
    } else if (url.pathname === '/cloud-auth-test') {
      await route.fulfill({ contentType: 'text/html', body: fixture });
    } else {
      await route.continue();
    }
  });
  await page.goto(`${origin}/cloud-auth-test`, { waitUntil: 'domcontentloaded' });
  const create = page.getByRole('button', { name: 'Create account', exact: true });
  const signIn = page.getByRole('button', { name: 'Sign in', exact: true });
  const email = page.getByLabel('Email', { exact: true });
  const password = page.getByLabel('Password', { exact: true });
  const status = page.getByRole('status');
  const calls = () => page.evaluate(() => window.cloudAuthTest.calls);

  await expect(create).toBeEnabled({ timeout: 15000 }).catch(async error => {
    console.error('Fixture body:', await page.locator('body').innerText());
    throw error;
  });
  await create.click();
  await expect(status).toContainText(/email/i);
  assert.deepEqual(await calls(), [], 'Empty fields must not reach the cloud bridge');

  await email.fill('invalid-address');
  await password.fill('123456');
  await create.click();
  await expect(status).toContainText(/valid.*email|email.*valid/i);
  assert.deepEqual(await calls(), [], 'Invalid email must not reach the cloud bridge');

  await email.fill(' learner@example.test ');
  await password.fill('12345');
  await expect(create).toBeEnabled();
  await create.click();
  await expect(status).toContainText(/password|6|six/i);
  assert.deepEqual(await calls(), [], 'Short password must not reach the cloud bridge');

  const originalPassword = ' 123456 ';
  await password.fill(originalPassword);
  await signIn.click();
  await expect(status).toContainText('Incorrect email or password');
  await expect(password).toHaveValue(originalPassword);
  await expect(create).toBeEnabled();
  assert.deepEqual(await calls(), [
    { action: 'signIn', email: 'learner@example.test', password: originalPassword },
  ]);

  // The failed sign-in must leave enough state to choose Create account directly.
  await create.click();
  await expect.poll(async () => (await calls()).length).toBe(2);
  await expect(create).toBeDisabled();
  await expect(signIn).toBeDisabled();
  await expect(password).toHaveValue(originalPassword);
  assert.deepEqual((await calls())[1], {
    action: 'signUp', email: 'learner@example.test', password: originalPassword,
  });
  await page.evaluate(() => window.cloudAuthTest.finishSignup());
  await expect(status).toContainText('Check your email to confirm the account');
  await expect(password).toHaveValue('');
  await expect(create).toBeEnabled();
  assert.equal(await page.evaluate(() => window.cloudAuthTest.refreshes), 1);
  assert.equal(await status.evaluate(element => {
    const preferences = document.querySelector('.cloud-check');
    return !!preferences && !!(element.compareDocumentPosition(preferences) & Node.DOCUMENT_POSITION_FOLLOWING);
  }), true, 'Account feedback must appear before contribution preferences, beside account controls');
  assert.deepEqual(blockedRequests, [], 'The isolated test must not attempt external requests or mutations');
  assert.deepEqual(pageErrors, [], 'The isolated account form must not throw browser errors');
  console.log('PASS: account validation, preserved retry credentials, trimmed email, busy state, and nearby signup confirmation.');
} finally {
  await browser.close();
}
