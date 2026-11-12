'use strict';

const $ = (id) => document.getElementById(id);
const state = { tokens: null, verification: null, pending: null, busy: false };

function message(text, error = false) {
  $('status').textContent = text;
  $('status').dataset.error = String(error);
}

function resetVerification() {
  state.verification = null;
  state.pending = null;
  $('accounts').replaceChildren(new Option('먼저 계좌를 조회하세요', ''));
  $('ticket-status').textContent = '';
}

function clearSession() {
  state.tokens = null;
  resetVerification();
  $('verify-form').reset();
  $('member').textContent = '로그인 전';
  $('connections').textContent = '로그인 후 확인할 수 있어요.';
  $('connection-result').textContent = '';
  window.google?.accounts.id.disableAutoSelect();
  renderControls();
}

function renderControls() {
  const loggedIn = Boolean(state.tokens);
  const expired = state.verification && Date.now() >= state.verification.expiresAt;
  $('credentials').disabled = state.busy || !loggedIn;
  $('accounts').disabled = state.busy || !loggedIn || !state.verification;
  $('connect').disabled = state.busy || !loggedIn || !state.verification || !$('accounts').value
    || (expired && !state.pending);
  $('reload-connections').disabled = state.busy || !loggedIn;
  $('logout').disabled = state.busy || !loggedIn;
  $('google-button').inert = state.busy || loggedIn;
  if (state.verification) {
    const seconds = Math.max(0, Math.ceil((state.verification.expiresAt - Date.now()) / 1000));
    $('ticket-status').textContent = seconds > 0
      ? `계좌 선택 유효시간: ${seconds}초`
      : '검증 시간이 만료됐어요. 계좌를 다시 조회하세요. 이전 연결 요청의 재시도는 가능해요.';
  }
}

async function api(path, { body, headers = {}, authenticated = true } = {}) {
  const requestHeaders = { ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  if (authenticated) {
    if (!state.tokens) throw new Error('먼저 구글 로그인을 해주세요.');
    requestHeaders.Authorization = `Bearer ${state.tokens.accessToken}`;
  }
  let response;
  try {
    response = await fetch(path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
      credentials: 'omit',
      signal: AbortSignal.timeout(20000),
    });
  } catch {
    throw new Error('서버에 연결할 수 없거나 응답 시간이 초과됐어요. 잠시 후 다시 시도하세요.');
  }
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && authenticated) {
      clearSession();
      throw new Error('로그인이 만료됐어요. 구글 로그인을 다시 해주세요.');
    }
    const text = typeof data?.message === 'string' ? data.message : '요청을 처리하지 못했어요.';
    throw new Error(`${response.status}: ${text}`);
  }
  return data;
}

async function run(task) {
  if (state.busy) return;
  state.busy = true;
  renderControls();
  try {
    await task();
  } catch (error) {
    message(error.message || '요청을 처리하지 못했어요.', true);
  } finally {
    state.busy = false;
    renderControls();
  }
}

async function reloadConnections() {
  const connections = await api('/api/v1/broker-connections');
  $('connections').textContent = connections.length ? JSON.stringify(connections, null, 2) : '연결된 계좌가 없어요.';
}

async function login(credential) {
  await run(async () => {
    clearSession();
    message('로그인 중…');
    const tokens = await api('/api/v1/auth/oauth2', {
      authenticated: false,
      body: { provider: 'GOOGLE', idToken: credential },
    });
    if (!tokens?.accessToken || !tokens?.refreshToken) throw new Error('로그인 응답이 올바르지 않아요.');
    state.tokens = tokens;
    try {
      const member = await api('/api/v1/users/me');
      $('member').textContent = JSON.stringify(member, null, 2);
    } catch (error) {
      clearSession();
      throw error;
    }
    await reloadConnections();
    message('로그인 완료. 토스 인증정보를 입력하세요.');
  });
}

$('verify-form').addEventListener('submit', (event) => {
  event.preventDefault();
  run(async () => {
    resetVerification();
    $('connection-result').textContent = '';
    message('토스 인증정보 및 계좌 확인 중…');
    const body = { clientId: $('client-id').value.trim(), clientSecret: $('client-secret').value };
    let result;
    try {
      result = await api('/api/v1/broker-connections/toss/verify', { body });
    } finally {
      $('client-secret').value = '';
    }
    state.verification = {
      ticket: result.verificationTicket,
      expiresAt: Date.now() + result.expiresIn * 1000,
    };
    $('accounts').replaceChildren(...result.accounts.map((account) =>
      new Option(`${account.accountNumberMasked} (${account.accountType})`, account.accountSeq)));
    message('계좌 조회 완료. 연결할 계좌를 선택하세요.');
  });
});

$('accounts').addEventListener('change', () => {
  state.pending = null;
  renderControls();
});

$('connect').addEventListener('click', () => run(async () => {
  if (!state.verification || !$('accounts').value) throw new Error('먼저 연결할 계좌를 조회하세요.');
  if (!state.pending) {
    state.pending = {
      key: crypto.randomUUID(),
      body: { verificationTicket: state.verification.ticket, accountSeq: $('accounts').value },
    };
  }
  message('계좌 연결 중…');
  // Keep the key and body after network errors so retry cannot create a second connection.
  const connection = await api('/api/v1/broker-connections/toss', {
    body: state.pending.body,
    headers: { 'Idempotency-Key': state.pending.key },
  });
  $('connection-result').textContent = JSON.stringify(connection, null, 2);
  resetVerification();
  await reloadConnections();
  message('계좌 연결 완료.');
}));

$('reload-connections').addEventListener('click', () => run(async () => {
  await reloadConnections();
  message('연결 계좌 목록을 확인했어요.');
}));

$('logout').addEventListener('click', () => run(async () => {
  try {
    if (state.tokens) await api('/api/v1/auth/logout', {
      authenticated: false, body: { refreshToken: state.tokens.refreshToken },
    });
    message('로그아웃했어요.');
  } finally {
    clearSession();
  }
}));

async function initializeGoogle() {
  try {
    const config = await api('/test-ui/config', { authenticated: false });
    if (!config?.googleClientId) throw new Error('서버의 Google Web Client ID 설정을 확인해주세요.');
    await new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client?hl=ko';
      script.async = true;
      script.onload = resolve;
      script.onerror = () => reject(new Error('Google 로그인 스크립트를 불러오지 못했어요. 네트워크를 확인해주세요.'));
      document.head.append(script);
    });
    google.accounts.id.initialize({
      client_id: config.googleClientId,
      callback: (response) => login(response.credential),
      auto_select: false,
      ux_mode: 'popup',
    });
    google.accounts.id.renderButton($('google-button'), { theme: 'outline', size: 'large', text: 'signin_with' });
    message('구글 로그인 버튼을 눌러주세요.');
  } catch (error) {
    message(error.message, true);
  }
}

renderControls();
setInterval(renderControls, 1000);
initializeGoogle();
