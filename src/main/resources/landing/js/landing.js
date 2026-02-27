/**
 * Mini Numbers Landing Page — Interactive Features
 * Vanilla JS, no dependencies.
 */

(function () {
  'use strict';

  /* ============================================
     THEME MANAGER
     Adapted from static/js/theme.js — same localStorage key
     ============================================ */
  const ThemeManager = {
    STORAGE_KEY: 'ma-theme-preference',

    init() {
      document.documentElement.classList.add('no-transition');
      const saved = this.getSaved();
      this.apply(saved || this.systemPref());
      setTimeout(() => document.documentElement.classList.remove('no-transition'), 100);
      this.watchSystem();
      this.bindToggle();
    },

    getSaved() {
      try { return localStorage.getItem(this.STORAGE_KEY); } catch { return null; }
    },

    save(theme) {
      try { localStorage.setItem(this.STORAGE_KEY, theme); } catch { /* noop */ }
    },

    systemPref() {
      return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    },

    watchSystem() {
      if (!window.matchMedia) return;
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        if (!this.getSaved()) this.apply(e.matches ? 'dark' : 'light');
      });
    },

    apply(theme) {
      if (theme === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
      } else {
        document.documentElement.removeAttribute('data-theme');
      }
      this.updateIcon();
    },

    current() {
      return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    },

    toggle() {
      const next = this.current() === 'dark' ? 'light' : 'dark';
      this.apply(next);
      this.save(next);
    },

    updateIcon() {
      const icon = document.getElementById('theme-icon');
      if (!icon) return;
      icon.className = this.current() === 'dark' ? 'ri-sun-line' : 'ri-moon-line';
    },

    bindToggle() {
      const btn = document.getElementById('theme-toggle');
      if (btn) btn.addEventListener('click', () => this.toggle());
    }
  };

  /* ============================================
     SCROLL ANIMATOR
     Intersection Observer — add .visible on scroll
     ============================================ */
  const ScrollAnimator = {
    init() {
      const els = document.querySelectorAll('.animate-on-scroll');
      if (!els.length) return;

      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            entry.target.classList.add('visible');
            observer.unobserve(entry.target);
          }
        });
      }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });

      els.forEach(el => observer.observe(el));
    }
  };

  /* ============================================
     COUNTER ANIMATOR
     Animate numbers from 0 to target
     ============================================ */
  const CounterAnimator = {
    init() {
      const els = document.querySelectorAll('[data-count]');
      if (!els.length) return;

      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            this.animate(entry.target);
            observer.unobserve(entry.target);
          }
        });
      }, { threshold: 0.3 });

      els.forEach(el => observer.observe(el));
    },

    animate(el) {
      const text = el.getAttribute('data-text');
      if (text) {
        // Static text like "Zero" — just set it
        el.textContent = text;
        return;
      }

      const target = parseFloat(el.getAttribute('data-count'));
      const suffix = el.getAttribute('data-suffix') || '';
      const isFloat = target % 1 !== 0;
      const duration = 1500;
      const start = performance.now();

      function step(now) {
        const elapsed = now - start;
        const progress = Math.min(elapsed / duration, 1);
        // Ease out cubic
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = target * eased;

        if (isFloat) {
          el.textContent = current.toFixed(1) + suffix;
        } else {
          el.textContent = Math.round(current).toLocaleString() + suffix;
        }

        if (progress < 1) {
          requestAnimationFrame(step);
        }
      }

      requestAnimationFrame(step);
    }
  };

  /* ============================================
     TAB MANAGER
     Handles feature tabs and install tabs
     ============================================ */
  const TabManager = {
    init() {
      this.setup('.landing-tab-btn', '.landing-tab-content', 'tab');
      this.setup('.landing-install-tab', '.landing-install-content', 'install');
    },

    setup(btnSelector, contentSelector, attr) {
      const btns = document.querySelectorAll(btnSelector);
      const contents = document.querySelectorAll(contentSelector);
      if (!btns.length) return;

      btns.forEach(btn => {
        btn.addEventListener('click', () => {
          const value = btn.getAttribute('data-' + attr);

          btns.forEach(b => {
            b.classList.remove('active');
            b.setAttribute('aria-selected', 'false');
          });
          contents.forEach(c => c.classList.remove('active'));

          btn.classList.add('active');
          btn.setAttribute('aria-selected', 'true');

          const target = document.querySelector(contentSelector + '[data-' + attr + '="' + value + '"]');
          if (target) target.classList.add('active');
        });
      });
    }
  };

  /* ============================================
     CODE COPY MANAGER
     Clipboard API with feedback
     ============================================ */
  const CodeCopyManager = {
    init() {
      document.querySelectorAll('.landing-copy-btn').forEach(btn => {
        btn.addEventListener('click', () => this.copy(btn));
      });
    },

    async copy(btn) {
      const code = btn.getAttribute('data-code');
      if (!code) return;

      try {
        await navigator.clipboard.writeText(code);
        btn.classList.add('copied');
        const original = btn.innerHTML;
        btn.innerHTML = '<i class="ri-check-line"></i> Copied!';
        setTimeout(() => {
          btn.classList.remove('copied');
          btn.innerHTML = original;
        }, 2000);
      } catch {
        // Fallback for non-HTTPS or older browsers
        const textarea = document.createElement('textarea');
        textarea.value = code;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);

        btn.classList.add('copied');
        const original = btn.innerHTML;
        btn.innerHTML = '<i class="ri-check-line"></i> Copied!';
        setTimeout(() => {
          btn.classList.remove('copied');
          btn.innerHTML = original;
        }, 2000);
      }
    }
  };

  /* ============================================
     NAV MANAGER
     Scroll spy, hamburger, shrink on scroll
     ============================================ */
  const NavManager = {
    init() {
      this.nav = document.querySelector('.landing-nav');
      this.links = document.querySelectorAll('.landing-nav-links a');
      this.sections = [];

      // Build section map
      this.links.forEach(link => {
        const href = link.getAttribute('href');
        if (href && href.startsWith('#')) {
          const section = document.querySelector(href);
          if (section) this.sections.push({ el: section, link });
        }
      });

      // Scroll handlers
      let ticking = false;
      window.addEventListener('scroll', () => {
        if (!ticking) {
          requestAnimationFrame(() => {
            this.onScroll();
            ticking = false;
          });
          ticking = true;
        }
      });

      // Hamburger
      const hamburger = document.getElementById('nav-hamburger');
      const navLinks = document.getElementById('nav-links');
      if (hamburger && navLinks) {
        hamburger.addEventListener('click', () => {
          const isOpen = navLinks.classList.toggle('open');
          hamburger.setAttribute('aria-expanded', isOpen);
          const icon = document.getElementById('hamburger-icon');
          if (icon) icon.className = isOpen ? 'ri-close-line' : 'ri-menu-line';
        });

        // Close on link click
        navLinks.querySelectorAll('a').forEach(link => {
          link.addEventListener('click', () => {
            navLinks.classList.remove('open');
            hamburger.setAttribute('aria-expanded', 'false');
            const icon = document.getElementById('hamburger-icon');
            if (icon) icon.className = 'ri-menu-line';
          });
        });
      }
    },

    onScroll() {
      // Shrink nav
      if (this.nav) {
        this.nav.classList.toggle('scrolled', window.scrollY > 50);
      }

      // Scroll spy
      const scrollPos = window.scrollY + 100;
      let current = null;

      for (const { el, link } of this.sections) {
        if (el.offsetTop <= scrollPos) {
          current = link;
        }
      }

      this.links.forEach(l => l.classList.remove('active'));
      if (current) current.classList.add('active');
    }
  };

  /* ============================================
     WIDGET TOGGLES
     Show/hide code snippets
     ============================================ */
  const WidgetToggles = {
    init() {
      document.querySelectorAll('.landing-widget-toggle').forEach(btn => {
        btn.addEventListener('click', () => {
          const widget = btn.getAttribute('data-widget');
          const codeEl = document.querySelector('.landing-widget-code[data-widget="' + widget + '"]');
          if (!codeEl) return;

          const isOpen = codeEl.classList.toggle('open');
          btn.innerHTML = isOpen
            ? '<i class="ri-code-line"></i> Hide Code'
            : '<i class="ri-code-line"></i> View Code';
        });
      });
    }
  };

  /* ============================================
     SIZE BAR ANIMATOR
     Animate tracker size comparison bars
     ============================================ */
  const SizeBarAnimator = {
    init() {
      const bars = document.querySelectorAll('.landing-size-bar-fill');
      if (!bars.length) return;

      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            this.animateBars(entry.target.closest('.landing-size-bars'));
            observer.unobserve(entry.target);
          }
        });
      }, { threshold: 0.3 });

      const container = document.querySelector('.landing-size-bars');
      if (container) observer.observe(container);
    },

    animateBars(container) {
      if (!container) return;
      const bars = container.querySelectorAll('.landing-size-bar-fill');
      bars.forEach(bar => {
        const width = bar.getAttribute('data-width');
        if (width) {
          // Small delay for visual effect
          setTimeout(() => {
            bar.style.width = width + '%';
          }, 100);
        }
      });
    }
  };

  /* ============================================
     WIDGET DEMO
     Simulated real-time visitor count
     ============================================ */
  const WidgetDemo = {
    init() {
      const countEl = document.getElementById('widget-demo-count');
      if (!countEl) return;

      setInterval(() => {
        const current = parseInt(countEl.textContent) || 3;
        // Random walk: -1, 0, or +1
        const delta = Math.floor(Math.random() * 3) - 1;
        const next = Math.max(1, Math.min(12, current + delta));
        countEl.textContent = next;
      }, 3000);
    }
  };

  /* ============================================
     10. PARTICLE CANVAS (Hero Background)
     ============================================ */
  const ParticleCanvas = {
    canvas: null,
    ctx: null,
    particles: [],
    mouse: { x: -1000, y: -1000 },
    raf: null,

    init() {
      this.canvas = document.getElementById('hero-particles');
      if (!this.canvas) return;
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

      this.ctx = this.canvas.getContext('2d');
      this.resize();
      this.createParticles();
      this.animate();

      window.addEventListener('resize', () => this.resize());
      this.canvas.parentElement.addEventListener('mousemove', (e) => {
        const rect = this.canvas.getBoundingClientRect();
        this.mouse.x = e.clientX - rect.left;
        this.mouse.y = e.clientY - rect.top;
      });
      this.canvas.parentElement.addEventListener('mouseleave', () => {
        this.mouse.x = -1000;
        this.mouse.y = -1000;
      });
    },

    resize() {
      const rect = this.canvas.parentElement.getBoundingClientRect();
      this.canvas.width = rect.width;
      this.canvas.height = rect.height;
      if (this.particles.length === 0) this.createParticles();
    },

    createParticles() {
      const count = Math.min(50, Math.floor((this.canvas.width * this.canvas.height) / 15000));
      this.particles = [];
      for (let i = 0; i < count; i++) {
        this.particles.push({
          x: Math.random() * this.canvas.width,
          y: Math.random() * this.canvas.height,
          vx: (Math.random() - 0.5) * 0.5,
          vy: (Math.random() - 0.5) * 0.5,
          r: Math.random() * 2 + 1,
          opacity: Math.random() * 0.5 + 0.2
        });
      }
    },

    animate() {
      if (!this.ctx) return;
      const { width, height } = this.canvas;
      this.ctx.clearRect(0, 0, width, height);

      const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
      const dotColor = isDark ? '255,255,255' : '99,102,241';
      const lineColor = isDark ? '255,255,255' : '99,102,241';

      for (const p of this.particles) {
        // Mouse repulsion
        const dx = p.x - this.mouse.x;
        const dy = p.y - this.mouse.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 120) {
          const force = (120 - dist) / 120 * 0.02;
          p.vx += dx * force;
          p.vy += dy * force;
        }

        p.x += p.vx;
        p.y += p.vy;
        p.vx *= 0.99;
        p.vy *= 0.99;

        // Wrap around edges
        if (p.x < 0) p.x = width;
        if (p.x > width) p.x = 0;
        if (p.y < 0) p.y = height;
        if (p.y > height) p.y = 0;

        // Draw dot
        this.ctx.beginPath();
        this.ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        this.ctx.fillStyle = `rgba(${dotColor},${p.opacity})`;
        this.ctx.fill();
      }

      // Draw connecting lines
      for (let i = 0; i < this.particles.length; i++) {
        for (let j = i + 1; j < this.particles.length; j++) {
          const a = this.particles[i];
          const b = this.particles[j];
          const dx = a.x - b.x;
          const dy = a.y - b.y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 120) {
            this.ctx.beginPath();
            this.ctx.moveTo(a.x, a.y);
            this.ctx.lineTo(b.x, b.y);
            this.ctx.strokeStyle = `rgba(${lineColor},${0.1 * (1 - dist / 120)})`;
            this.ctx.stroke();
          }
        }
      }

      this.raf = requestAnimationFrame(() => this.animate());
    }
  };

  /* ============================================
     INIT
     ============================================ */
  function init() {
    ThemeManager.init();
    ScrollAnimator.init();
    CounterAnimator.init();
    TabManager.init();
    CodeCopyManager.init();
    NavManager.init();
    WidgetToggles.init();
    SizeBarAnimator.init();
    WidgetDemo.init();
    ParticleCanvas.init();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
