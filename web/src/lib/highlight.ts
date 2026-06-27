/**
 * Lightweight, dependency-free syntax highlighter for the preview
 * pane. Intentionally minimal — only the most common token kinds per
 * language. Returns HTML string with span.hl-* classes that the
 * PreviewView stylesheet defines.
 */

export interface HighlightRule {
  pattern: RegExp
  className: string
}

const ESCAPE_RE = /[&<>]/g
function escapeHtml(text: string): string {
  return text.replace(ESCAPE_RE, (ch) => {
    if (ch === '&') return '&amp;'
    if (ch === '<') return '&lt;'
    return '&gt;'
  })
}

interface LanguageSpec {
  keywords: string[]
  types?: string[]
  comment?: RegExp
  string?: RegExp
  number?: RegExp
}

const LANG: Record<string, LanguageSpec> = {
  js: {
    keywords: ['const', 'let', 'var', 'function', 'return', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue', 'new', 'delete', 'typeof', 'instanceof', 'in', 'of', 'this', 'super', 'class', 'extends', 'import', 'export', 'from', 'as', 'default', 'async', 'await', 'yield', 'throw', 'try', 'catch', 'finally', 'void', 'null', 'undefined', 'true', 'false', 'static', 'get', 'set'],
  },
  ts: {
    keywords: ['const', 'let', 'var', 'function', 'return', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue', 'new', 'delete', 'typeof', 'instanceof', 'in', 'of', 'this', 'super', 'class', 'extends', 'import', 'export', 'from', 'as', 'default', 'async', 'await', 'yield', 'throw', 'try', 'catch', 'finally', 'void', 'null', 'undefined', 'true', 'false', 'static', 'get', 'set', 'interface', 'type', 'enum', 'namespace', 'declare', 'readonly', 'public', 'private', 'protected', 'implements', 'abstract', 'keyof'],
    types: ['string', 'number', 'boolean', 'any', 'unknown', 'never', 'object', 'symbol', 'bigint', 'void'],
  },
  py: {
    keywords: ['and', 'as', 'assert', 'async', 'await', 'break', 'class', 'continue', 'def', 'del', 'elif', 'else', 'except', 'finally', 'for', 'from', 'global', 'if', 'import', 'in', 'is', 'lambda', 'nonlocal', 'not', 'or', 'pass', 'raise', 'return', 'try', 'while', 'with', 'yield', 'True', 'False', 'None', 'self'],
  },
  java: {
    keywords: ['abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'goto', 'if', 'implements', 'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new', 'package', 'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp', 'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient', 'try', 'void', 'volatile', 'while', 'true', 'false', 'null'],
    types: ['String', 'Integer', 'Long', 'Double', 'Float', 'Boolean', 'List', 'Map', 'Set', 'ArrayList', 'HashMap', 'HashSet', 'Optional'],
  },
  kt: {
    keywords: ['abstract', 'actual', 'annotation', 'as', 'break', 'by', 'catch', 'class', 'companion', 'const', 'continue', 'crossinline', 'data', 'do', 'dynamic', 'else', 'enum', 'expect', 'external', 'false', 'final', 'finally', 'for', 'fun', 'get', 'if', 'import', 'in', 'infix', 'init', 'inline', 'inner', 'interface', 'internal', 'is', 'lateinit', 'noinline', 'null', 'object', 'open', 'operator', 'out', 'override', 'package', 'private', 'protected', 'public', 'reified', 'return', 'sealed', 'set', 'super', 'suspend', 'tailrec', 'this', 'throw', 'true', 'try', 'typealias', 'val', 'var', 'vararg', 'when', 'where', 'while'],
    types: ['String', 'Int', 'Long', 'Double', 'Float', 'Boolean', 'Char', 'Byte', 'Short', 'Unit', 'Any', 'Nothing', 'List', 'Map', 'Set', 'Array'],
  },
  css: { keywords: [] },
  json: { keywords: ['true', 'false', 'null'] },
  xml: { keywords: [] },
  yaml: { keywords: ['true', 'false', 'null', 'yes', 'no', 'on', 'off'] },
  md: { keywords: [] },
}

function buildRules(spec: LanguageSpec | undefined, ext: string): HighlightRule[] {
  const rules: HighlightRule[] = []
  if (!spec) {
    if (ext === 'xml' || ext === 'md') {
      rules.push({ pattern: /<!--[\s\S]*?-->/g, className: 'hl-comment' })
    }
    if (ext === 'yaml' || ext === 'md') {
      rules.push({ pattern: /^\s*#.*$/gm, className: 'hl-comment' })
    }
    if (ext === 'md') {
      rules.push({ pattern: /^#{1,6}\s.+$/gm, className: 'hl-title' })
    }
    return rules
  }

  if (ext === 'py') {
    rules.push({ pattern: /^\s*#.*$/gm, className: 'hl-comment' })
    rules.push({ pattern: /"""[\s\S]*?"""|'''[\s\S]*?'''/g, className: 'hl-string' })
  } else if (ext === 'css') {
    rules.push({ pattern: /\/\*[\s\S]*?\*\//g, className: 'hl-comment' })
  } else {
    rules.push({ pattern: /\/\/[^\n]*/g, className: 'hl-comment' })
    rules.push({ pattern: /\/\*[\s\S]*?\*\//g, className: 'hl-comment' })
  }

  rules.push({ pattern: /"(?:\\.|[^"\\\n])*"/g, className: 'hl-string' })
  rules.push({ pattern: /'(?:\\.|[^'\\\n])*'/g, className: 'hl-string' })
  rules.push({ pattern: /`(?:\\.|[^`\\])*`/g, className: 'hl-string' })
  rules.push({ pattern: /\b\d+(?:\.\d+)?\b/g, className: 'hl-number' })

  const kws = spec.keywords ?? []
  if (kws.length) {
    rules.push({
      pattern: new RegExp(`\\b(?:${kws.join('|')})\\b`, 'g'),
      className: 'hl-keyword',
    })
  }
  const types = spec.types ?? []
  if (types.length) {
    rules.push({
      pattern: new RegExp(`\\b(?:${types.join('|')})\\b`, 'g'),
      className: 'hl-type',
    })
  }
  return rules
}

const LANG_BY_EXT: Record<string, keyof typeof LANG> = {
  js: 'js', jsx: 'js', mjs: 'js', cjs: 'js',
  ts: 'ts', tsx: 'ts', mts: 'ts', cts: 'ts',
  py: 'py', pyw: 'py',
  java: 'java',
  kt: 'kt', kts: 'kt',
  css: 'css', scss: 'css',
  json: 'json', jsonc: 'json',
  xml: 'xml', html: 'xml', svg: 'xml', htm: 'xml',
  yaml: 'yaml', yml: 'yaml',
  md: 'md', markdown: 'md',
}

export function highlightCode(code: string, fileName: string): string {
  const i = fileName.lastIndexOf('.')
  const ext = i > 0 ? fileName.slice(i + 1).toLowerCase() : ''
  const langKey = LANG_BY_EXT[ext]
  if (!langKey) {
    return escapeHtml(code)
  }
  const spec = LANG[langKey]
  const rules = buildRules(spec, ext)

  if (ext === 'json') {
    return highlightJson(code)
  }
  if (ext === 'xml' || ext === 'html' || ext === 'svg' || ext === 'htm') {
    return highlightXml(code)
  }
  if (ext === 'css' || ext === 'scss') {
    return highlightCss(code)
  }
  if (ext === 'yaml' || ext === 'yml') {
    return highlightYaml(code)
  }
  if (ext === 'md' || ext === 'markdown') {
    return highlightMarkdown(code)
  }

  const escaped = escapeHtml(code)
  let out = escaped
  for (const rule of rules) {
    out = out.replace(rule.pattern, (match) => `<span class="${rule.className}">${match}</span>`)
  }
  return out
}

function highlightJson(code: string): string {
  const escaped = escapeHtml(code)
  return escaped
    .replace(/(&quot;)([^&]*?)(&quot;)(\s*:)/g, '<span class="hl-key">$1$2$3</span>$4')
    .replace(/: (&quot;[^&]*?&quot;)/g, ': <span class="hl-string">$1</span>')
    .replace(/\b(true|false|null)\b/g, '<span class="hl-keyword">$1</span>')
    .replace(/\b(-?\d+(?:\.\d+)?)\b/g, '<span class="hl-number">$1</span>')
}

function highlightXml(code: string): string {
  const escaped = escapeHtml(code)
  return escaped
    .replace(/(&lt;!--[\s\S]*?--&gt;)/g, '<span class="hl-comment">$1</span>')
    .replace(/(&lt;\/?)([a-zA-Z][\w:-]*)/g, '$1<span class="hl-tag">$2</span>')
    .replace(/([a-zA-Z-]+)=(&quot;[^&]*?&quot;)/g, '<span class="hl-attr">$1</span>=<span class="hl-string">$2</span>')
}

function highlightCss(code: string): string {
  const escaped = escapeHtml(code)
  return escaped
    .replace(/(\/\*[\s\S]*?\*\/)/g, '<span class="hl-comment">$1</span>')
    .replace(/([.#][\w-]+|@[\w-]+)/g, '<span class="hl-key">$1</span>')
    .replace(/([\w-]+)(\s*:)/g, '<span class="hl-attr">$1</span>$2')
    .replace(/(#[0-9a-fA-F]{3,8})/g, '<span class="hl-number">$1</span>')
    .replace(/\b(\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|s|ms)?)\b/g, '<span class="hl-number">$1</span>')
}

function highlightYaml(code: string): string {
  const escaped = escapeHtml(code)
  return escaped
    .replace(/(^|\n)([ \t]*)(#.*)/g, '$1$2<span class="hl-comment">$3</span>')
    .replace(/(^|\n)([ \t]*)([\w-]+)(:)/g, '$1$2<span class="hl-key">$3</span>$4')
    .replace(/: ((?:&quot;|&#39;)?[^&<\n]*)/g, (_m, p1, p2) => {
      return `: <span class="hl-string">${p1}${p2}</span>`
    })
    .replace(/\b(true|false|null|yes|no|on|off)\b/g, '<span class="hl-keyword">$1</span>')
}

function highlightMarkdown(code: string): string {
  const escaped = escapeHtml(code)
  return escaped
    .replace(/(^|\n)(#{1,6})\s+([^\n]+)/g, '$1<span class="hl-title">$2 $3</span>')
    .replace(/```[\s\S]*?```/g, (m) => `<span class="hl-string">${m}</span>`)
    .replace(/`([^`\n]+)`/g, '<span class="hl-string">$1</span>')
    .replace(/(^|\n)\s*&gt; ([^\n]+)/g, '$1<span class="hl-comment">&gt; $2</span>')
}