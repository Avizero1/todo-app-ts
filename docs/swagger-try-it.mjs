// docs/swagger-try-it.mjs — локальный Swagger UI с кнопкой «Try it out» для этого API.
// Запуск (Node 18+):  node docs/swagger-try-it.mjs     → http://127.0.0.1:8092
// Опции: PORT=9000 BACKEND_URL=http://127.0.0.1:8080 node docs/swagger-try-it.mjs
//
// Зачем прокси: запросы идут на ЭТОТ же адрес (тот же origin) и сервер пересылает их
// на бэкенд — не нужен CORS в Ktor (его пока нет в заготовке Plugins.kt).
// Спека отдаётся с подменённым `servers.url`, чтобы Swagger UI обращался к нам.
// Ассеты swagger-ui-dist ставятся автоматически во временную папку (один раз, npm).
// Оформление — тёмная тема из docs/swagger-theme.css (гид: README-раздел «Swagger» в docs/api.md).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const PORT = Number(process.env.PORT || 8092);
const BACKEND = (process.env.BACKEND_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const SPEC_PATH = path.join(path.dirname(fileURLToPath(import.meta.url)), 'swagger.yaml');
const THEME_PATH = path.join(path.dirname(fileURLToPath(import.meta.url)), 'swagger-theme.css');
const ASSETS_ROOT = path.join(os.tmpdir(), 'swagger-try-it');
const ASSETS = path.join(ASSETS_ROOT, 'node_modules', 'swagger-ui-dist');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.yaml': 'text/yaml; charset=utf-8',
  '.svg': 'image/svg+xml',
};

const INDEX = `<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Todo App API — Swagger UI</title>
  <link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Crect width='32' height='32' rx='7' fill='%232f81f7'/%3E%3Cpath d='M8 17l5 5 11-12' stroke='%23fff' stroke-width='3.5' fill='none' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E">
  <link rel="stylesheet" href="/assets/swagger-ui.css">
  <link rel="stylesheet" href="/theme.css">
</head>
<body>
  <header class="app-header">
    <div class="app-header__inner">
      <a class="brand" href="/">
        <span class="brand__logo">✓</span>
        <span>Todo App API</span>
      </a>
      <div class="app-header__meta">
        <span class="chip chip--accent">OpenAPI 3.0.3</span>
        <span class="chip" id="backend-chip" title="Проверка прокси /api/* → бэкенд">
          <span class="chip__dot"></span><span id="backend-state">проверяю бэкенд…</span>
        </span>
        <a class="chip chip--link" href="/swagger.yaml">swagger.yaml</a>
        <a class="chip chip--link" href="https://github.com/dodry-question/todo-app-ts" target="_blank" rel="noopener">GitHub</a>
      </div>
    </div>
  </header>
  <div id="swagger-ui"></div>
  <footer class="app-footer">
    <span>Тёмная тема: <code>docs/swagger-theme.css</code></span>
    <span>Запросы идут через этот же адрес (прокси <code>/api/*</code>), CORS не нужен</span>
    <span>Токен: кнопка <strong>Authorize</strong> → вставь JWT из register/login без слова Bearer</span>
  </footer>
  <script src="/assets/swagger-ui-bundle.js"></script>
  <script src="/assets/swagger-ui-standalone-preset.js"></script>
  <script>
    window.ui = SwaggerUIBundle({
      url: '/swagger.yaml',
      dom_id: '#swagger-ui',
      deepLinking: true,
      tryItOutEnabled: true,
      persistAuthorization: true,
      supportedSubmitMethods: ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'],
      docExpansion: 'list',
      defaultModelsExpandDepth: 1,
      displayRequestDuration: true,
      filter: true,
      presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
      plugins: [SwaggerUIBundle.plugins.DownloadUrl],
      layout: 'StandaloneLayout',
      onComplete: function () { paintStatusCodes(); }
    });

    // Коды ответов окрашиваем по классу (2xx/3xx/4xx/5xx) — swagger-ui сам этого не делает.
    function paintStatusCodes() {
      document.querySelectorAll('.response-col_status').forEach(function (cell) {
        var m = (cell.textContent || '').match(/\b\d{3}\b/);
        if (!m) return;
        var n = Number(m[0]);
        var cls = n < 300 ? 'st-2xx' : n < 400 ? 'st-3xx' : n < 500 ? 'st-4xx' : 'st-5xx';
        if (!cell.classList.contains(cls)) {
          cell.classList.remove('st-2xx', 'st-3xx', 'st-4xx', 'st-5xx');
          cell.classList.add(cls);
        }
      });
    }
    if (window.MutationObserver) {
      var uiRoot = document.getElementById('swagger-ui');
      var paintTimer = null;
      new MutationObserver(function () {
        clearTimeout(paintTimer);
        paintTimer = setTimeout(paintStatusCodes, 80);
      }).observe(uiRoot, { childList: true, subtree: true });
    }

    // Индикатор бэкенда в шапке: ходим в прокси /api/*, 502 от прокси = бэкенд мёртв.
    (function checkBackend() {
      var chip = document.getElementById('backend-chip');
      var state = document.getElementById('backend-state');
      if (!chip || !state) return;
      fetch('/api/__health__', { method: 'GET' })
        .then(function (res) {
          var dead = res.status === 502;
          chip.classList.toggle('is-ok', !dead);
          chip.classList.toggle('is-bad', dead);
          state.textContent = dead ? 'бэкенд не запущен' : 'бэкенд на связи';
        })
        .catch(function () {
          chip.classList.add('is-bad');
          state.textContent = 'нет связи с прокси';
        });
    })();
  </script>
</body>
</html>`;

