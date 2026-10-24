(() => {
  const nav = document.querySelector('#account');
  const panel = document.querySelector('#feedback-account');
  let user = null, busy = false;
  async function refresh() {
    try {
      const response = await fetch('/unbox/api/auth/session');
      if (!response.ok) throw new Error();
      user = (await response.json()).user;
      nav.textContent = user ? 'Sign out' : 'Sign in';
      nav.setAttribute('aria-label', user ? 'Sign out of Unbox' : 'Sign in with Microsoft');
      panel.replaceChildren();
      const text = document.createElement('p');
      text.textContent = user ? `Signed in as ${user.name}` : 'Sign in with Microsoft to leave feedback.';
      panel.append(text);
      if (user) {
        const privateEmail = document.createElement('small'); privateEmail.textContent = `Reply email: ${user.email} · Only you and the studio can see this.`; panel.append(privateEmail);
      } else {
        const button = document.createElement('button'); button.type = 'button'; button.className = 'microsoft-sign-in'; button.textContent = 'Sign in with Microsoft'; button.addEventListener('click', signIn); panel.append(button);
      }
      for (const element of document.querySelectorAll('#feedback-name,#feedback-message,#feedback-form [type=submit]')) element.disabled = !user;
      document.dispatchEvent(new CustomEvent('unbox-auth', {detail: {signedIn: Boolean(user)}}));
    } catch { panel.textContent = 'Could not check sign-in. Refresh the page to retry.'; }
  }
  async function signIn() {
    if (busy) return; busy = true; nav.disabled = true;
    try {
      const response = await fetch('/unbox/api/auth/start', {method: 'POST'});
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Could not start sign-in.');
      sessionStorage.setItem('unbox-signin-theme', document.documentElement.dataset.theme);
      sessionStorage.setItem('unbox-feedback-draft', JSON.stringify({name: document.querySelector('#feedback-name').value, message: document.querySelector('#feedback-message').value}));
      location.assign(data.url);
    } catch (error) { document.querySelector('#feedback-status').textContent = error.message; busy = false; nav.disabled = false; }
  }
  nav.addEventListener('click', async () => {
    if (!user) return signIn();
    if (busy) return; busy = true; nav.disabled = true;
    try {
      const response = await fetch('/unbox/api/auth/logout', {method: 'POST'});
      if (!response.ok) throw new Error('Could not sign out. Please try again.');
      await refresh();
    } catch (error) { document.querySelector('#feedback-status').textContent = error.message; }
    finally { busy = false; nav.disabled = false; }
  });
  document.addEventListener('unbox-auth-expired', refresh);
  refresh();
})();
