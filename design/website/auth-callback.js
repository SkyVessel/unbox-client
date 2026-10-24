(async () => {
  const status = document.querySelector('#auth-result');
  const query = new URLSearchParams(location.search);
  history.replaceState(null, '', '/unbox/auth/callback');
  const theme = sessionStorage.getItem('unbox-signin-theme') === 'light' ? 'light' : 'dark';
  document.documentElement.dataset.theme = theme;
  document.querySelector('.auth-result img').src = `/unbox/assets/unbox-mark-${theme}.svg`;
  async function post(path, body) {
    const response = await fetch('/unbox/api/auth/' + path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Sign-in could not finish. Please try again.');
    return data;
  }
  try {
    if (query.has('error')) throw new Error(query.get('error') === 'access_denied' ? 'Sign-in was cancelled. You can try again from Unbox.' : 'Microsoft sign-in could not finish. Please try again.');
    const code = query.get('code'), state = query.get('state');
    if (!code || !state) throw new Error('Start sign-in from the Unbox website.');
    const flow = await post('flow', {state});
    const response = await fetch('https://login.microsoftonline.com/consumers/oauth2/v2.0/token', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({client_id: flow.clientId, grant_type: 'authorization_code', code, redirect_uri: flow.redirectUri, code_verifier: flow.verifier, scope: flow.scope})});
    const tokens = await response.json();
    if (!response.ok || !tokens.id_token) throw new Error('Microsoft could not finish this sign-in. Please retry from Unbox.');
    await post('complete', {state, idToken: tokens.id_token});
    location.replace('/unbox/?theme=' + theme + '#feedback');
  } catch (error) { document.querySelector('h1').textContent = 'SIGN-IN INCOMPLETE.'; status.textContent = error.message; }
})();