function send(res, code, body, ext) {
  const buf = Buffer.isBuffer(body) ? body : Buffer.from(body, 'utf8');
  res.writeHead(code, {
    'content-type': MIME[ext] || 'application/octet-stream',
    'content-length': buf.length,
    'cache-control': 'no-store', // при правках спеки/темы F5 сразу показывает новое
  });
  res.end(buf);
}

/** Пересылаем запрос на бэкенд как есть; если бэкенд мёртв — понятный JSON вместо пустоты. */
function proxy(req, res, url) {
  const target = new URL(url.pathname + url.search, BACKEND);
  const headers = { ...req.headers, host: target.host, 'accept-encoding': 'identity' };
  delete headers.connection;
  delete headers['content-length'];
  const upstream = http.request(target, { method: req.method, headers }, (up) => {
    const rh = { ...up.headers };
    delete rh.connection;
    delete rh['keep-alive'];
    delete rh['transfer-encoding'];
    res.writeHead(up.statusCode || 502, rh);
    up.pipe(res);
  });
  upstream.on('error', (e) => {
    const body = JSON.stringify({
      message: `Бэкенд по адресу ${BACKEND} не запущен (${e.code || e.message}). Запусти его и повтори запрос.`,
    });
    if (!res.headersSent) res.writeHead(502, { 'content-type': 'application/json; charset=utf-8' });
    res.end(body);
  });
  req.pipe(upstream);
}

if (!fs.existsSync(path.join(ASSETS, 'swagger-ui-bundle.js'))) {
  console.log('Ставлю swagger-ui-dist (один раз)...');
  execSync(`npm install swagger-ui-dist@5 --prefix "${ASSETS_ROOT}" --no-audit --no-fund --loglevel=error`, {
    stdio: 'inherit',
    timeout: 240000,
  });
}

http
  .createServer((req, res) => {
    const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
    const p = url.pathname;
    if (p === '/' || p === '/index.html') return send(res, 200, INDEX, '.html');
    if (p === '/theme.css') return send(res, 200, fs.readFileSync(THEME_PATH), '.css');
    if (p === '/favicon.ico') return send(res, 204, Buffer.alloc(0), '.svg');
    if (p === '/swagger.yaml') {
      let spec = fs.readFileSync(SPEC_PATH, 'utf8');
      // Подменяем только первый occurrence — это servers[0].url (проверено: он один).
      spec = spec.replace('http://localhost:8080', `http://127.0.0.1:${PORT}`);
      return send(res, 200, spec, '.yaml');
    }
    if (p.startsWith('/assets/')) {
      const file = path.join(ASSETS, path.basename(p));
      if (fs.existsSync(file)) return send(res, 200, fs.readFileSync(file), path.extname(file).slice(1));
      return send(res, 404, 'asset not found — запусти npm install swagger-ui-dist', '.html');
    }
    if (p.startsWith('/api/')) return proxy(req, res, url);
    return send(res, 404, 'Not found', '.html');
  })
  .listen(PORT, '127.0.0.1', () => {
    console.log(`Swagger UI (Try it out): http://127.0.0.1:${PORT}`);
    console.log(`Прокси /api/* -> ${BACKEND}`);
    console.log(`Спека: ${SPEC_PATH}`);
  });
