(() => {
  'use strict';

  const root = document.documentElement;
  const body = document.body;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const header = document.querySelector('.site-header');
  const menu = document.querySelector('#mobile-menu');
  const menuToggle = document.querySelector('.menu-toggle');
  const menuClose = document.querySelector('.menu-close');
  const dialog = document.querySelector('#browser-dialog');
  const menuFocusable = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';
  let menuIsOpen = false;
  let previousFocus = null;
  let scrollFrame = 0;

  const setTheme = (theme) => {
    const next = theme === 'light' ? 'light' : 'dark';
    root.dataset.theme = next;
    try { localStorage.setItem('blockline-theme', next); } catch (_) {}
    document.querySelectorAll('.theme-toggle').forEach((button) => {
      button.setAttribute('aria-pressed', String(next === 'light'));
      button.setAttribute('aria-label', next === 'light' ? 'Chuyển sang giao diện tối' : 'Chuyển sang giao diện sáng');
    });
    const themeMeta = document.querySelector('meta[name="theme-color"]');
    if (themeMeta) themeMeta.content = next === 'light' ? '#f4f7f2' : '#101612';
  };

  let savedTheme = '';
  try { savedTheme = localStorage.getItem('blockline-theme') || ''; } catch (_) {}
  if (savedTheme === 'light' || savedTheme === 'dark') setTheme(savedTheme);
  else {
    const lightPreference = window.matchMedia('(prefers-color-scheme: light)').matches;
    root.dataset.theme = lightPreference ? 'light' : 'dark';
    setTheme(root.dataset.theme);
  }
  document.querySelectorAll('.theme-toggle').forEach((button) => {
    button.addEventListener('click', () => setTheme(root.dataset.theme === 'light' ? 'dark' : 'light'));
  });

  const updateScrollChrome = () => {
    scrollFrame = 0;
    header?.classList.toggle('is-scrolled', window.scrollY > 12);
    const maxScroll = root.scrollHeight - window.innerHeight;
    const ratio = maxScroll > 0 ? Math.min(1, Math.max(0, window.scrollY / maxScroll)) : 0;
    const bar = document.querySelector('#scroll-progress');
    if (bar) {
      bar.style.transform = 'scaleX(' + ratio + ')';
      bar.classList.toggle('is-active', window.scrollY > 80 && maxScroll > 0);
    }
  };
  const queueScrollChrome = () => {
    if (scrollFrame) return;
    scrollFrame = window.requestAnimationFrame(updateScrollChrome);
  };
  const progressBar = document.createElement('div');
  progressBar.id = 'scroll-progress';
  progressBar.setAttribute('aria-hidden', 'true');
  body.prepend(progressBar);
  window.addEventListener('scroll', queueScrollChrome, { passive: true });
  window.addEventListener('resize', queueScrollChrome, { passive: true });
  updateScrollChrome();

  const openMenu = () => {
    if (!menu || !menuToggle || menuIsOpen) return;
    previousFocus = document.activeElement;
    menuIsOpen = true;
    menu.inert = false;
    menu.setAttribute('aria-hidden', 'false');
    menu.classList.add('is-open');
    menuToggle.setAttribute('aria-expanded', 'true');
    menuToggle.setAttribute('aria-label', 'Đóng menu điều hướng');
    body.classList.add('menu-open');
    const first = menu.querySelector(menuFocusable);
    window.requestAnimationFrame(() => first?.focus());
  };
  const closeMenu = (restoreFocus = true) => {
    if (!menu || !menuToggle || !menuIsOpen) return;
    menuIsOpen = false;
    menu.classList.remove('is-open');
    menuToggle.setAttribute('aria-expanded', 'false');
    menuToggle.setAttribute('aria-label', 'Mở menu điều hướng');
    body.classList.remove('menu-open');
    window.setTimeout(() => {
      if (menuIsOpen) return;
      menu.inert = true;
      menu.setAttribute('aria-hidden', 'true');
    }, reducedMotion.matches ? 0 : 320);
    if (restoreFocus) menuToggle.focus();
    else if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus({ preventScroll: true });
  };
  menuToggle?.addEventListener('click', () => menuIsOpen ? closeMenu() : openMenu());
  menuClose?.addEventListener('click', () => closeMenu());
  menu?.querySelectorAll('a[href]').forEach((link) => link.addEventListener('click', () => closeMenu(false)));
  document.addEventListener('keydown', (event) => {
    if (menuIsOpen && event.key === 'Escape') {
      event.preventDefault();
      closeMenu();
      return;
    }
    if (menuIsOpen && event.key === 'Tab') {
      const items = [...menu.querySelectorAll(menuFocusable)].filter((item) => !item.hasAttribute('disabled'));
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    if (dialog?.open && event.key === 'Escape') dialog.close();
  });
  window.addEventListener('resize', () => {
    if (window.innerWidth > 820 && menuIsOpen) closeMenu(false);
  }, { passive: true });

  const revealTargets = [...document.querySelectorAll('.section-intro, .feature-row, .demo-layout, .comparison-wrap, .roadmap-item, .setup-step, .faq-list, .download-panel')];
  if (!reducedMotion.matches && 'IntersectionObserver' in window) {
    const revealObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -7% 0px', threshold: 0.08 });
    revealTargets.forEach((target, index) => {
      target.classList.add('reveal');
      if (target.classList.contains('roadmap-item') || target.classList.contains('setup-step')) {
        target.style.setProperty('--reveal-delay', (index % 3) * 65 + 'ms');
      }
      revealObserver.observe(target);
    });
  } else {
    revealTargets.forEach((target) => target.classList.add('is-visible'));
  }

  const readingCopy = document.querySelector('[data-reading-copy]');
  let readingFrame = 0;
  const updateReadingHighlight = () => {
    readingFrame = 0;
    if (!readingCopy || reducedMotion.matches) return;
    const rect = readingCopy.getBoundingClientRect();
    const vh = window.innerHeight || 1;
    const progress = Math.min(1, Math.max(0, (vh * 0.82 - rect.top) / (rect.height + vh * 0.42)));
    readingCopy.style.setProperty('--reading-progress', String(progress));
  };
  const queueReadingHighlight = () => {
    if (readingFrame) return;
    readingFrame = window.requestAnimationFrame(updateReadingHighlight);
  };
  window.addEventListener('scroll', queueReadingHighlight, { passive: true });
  window.addEventListener('resize', queueReadingHighlight, { passive: true });
  updateReadingHighlight();
  if (reducedMotion.matches && readingCopy) readingCopy.style.setProperty('--reading-progress', '1');

  document.querySelectorAll('.tree-toggle').forEach((button) => {
    button.addEventListener('click', () => {
      const panel = document.getElementById(button.getAttribute('aria-controls'));
      if (!panel) return;
      const expanded = button.getAttribute('aria-expanded') === 'true';
      button.setAttribute('aria-expanded', String(!expanded));
      panel.classList.toggle('is-collapsed', expanded);
      panel.setAttribute('aria-hidden', String(expanded));
    });
  });
  document.querySelectorAll('.tree-file').forEach((button) => {
    button.addEventListener('click', () => {
      document.querySelectorAll('.tree-file').forEach((file) => file.classList.toggle('is-selected', file === button));
      const selectedName = document.querySelector('#selected-file-name');
      if (selectedName) selectedName.textContent = button.dataset.fileName || button.textContent.trim();
    });
  });

  const editorTabs = [...document.querySelectorAll('[data-editor-tab]')];
  const editorPanel = document.querySelector('#editor-tab-panel');
  const editorLanguage = document.querySelector('#editor-language');
  const editorLabels = { manifest: ['tab-manifest', 'JSON'], script: ['tab-script', 'JavaScript'], function: ['tab-function', 'MCFUNCTION'] };
  const activateEditorTab = (key, focus = false) => {
    const selected = editorTabs.find((tab) => tab.dataset.editorTab === key);
    if (!selected) return;
    editorTabs.forEach((tab) => {
      const active = tab === selected;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
    });
    document.querySelectorAll('[data-code-sample]').forEach((sample) => {
      sample.hidden = sample.dataset.codeSample !== key;
    });
    if (editorPanel) editorPanel.setAttribute('aria-labelledby', editorLabels[key][0]);
    if (editorLanguage) editorLanguage.textContent = editorLabels[key][1];
    if (focus) selected.focus();
  };
  editorTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activateEditorTab(tab.dataset.editorTab));
    tab.addEventListener('keydown', (event) => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      let next = index;
      if (event.key === 'ArrowLeft') next = (index - 1 + editorTabs.length) % editorTabs.length;
      if (event.key === 'ArrowRight') next = (index + 1) % editorTabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = editorTabs.length - 1;
      activateEditorTab(editorTabs[next].dataset.editorTab, true);
    });
  });

  const demoTabs = [...document.querySelectorAll('[data-demo-tab]')];
  const demoPanel = document.querySelector('[data-demo-panel]');
  const demoLabel = document.querySelector('#demo-screen-label');
  const demoLabels = { import: 'IMPORT / PACK FILE', edit: 'EDITOR / TEXT FILE', manifest: 'MANIFEST / DETAILS', export: 'EXPORT / PACK' };
  const activateDemoTab = (key, focus = false) => {
    const selected = demoTabs.find((tab) => tab.dataset.demoTab === key);
    if (!selected) return;
    demoTabs.forEach((tab) => {
      const active = tab === selected;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
    });
    document.querySelectorAll('[data-demo-content]').forEach((panel) => {
      panel.hidden = panel.dataset.demoContent !== key;
    });
    if (demoPanel) demoPanel.setAttribute('aria-labelledby', selected.id);
    if (demoLabel) demoLabel.textContent = demoLabels[key] || 'BLOCKLINE / DEMO';
    const activeContent = document.querySelector('[data-demo-content="' + key + '"]');
    if (activeContent && !reducedMotion.matches) {
      activeContent.style.animation = 'none';
      void activeContent.offsetWidth;
      activeContent.style.animation = '';
    }
    if (focus) selected.focus();
  };
  demoTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activateDemoTab(tab.dataset.demoTab));
    tab.addEventListener('keydown', (event) => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      let next = index;
      if (event.key === 'ArrowLeft') next = (index - 1 + demoTabs.length) % demoTabs.length;
      if (event.key === 'ArrowRight') next = (index + 1) % demoTabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = demoTabs.length - 1;
      activateDemoTab(demoTabs[next].dataset.demoTab, true);
    });
  });

  const faqItems = [...document.querySelectorAll('.faq-list details')];
  faqItems.forEach((item) => {
    item.addEventListener('toggle', () => {
      if (!item.open) return;
      faqItems.forEach((other) => { if (other !== item) other.open = false; });
    });
  });

  const isEmbeddedBrowser = () => {
    const ua = navigator.userAgent || '';
    return /(FBAN|FBAV|Instagram|Messenger|Zalo|Line\/|TikTok|MicroMessenger|wv\))/i.test(ua);
  };
  const showDownloadHelp = (event) => {
    if (!isEmbeddedBrowser() || !dialog || typeof dialog.showModal !== 'function') return;
    event.preventDefault();
    if (!dialog.open) dialog.showModal();
  };
  document.querySelectorAll('[data-apk-link]').forEach((link) => link.addEventListener('click', showDownloadHelp));
  document.querySelector('[data-close-dialog]')?.addEventListener('click', () => dialog?.close());
  document.querySelector('[data-copy-page]')?.addEventListener('click', async () => {
    const status = document.querySelector('.copy-status');
    const pageUrl = window.location.href;
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(pageUrl);
      } else {
        const temporary = document.createElement('textarea');
        temporary.value = pageUrl;
        temporary.setAttribute('readonly', '');
        temporary.style.position = 'fixed';
        temporary.style.opacity = '0';
        body.append(temporary);
        temporary.select();
        const copied = document.execCommand('copy');
        temporary.remove();
        if (!copied) throw new Error('copy failed');
      }
      if (status) status.textContent = 'Đã chép liên kết. Hãy mở Chrome và dán liên kết vào thanh địa chỉ.';
    } catch (_) {
      if (status) status.textContent = 'Không thể tự chép liên kết. Hãy sao chép địa chỉ trên thanh trình duyệt.';
    }
  });
  dialog?.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });

  document.querySelectorAll('[data-current-year]').forEach((element) => {
    element.textContent = String(new Date().getFullYear());
  });
})();