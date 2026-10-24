(() => {
  const form = document.querySelector('#feedback-form');
  const list = document.querySelector('#feedback-list');
  const status = document.querySelector('#feedback-status');
  const more = document.querySelector('#feedback-more');
  const retry = document.querySelector('#feedback-retry');
  const submit = form.querySelector('[type=submit]');
  const message = form.elements.message;
  let cursor = null, loading = false, posting = false;
  const seen = new Set();
  const count = () => { document.querySelector('#feedback-count').textContent = `${message.value.length.toLocaleString('en')} / 1,200`; };
  message.addEventListener('input', count);
  function render(post, first = false) {
    if (seen.has(post.id)) return;
    seen.add(post.id);
    list.querySelector('.feedback-empty')?.remove();
    const article = document.createElement('article'); article.className = 'feedback-post';
    const header = document.createElement('div'); header.className = 'feedback-post-heading';
    const name = document.createElement('strong'); name.textContent = post.name;
    const time = document.createElement('time'); const date = new Date(post.created_at);
    time.dateTime = date.toISOString(); time.textContent = date.toLocaleDateString('en', {month: 'short', day: 'numeric', year: 'numeric'});
    const body = document.createElement('p'); body.textContent = post.message;
    header.append(name, time); article.append(header, body);
    if (first) list.prepend(article); else list.append(article);
  }
  async function load(append = false) {
    if (loading) return;
    loading = true; more.disabled = true; retry.hidden = true;
    list.setAttribute('aria-busy', 'true');
    try {
      const response = await fetch('/unbox/api/feedback' + (append && cursor ? '?before=' + encodeURIComponent(cursor) : ''));
      if (!response.ok) throw new Error('Could not load messages. Please try again.');
      const data = await response.json();
      if (!append) { list.replaceChildren(); seen.clear(); }
      for (const post of data.posts) render(post);
      if (!seen.size) { const empty = document.createElement('p'); empty.className = 'feedback-empty'; empty.textContent = 'No messages yet. Share the first idea.'; list.append(empty); }
      cursor = data.next; more.hidden = !cursor;
    } catch (error) {
      if (!seen.size) { list.replaceChildren(); const empty = document.createElement('p'); empty.className = 'feedback-empty'; empty.textContent = error.message; list.append(empty); }
      retry.hidden = false;
    } finally { loading = false; more.disabled = false; list.removeAttribute('aria-busy'); }
  }
  more.addEventListener('click', () => load(true));
  retry.addEventListener('click', () => load(Boolean(cursor)));
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (posting) return;
    if (message.value.trim().length < 10) { status.textContent = 'Please write at least 10 characters.'; message.focus(); return; }
    posting = true; submit.disabled = true; submit.textContent = 'Posting…'; status.textContent = '';
    try {
      const response = await fetch('/unbox/api/feedback', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({name: form.elements.name.value, message: message.value, website: form.elements.website.value})});
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Could not post your message. Please try again.');
      render(data.post, true); message.value = ''; count(); status.textContent = 'Message posted. Thank you for your feedback.';
    } catch (error) { status.textContent = error.message; }
    finally { posting = false; submit.disabled = false; submit.textContent = 'Post message'; }
  });
  load();
})();
