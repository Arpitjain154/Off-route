import { useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { Cloud, type CloudStatus } from './cloud';
import './cloud-settings.css';
export default function CloudSettings({
  status,
  refresh,
  error,
  canApply,
  onApply,
}: {
  status: CloudStatus;
  refresh: () => Promise<void>;
  error: string;
  canApply: boolean;
  onApply: () => Promise<void>;
}) {
  const [email, setEmail] = useState(''),
    [password, setPassword] = useState(''),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState('');
  const [url, setUrl] = useState(status.url),
    [bucket, setBucket] = useState(status.bucket),
    [key, setKey] = useState('');
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setMessage('');
    try {
      await action();
      await refresh();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Cloud action failed');
    } finally {
      setBusy(false);
    }
  }
  async function authenticate(signup: boolean) {
    const address = email.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address)) {
      setMessage('Enter a valid email address.');
      return;
    }
    if (!password || (signup && password.length < 6)) {
      setMessage(signup ? 'Use a password with at least 6 characters to create an account.' : 'Enter your password.');
      return;
    }
    await run(async () => {
      const result = await (signup ? Cloud.signUp : Cloud.signIn)({ email: address, password });
      setPassword('');
      setMessage(result.message);
    });
  }
  if (!Capacitor.isNativePlatform())
    return (
      <section className="settings-box cloud-settings">
        <h3>Cloud contribution</h3>
        <p>
          Cloud recording uploads are available in the Android app. Browser simulations stay local.
        </p>
      </section>
    );
  return (
    <section className="settings-box cloud-settings">
      <h3>Cloud contribution</h3>
      <p className="muted">Private trip storage · {status.bucket}</p>
      {status.connected ? (
        <>
          <p>
            Signed in as <strong>{status.email}</strong>
          </p>
          <button
            className="text-button"
            disabled={busy}
            onClick={() => void run(() => Cloud.signOut())}
          >
            Sign out
          </button>
        </>
      ) : (
        <>
          <p>
            Sign in to save recordings for future model improvement. Navigation works without an
            account.
          </p>
          <p className="muted">
            First time here? Choose Create account, confirm your email, then sign in.
            Your Supabase dashboard account is separate.
          </p>
          <label className="field-label">
            Email
            <input
              type="email"
              disabled={busy}
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className="field-label">
            Password
            <input
              type="password"
              disabled={busy}
              aria-describedby="cloud-password-help"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <p id="cloud-password-help" className="muted">New accounts need a password with at least 6 characters.</p>
          <div className="cloud-actions">
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={() => void authenticate(false)}
            >
              Sign in
            </button>
            <button
              type="button"
              className="text-button"
              disabled={busy}
              onClick={() => void authenticate(true)}
            >
              Create account
            </button>
          </div>
        </>
      )}
      {(message || error) && <p role="status">{message || error}</p>}
      <label className="cloud-check">
        <input
          type="checkbox"
          disabled={busy || !status.connected}
          checked={status.consent}
          onChange={(e) =>
            void run(() =>
              Cloud.setPreferences({ consent: e.target.checked, wifiOnly: status.wifiOnly }),
            )
          }
        />
        Contribute new trips to improve navigation
      </label>
      <p className="muted">
        Uploads include precise location and motion sensors. Files stay private in your account.
        This collects training data; it does not train a model on your phone. Older trips upload
        only when you select them.
      </p>
      <label className="cloud-check">
        <input
          type="checkbox"
          disabled={busy}
          checked={status.wifiOnly}
          onChange={(e) =>
            void run(() =>
              Cloud.setPreferences({ consent: status.consent, wifiOnly: e.target.checked }),
            )
          }
        />
        Upload and download updates on Wi-Fi only
      </label>
      <p>
        {status.queue.filter((x) => x.state === 'uploaded').length} uploaded ·{' '}
        {status.queue.filter((x) => x.state === 'queued' || x.state === 'uploading').length} pending
        · {status.queue.filter((x) => x.state === 'action-required').length} need attention
      </p>
      <p className="muted">{status.modelMessage}</p>
      <button
        className="button secondary"
        disabled={busy || !status.connected}
        onClick={() => void run(() => Cloud.checkModelUpdates())}
      >
        Check approved model updates
      </button>
      {status.modelMessage.startsWith('Update ready') && (
        <>
          <button
            className="button secondary"
            disabled={busy || !canApply}
            onClick={() => void run(onApply)}
          >
            Apply update and restart tracking
          </button>
          <p className="muted">
            {canApply
              ? 'Tracking pauses briefly while the verified model is installed.'
              : 'Finish navigation and recording before applying an update.'}
          </p>
        </>
      )}
      <details>
        <summary>Supabase connection</summary>
        <p className="muted">
          {status.keyConfigured
            ? 'Publishable key is configured. You do not need to enter it to sign in.'
            : 'Publishable key is not configured. Save your project connection below.'}
        </p>
        <label className="field-label">
          Project URL
          <input value={url} onChange={(e) => setUrl(e.target.value)} />
        </label>
        <label className="field-label">
          Trip bucket
          <input value={bucket} onChange={(e) => setBucket(e.target.value)} />
        </label>
        <label className="field-label">
          Replace publishable key (optional)
          <input
            autoComplete="off"
            placeholder="Leave empty to keep the configured key"
            value={key}
            onChange={(e) => setKey(e.target.value)}
          />
        </label>
        <p className="muted">
          Changing the connection signs you out. Never use a secret or service-role key.
        </p>
        <button
          className="button secondary"
          disabled={busy || !key.startsWith('sb_publishable_')}
          onClick={() =>
            void run(async () => {
              await Cloud.configure({ url, key, bucket });
              setKey('');
              setMessage('Connection saved. Sign in to continue.');
            })
          }
        >
          Save cloud connection
        </button>
      </details>
    </section>
  );
}
