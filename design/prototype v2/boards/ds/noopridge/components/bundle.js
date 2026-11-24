/* @ds-bundle: {"format":4,"namespace":"NoopRidge","components":[{"name":"LiquidOrb"},{"name":"OrbTrio"},{"name":"GlassCard"},{"name":"MetricTile"},{"name":"SynthesisScale"},{"name":"Chip"},{"name":"Button"},{"name":"SectionHeader"},{"name":"ListRow"},{"name":"TabBar"},{"name":"Icon"}]} */
(function () {
  var React = window.React;
  var h = React.createElement;
  var uid = 0;
  function nextId(p) { uid += 1; return 'nr-' + p + '-' + uid; }
  function cx() { return Array.prototype.slice.call(arguments).filter(Boolean).join(' '); }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

  var LIQUIDS = {
    charge: { top: 'var(--charge)', deep: 'var(--charge-deep)', glow: 'var(--glow-charge)' },
    effort: { top: 'var(--effort)', deep: 'var(--effort-deep)', glow: 'var(--glow-effort)' },
    rest: { top: 'var(--rest)', deep: 'var(--rest-deep)', glow: 'var(--glow-rest)' },
    brand: { top: 'var(--brand)', deep: 'var(--brand-deep)', glow: 'var(--glow-rest)' },
    battery: { top: 'var(--battery)', deep: 'var(--battery-deep)', glow: '0 0 0 transparent' }
  };

  /* ---------- Icon: 24px grid, 1.75 stroke, round caps ---------- */
  var ICONS = {
    leaf: 'M5 19c0-8 5-13 14-14 0 9-5 14-13 14M5 19l7-7',
    bolt: 'M13 3 5 13.5h6L10 21l8-10.5h-6z',
    moon: 'M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z',
    heart: 'M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z',
    flame: 'M12 21a6 6 0 0 0 6-6c0-4-3-6-4-10-2 2-3 4-3 6-1-1-2-2-2-3-2 2-3 4-3 7a6 6 0 0 0 6 6z',
    pulse: 'M3 12h4l2-5 4 10 2-5h6',
    target: 'M12 12m-8 0a8 8 0 1 0 16 0a8 8 0 1 0-16 0M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0',
    hourglass: 'M7 3h10M7 21h10M8 3c0 5 8 5 8 9s-8 4-8 9M16 3c0 5-8 5-8 9',
    wind: 'M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3',
    run: 'M15 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3M13 8.5l-3 4 3 2.5-1.5 5M10 12.5l-4 .5M13 8.5l2.5 2.5H19M13 15l3.5 1.5L15 20',
    dumbbell: 'M3 10v4M6 7v10M18 7v10M21 10v4M6 12h12',
    sun: 'M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4',
    peak: 'M2.5 19.5 9 8.5l3.2 5.2L15 9.5l6.5 10zM7.2 11.6l1.8 1.4 1.4-1.3',
    home: 'M4 11 12 4l8 7v9h-5v-6H9v6H4z',
    bars: 'M6 20v-6M12 20V6M18 20v-10',
    dots: 'M6 12h.01M12 12h.01M18 12h.01',
    plus: 'M12 5v14M5 12h14',
    'chevron-right': 'M9 5l7 7-7 7',
    'chevron-down': 'M5 9l7 7 7-7',
    'chevron-up': 'M5 15l7-7 7 7',
    calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4'
  };
  function Icon(props) {
    var size = props.size || 20;
    var d = ICONS[props.name] || ICONS.dots;
    var isDots = props.name === 'dots';
    return h('svg', {
      className: cx('nr-icon', props.className), width: size, height: size, viewBox: '0 0 24 24',
      fill: 'none', stroke: 'currentColor', strokeWidth: isDots ? 3 : (props.strokeWidth || 1.75),
      strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': props.label ? undefined : true,
      role: props.label ? 'img' : undefined, 'aria-label': props.label, style: props.color ? { color: props.color } : undefined
    }, h('path', { d: d }));
  }

  /* ---------- wave paths: one gentle wavelength per orb width, drawn 4x wide so it can slide ---------- */
  function waveLine(x0, width, level, amp, phase) {
    var seg = width / 2, d = 'M' + x0 + ' ' + level, x = x0, i;
    for (i = 0; i < 8; i += 1) {
      var up = (i + phase) % 2 === 0 ? -amp : amp;
      d += ' Q' + (x + seg / 2) + ' ' + (level + up) + ' ' + (x + seg) + ' ' + level;
      x += seg;
    }
    return { d: d, end: x };
  }
  function wavePath(x0, width, level, amp, bottom, phase) {
    var w = waveLine(x0, width, level, amp, phase);
    return w.d + ' L' + w.end + ' ' + bottom + ' L' + x0 + ' ' + bottom + ' Z';
  }

  /* ---------- LiquidOrb: a glossy glass bubble holding bright, luminous liquid ---------- */
  var ORB_SIZES = { sm: 64, md: 104, lg: 148 };
  function mixWhite(c, pct) { return 'color-mix(in srgb, ' + c + ' ' + (100 - pct) + '%, #ffffff)'; }
  function LiquidOrb(props) {
    var liquid = LIQUIDS[props.liquid] || LIQUIDS.brand;
    var sizeProp = props.size;
    if (typeof sizeProp === 'string' && /^\d+(\.\d+)?$/.test(sizeProp)) sizeProp = parseFloat(sizeProp);
    var size = typeof sizeProp === 'number' ? sizeProp : (ORB_SIZES[sizeProp] || ORB_SIZES.md);
    var value = typeof props.value === 'string' ? parseFloat(props.value) : props.value;
    var max = props.max ? parseFloat(props.max) : 100;
    var frac = clamp((props.fill != null ? parseFloat(props.fill) : value / max), 0, 1);
    var ids = React.useMemo(function () {
      return { clip: nextId('clip'), sphere: nextId('sph'), grad: nextId('grad'), back: nextId('back'), shade: nextId('shade'),
        gloss: nextId('gloss'), sheen: nextId('sheen'), rim: nextId('rim'), base: nextId('base'), soft: nextId('soft'), shadow: nextId('shd') };
    }, []);
    var R = 49.2, level = 50 + R - 2 * R * frac;
    var amp = frac <= 0.02 || frac >= 0.98 ? 0 : 7;
    var display = props.display != null ? String(props.display) : String(props.value);
    var fs = display.length > 3 ? 22 : (size <= 64 ? 32 : 34);
    var calm = props.still === true || props.still === 'true';
    var url = function (id) { return 'url(#' + id + ')'; };
    var front = waveLine(-100, 100, level, amp, 0);
    var svg = h('svg', { viewBox: '0 0 100 100', width: size, height: size, className: 'nr-orb-svg', 'aria-hidden': true },
      h('defs', null,
        h('clipPath', { id: ids.clip }, h('circle', { cx: 50, cy: 50, r: R })),
        /* the empty glass: deep, slightly blue, lighter toward the top */
        h('radialGradient', { id: ids.sphere, cx: '50%', cy: '30%', r: '75%' },
          h('stop', { offset: '0%', stopColor: '#3a4868' }), h('stop', { offset: '55%', stopColor: '#1c2539' }), h('stop', { offset: '100%', stopColor: '#111827' })),
        /* liquid body: pale and luminous at the surface, full colour, then deeper at the bottom */
        h('linearGradient', { id: ids.grad, gradientUnits: 'userSpaceOnUse', x1: 0, y1: level - amp, x2: 0, y2: 98 },
          h('stop', { offset: '0%', style: { stopColor: mixWhite(liquid.top, 38) } }),
          h('stop', { offset: '30%', style: { stopColor: liquid.top } }),
          h('stop', { offset: '100%', style: { stopColor: 'color-mix(in srgb, ' + liquid.top + ' 55%, ' + liquid.deep + ')' } })),
        h('linearGradient', { id: ids.back, x1: 0, y1: 0, x2: 1, y2: 0 },
          h('stop', { offset: '0%', style: { stopColor: mixWhite(liquid.top, 55), stopOpacity: 0.75 } }),
          h('stop', { offset: '100%', style: { stopColor: mixWhite(liquid.top, 30), stopOpacity: 0.55 } })),
        /* roundness: the liquid darkens toward the lower-right edge of the bubble */
        h('radialGradient', { id: ids.shade, cx: '40%', cy: '38%', r: '62%' },
          h('stop', { offset: '55%', stopColor: '#000', stopOpacity: 0 }), h('stop', { offset: '100%', stopColor: '#000', stopOpacity: 0.4 })),
        h('linearGradient', { id: ids.sheen, x1: 0, y1: 0, x2: 1, y2: 0 },
          h('stop', { offset: '0%', stopColor: '#fff', stopOpacity: 0.16 }), h('stop', { offset: '45%', stopColor: '#fff', stopOpacity: 0 }),
          h('stop', { offset: '100%', stopColor: '#000', stopOpacity: 0.12 })),
        /* the bright crescent along the top of the glass */
        h('linearGradient', { id: ids.gloss, x1: 0, y1: 0, x2: 0, y2: 1 },
          h('stop', { offset: '0%', stopColor: '#fff', stopOpacity: 0.6 }), h('stop', { offset: '100%', stopColor: '#fff', stopOpacity: 0 })),
        h('linearGradient', { id: ids.rim, x1: 0, y1: 0, x2: 0, y2: 1 },
          h('stop', { offset: '0%', stopColor: '#fff', stopOpacity: 0.55 }),
          h('stop', { offset: '50%', stopColor: '#fff', stopOpacity: 0.28 }),
          h('stop', { offset: '100%', style: { stopColor: mixWhite(liquid.top, 40), stopOpacity: 0.8 } })),
        h('radialGradient', { id: ids.base, cx: '50%', cy: '100%', r: '55%' },
          h('stop', { offset: '0%', stopColor: '#fff', stopOpacity: 0.28 }), h('stop', { offset: '100%', stopColor: '#fff', stopOpacity: 0 })),
        h('filter', { id: ids.soft, x: '-30%', y: '-30%', width: '160%', height: '160%' }, h('feGaussianBlur', { stdDeviation: 1.6 })),
        h('filter', { id: ids.shadow, x: '-20%', y: '-20%', width: '140%', height: '140%' },
          h('feDropShadow', { dx: 0, dy: 1, stdDeviation: 1.8, floodColor: '#06090f', floodOpacity: 0.7 }))),
      h('circle', { cx: 50, cy: 50, r: R, fill: url(ids.sphere) }),
      h('g', { clipPath: url(ids.clip) },
        /* the far side of the surface, seen through the glass */
        h('g', { className: calm ? '' : 'nr-wave nr-wave-back' },
          h('path', { d: wavePath(-100, 100, level - 3.2, amp * 0.9, 100, 1), fill: url(ids.back) })),
        h('g', { className: calm ? '' : 'nr-wave nr-wave-front' },
          h('path', { d: wavePath(-100, 100, level, amp, 100, 0), fill: url(ids.grad) }),
          frac > 0.02 && frac < 0.98 ? h('path', { d: front.d, fill: 'none', stroke: '#fff', strokeWidth: 0.9, opacity: 0.55 }) : null),
        h('rect', { x: 0, y: 0, width: 100, height: 100, fill: url(ids.sheen) }),
        h('circle', { cx: 50, cy: 50, r: R, fill: url(ids.shade) }),
        /* a soft glint inside the liquid, lower-left */
        frac > 0.15 ? h('ellipse', { cx: 30, cy: Math.min(86, level + (96 - level) * 0.55), rx: 9, ry: 4, transform: 'rotate(-25 30 ' + Math.min(86, level + (96 - level) * 0.55) + ')', fill: '#fff', opacity: 0.22, filter: url(ids.soft) }) : null,
        h('circle', { cx: 50, cy: 50, r: R, fill: url(ids.base) }),
        /* glass reflections: a bright crescent hugging the top, and a small soft spot */
        h('path', { d: 'M13 40 A38 38 0 0 1 87 40 A40 34 0 0 0 13 40 Z', fill: url(ids.gloss) }),
        h('ellipse', { cx: 32, cy: 22, rx: 7, ry: 3.2, transform: 'rotate(-30 32 22)', fill: '#fff', opacity: 0.35, filter: url(ids.soft) }),
        h('g', { filter: url(ids.shadow) },
          h('text', { x: 50, y: 50, dy: '0.35em', textAnchor: 'middle', className: 'nr-orb-num', style: { fontSize: fs + 'px', fill: 'var(--ink)' } }, display))),
      /* the glass edge: bright at the top, catching the liquid colour at the bottom */
      h('circle', { cx: 50, cy: 50, r: R - 0.4, fill: 'none', stroke: url(ids.rim), strokeWidth: 1.4 }));
    var Tag = props.onClick ? 'button' : 'div';
    return h(Tag, {
      className: cx('nr-orb', props.className), onClick: props.onClick, type: props.onClick ? 'button' : undefined,
      role: props.onClick ? undefined : 'img',
      'aria-label': props.ariaLabel || ((props.label || '') + ' ' + display + (props.status ? ', ' + props.status : ''))
    },
      h('span', { className: 'nr-orb-well', style: { boxShadow: (props.glow === false || props.glow === 'false') ? 'none' : liquid.glow, width: size, height: size } }, svg),
      props.label ? h('span', { className: 'nr-orb-label' }, props.label, props.onClick ? h(Icon, { name: 'chevron-right', size: 14 }) : null) : null,
      props.status ? h('span', { className: 'nr-orb-status' }, props.statusIcon ? h(Icon, { name: props.statusIcon, size: 16, color: liquid.top }) : null, props.status) : null);
  }

  /* ---------- OrbTrio: the Today header ---------- */
  function OrbTrio(props) {
    return h('section', { className: 'nr-glass nr-trio', 'aria-label': 'Today’s scores' },
      props.sync ? h('div', { className: 'nr-trio-sync' }, h(Chip, { variant: 'live' }, props.sync)) : null,
      h('div', { className: 'nr-trio-row' },
        (props.orbs || []).map(function (o, i) { return h(LiquidOrb, Object.assign({ key: i, size: props.size || 96 }, o)); })));
  }

  /* ---------- GlassCard ---------- */
  function GlassCard(props) {
    var tone = props.tone || 'neutral';
    return h(props.as || 'div', { className: cx('nr-glass', 'nr-card', 'nr-tone-' + tone, props.className), style: props.style }, props.children);
  }

  /* ---------- Sparkline: smooth area, full-bleed or inline ---------- */
  function smoothPath(c) {
    var d = 'M' + c[0][0].toFixed(2) + ' ' + c[0][1].toFixed(2), i;
    for (i = 0; i < c.length - 1; i += 1) {
      var p0 = c[i - 1] || c[i], p1 = c[i], p2 = c[i + 1], p3 = c[i + 2] || p2;
      d += ' C' + (p1[0] + (p2[0] - p0[0]) / 6).toFixed(2) + ' ' + (p1[1] + (p2[1] - p0[1]) / 6).toFixed(2) + ' ' +
        (p2[0] - (p3[0] - p1[0]) / 6).toFixed(2) + ' ' + (p2[1] - (p3[1] - p1[1]) / 6).toFixed(2) + ' ' + p2[0].toFixed(2) + ' ' + p2[1].toFixed(2);
    }
    return d;
  }
  function Sparkline(props) {
    var pts = props.points || [];
    var gid = React.useMemo(function () { return nextId('spark'); }, []);
    if (pts.length < 2) return null;
    var fill = !!props.fill;
    var w = fill ? 100 : (props.width || 80), hgt = fill ? 100 : (props.height || 28);
    var top = fill ? (props.top != null ? props.top : 56) : 3, bot = fill ? (props.bottom != null ? props.bottom : 80) : hgt - 3;
    var min = Math.min.apply(null, pts), max = Math.max.apply(null, pts), span = max - min || 1;
    var coords = pts.map(function (p, i) { return [(i / (pts.length - 1)) * w, bot - ((p - min) / span) * (bot - top)]; });
    var line = smoothPath(coords);
    return h('svg', { className: cx('nr-spark', fill && 'nr-spark-fill'), width: fill ? '100%' : w, height: fill ? '100%' : hgt,
      viewBox: '0 0 ' + w + ' ' + hgt, preserveAspectRatio: fill ? 'none' : undefined, 'aria-hidden': true },
      h('defs', null, h('linearGradient', { id: gid, x1: 0, y1: 0, x2: 0, y2: 1 },
        h('stop', { offset: '0%', style: { stopColor: props.color, stopOpacity: fill ? 0.32 : 0.35 } }),
        h('stop', { offset: '100%', style: { stopColor: props.color, stopOpacity: 0 } }))),
      h('path', { d: line + ' L' + w + ' ' + hgt + ' L0 ' + hgt + ' Z', fill: 'url(#' + gid + ')' }),
      h('path', { d: line, fill: 'none', style: { stroke: props.color }, strokeWidth: 2, vectorEffect: 'non-scaling-stroke', strokeLinecap: 'round', strokeLinejoin: 'round', className: 'nr-spark-line' }));
  }

  /* ---------- MetricTile: trend fills the tile, figure floats on top ---------- */
  var ACCENTS = { charge: '--charge', effort: '--effort', rest: '--rest', hrv: '--data-hrv', heart: '--data-heart', energy: '--data-energy', breath: '--data-breath', depleted: '--state-depleted' };
  function MetricTile(props) {
    var color = 'var(' + (ACCENTS[props.accent] || '--brand') + ')';
    return h('div', { className: cx('nr-glass', 'nr-tile', props.className), style: { '--tile-accent': color } },
      props.trend ? h('div', { className: 'nr-tile-chart' }, h(Sparkline, { points: props.trend, color: color, fill: true })) : null,
      h('div', { className: 'nr-tile-content' },
        h('div', { className: 'nr-tile-head' }, props.icon ? h(Icon, { name: props.icon, size: 18, color: color }) : null, h('span', { className: 'nr-eyebrow' }, props.label)),
        h('span', { className: 'nr-tile-value' }, props.value, props.unit ? h('span', { className: 'nr-tile-unit' }, props.unit) : null),
        h('div', { className: 'nr-tile-foot' },
          props.progress != null ? h('div', { className: 'nr-bar', role: 'presentation' }, h('span', { style: { width: clamp(props.progress, 0, 1) * 100 + '%' } })) : null,
          props.delta ? h('div', { className: 'nr-tile-delta' }, props.delta) : null,
          props.note ? h('div', { className: 'nr-tile-note' }, h('span', { className: 'nr-dot' }), props.note) : null)));
  }

  /* ---------- SynthesisScale ---------- */
  var STATES = ['depleted', 'low', 'moderate', 'good', 'ready'];
  var STATE_LABEL = { depleted: 'Depleted', low: 'Low', moderate: 'Moderate', good: 'Good', ready: 'Ready' };
  function SynthesisScale(props) {
    var state = STATES.indexOf(props.state) >= 0 ? props.state : 'moderate';
    var pos = props.value != null ? clamp(props.value, 0, 100) : STATES.indexOf(state) * 25;
    var st = React.useState(props.defaultOpen !== false), open = st[0], setOpen = st[1];
    return h('section', { className: cx('nr-glass', 'nr-card', 'nr-synth', 'nr-synth-' + state, open && 'is-open'), style: { '--state': 'var(--state-' + state + ')' } },
      h('button', { type: 'button', className: 'nr-synth-head', 'aria-expanded': open, onClick: function () { setOpen(!open); } },
        h('span', { className: 'nr-synth-title' }, h('span', { className: 'nr-eyebrow' }, 'Synthesis'), h('span', { className: 'nr-synth-word' }, STATE_LABEL[state])),
        h(Icon, { name: open ? 'chevron-up' : 'chevron-down', size: 20 })),
      open ? h('p', { className: 'nr-synth-msg' }, props.message) : null,
      open ? h('div', { className: 'nr-synth-scale' },
        h('div', { className: 'nr-synth-track' }, h('span', { className: 'nr-synth-thumb', style: { left: pos + '%' } })),
        h('div', { className: 'nr-synth-ticks' }, STATES.map(function (s) {
          return h('span', { key: s, className: s === state ? 'is-on' : '' }, STATE_LABEL[s]);
        }))) : null);
  }

  /* ---------- Chip ---------- */
  function Chip(props) {
    var v = props.variant || 'outline';
    return h(props.onClick ? 'button' : 'span', {
      type: props.onClick ? 'button' : undefined, onClick: props.onClick, 'aria-pressed': props.onClick ? !!props.selected : undefined,
      className: cx('nr-chip', 'nr-chip-' + v, props.selected && 'is-selected', props.className)
    }, (v === 'live' || props.dot) ? h('span', { className: 'nr-dot' }) : null, props.children, props.icon ? h(Icon, { name: props.icon, size: 16 }) : null);
  }

  /* ---------- Button ---------- */
  function Button(props) {
    var v = props.variant || 'primary';
    var rest = Object.assign({}, props); delete rest.variant; delete rest.liquid; delete rest.icon; delete rest.className;
    return h('button', Object.assign({ type: 'button' }, rest, {
      className: cx('nr-btn', 'nr-btn-' + v, props.liquid && 'nr-liquid-' + props.liquid, props.className)
    }), props.icon ? h(Icon, { name: props.icon, size: 18 }) : null, props.children);
  }

  /* ---------- SectionHeader ---------- */
  function SectionHeader(props) {
    return h('header', { className: 'nr-section-head' },
      h('div', null, props.eyebrow ? h('div', { className: 'nr-eyebrow' }, props.eyebrow) : null,
        h(props.level === 1 ? 'h1' : 'h2', { className: props.level === 1 ? 'nr-title-xl' : 'nr-title-lg' }, props.title),
        props.subtitle ? h('p', { className: 'nr-subtitle' }, props.subtitle) : null),
      props.aside ? h('div', { className: 'nr-section-aside' }, props.aside) : null);
  }

  /* ---------- ListRow ---------- */
  function ListRow(props) {
    return h(props.onClick ? 'button' : 'div', { type: props.onClick ? 'button' : undefined, onClick: props.onClick, className: cx('nr-row', props.className) },
      props.icon ? h('span', { className: 'nr-row-icon' }, h(Icon, { name: props.icon, size: 22 })) : null,
      h('span', { className: 'nr-row-text' }, h('span', { className: 'nr-row-title' }, props.title),
        props.meta ? h('span', { className: 'nr-row-meta' }, [].concat(props.meta).join('  ·  ')) : null),
      h(Icon, { name: 'chevron-right', size: 18, className: 'nr-row-chev' }));
  }

  /* ---------- TabBar: five tabs, no FAB ---------- */
  var DEFAULT_TABS = [
    { id: 'today', label: 'Today', icon: 'sun' },
    { id: 'trends', label: 'Trends', icon: 'bars' },
    { id: 'baseline', label: 'Baseline', icon: 'peak' },
    { id: 'sleep', label: 'Sleep', icon: 'moon' },
    { id: 'more', label: 'More', icon: 'dots' }
  ];
  function TabBar(props) {
    var tabs = (props.tabs || DEFAULT_TABS).slice(0, 5);
    var active = props.active || tabs[0].id;
    return h('nav', { className: 'nr-glass nr-tabbar', 'aria-label': 'Main', style: { gridTemplateColumns: 'repeat(' + tabs.length + ', minmax(0, 1fr))' } },
      tabs.map(function (t) {
        var on = t.id === active;
        return h('button', { key: t.id, type: 'button', className: cx('nr-tab', on && 'is-active'), 'aria-current': on ? 'page' : undefined,
          onClick: props.onChange ? function () { props.onChange(t.id); } : undefined },
          h('span', { className: 'nr-tab-icon' }, h(Icon, { name: t.icon, size: 24, strokeWidth: on ? 2 : 1.75 })), h('span', null, t.label));
      }));
  }

  window.NoopRidge = Object.assign(window.NoopRidge || {}, {
    LiquidOrb: LiquidOrb, OrbTrio: OrbTrio, GlassCard: GlassCard, MetricTile: MetricTile, SynthesisScale: SynthesisScale,
    Chip: Chip, Button: Button, SectionHeader: SectionHeader, ListRow: ListRow, TabBar: TabBar, Icon: Icon, Sparkline: Sparkline
  });
})();
