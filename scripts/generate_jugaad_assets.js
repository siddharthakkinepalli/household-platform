/* eslint-disable */
/**
 * Generates downloadable SVG assets for the JUGAAD icon pack.
 *
 *   node scripts/generate_jugaad_assets.js
 *
 * Writes into frontend/public/assets/jugaad/.
 */
const fs = require("fs");
const path = require("path");

const OUT = path.resolve(__dirname, "..", "frontend", "public", "assets", "jugaad");
fs.mkdirSync(OUT, { recursive: true });

// --- Aurora palette (matches the reference) ---
const P = {
  bgFrom: "#2A3CE8",
  bgVia: "#5B45F0",
  bgTo: "#9333EA",
  ring: "#A5B4FC",
  letter: "#FFFFFF",
  icons: ["#22D3A6","#FBBF24","#A78BFA","#60A5FA","#F59E0B","#2DD4BF","#FB7185","#7DD3FC"],
};

// --- Geometry ---
const SIZE = 512;
const CX = 256, CY = 256, RING = 188;

const positions = Array.from({ length: 8 }).map((_, i) => {
  const a = (-90 + i * 45) * Math.PI / 180;
  return {
    x: +(CX + RING * Math.cos(a)).toFixed(2),
    y: +(CY + RING * Math.sin(a)).toFixed(2),
  };
});

// --- Satellite glyphs (centered on 0,0 in 60x60 box) ---
const SAT = {
  cart:    `<path d="M -22 -16 L -14 -16 L -8 8 L 16 8 L 22 -10 L -10 -10"/><circle cx="-6" cy="18" r="3"/><circle cx="14" cy="18" r="3"/>`,
  tray:    `<path d="M -8 -22 Q -8 -16 -2 -16"/><path d="M 0 -22 Q 0 -16 6 -16"/><path d="M -22 0 Q 0 -22 22 0 Z"/><path d="M -26 6 L 26 6"/>`,
  calendar:`<rect x="-20" y="-16" width="40" height="36" rx="4"/><path d="M -20 -6 L 20 -6"/><path d="M -10 -22 L -10 -10"/><path d="M 10 -22 L 10 -10"/><circle cx="-6" cy="6" r="1.5" fill="currentColor" stroke="none"/><circle cx="6" cy="6" r="1.5" fill="currentColor" stroke="none"/>`,
  document:`<path d="M -16 -22 L 10 -22 L 20 -12 L 20 22 L -16 22 Z"/><path d="M 10 -22 L 10 -12 L 20 -12"/><path d="M -8 4 L 12 4"/><path d="M -8 14 L 12 14"/>`,
  folder:  `<path d="M -22 -14 L -6 -14 L -2 -8 L 22 -8 L 22 18 L -22 18 Z"/>`,
  shield:  `<path d="M 0 -22 L 18 -16 L 18 4 Q 18 18 0 22 Q -18 18 -18 4 L -18 -16 Z"/><path d="M -8 0 L -2 6 L 10 -6"/>`,
  bell:    `<path d="M -16 8 Q -16 -16 0 -18 Q 16 -16 16 8 L 20 14 L -20 14 Z"/><path d="M -4 18 Q 0 22 4 18"/><path d="M 0 -22 L 0 -18"/>`,
  receipt: `<path d="M -14 -22 L 14 -22 L 14 22 L 8 18 L 2 22 L -4 18 L -10 22 L -14 18 Z"/><path d="M -8 -12 L 8 -12"/><path d="M -8 -4 L 4 -4"/><path d="M -4 -4 Q 4 -4 4 4 Q 4 8 -4 8 L 4 14"/>`,
};

const SAT_ORDER = ["cart","tray","calendar","document","folder","shield","bell","receipt"];

// Build the satellites group (no positioning of group; absolute via translate)
function satellitesSVG(animated = false) {
  return positions.map((p, i) => {
    const color = P.icons[i];
    const inner = SAT[SAT_ORDER[i]];
    const counter = animated
      ? `<animateTransform attributeName="transform" type="rotate" from="0" to="-360" dur="24s" repeatCount="indefinite"/>`
      : "";
    return `
    <g transform="translate(${p.x} ${p.y})">
      <circle r="22" fill="#ffffff" fill-opacity="0.06"/>
      <g stroke="${color}" fill="none" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" color="${color}">
        ${inner}
        ${counter}
      </g>
    </g>`;
  }).join("\n");
}

// "J" letter path
const J_PATH = `M 178 138 L 334 138 Q 354 138 354 158 L 354 178 Q 354 198 334 198 L 290 198 L 290 312 Q 290 384 224 384 Q 158 384 158 312 L 158 296 Q 158 280 174 280 L 198 280 Q 214 280 214 296 L 214 312 Q 214 332 224 332 Q 234 332 234 312 L 234 198 L 178 198 Q 158 198 158 178 L 158 158 Q 158 138 178 138 Z`;

const DEFS = `
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0%"   stop-color="${P.bgFrom}"/>
    <stop offset="55%"  stop-color="${P.bgVia}"/>
    <stop offset="100%" stop-color="${P.bgTo}"/>
  </linearGradient>
  <radialGradient id="gloss" cx="30%" cy="20%" r="60%">
    <stop offset="0%"  stop-color="#ffffff" stop-opacity="0.35"/>
    <stop offset="60%" stop-color="#ffffff" stop-opacity="0"/>
  </radialGradient>
  <radialGradient id="glow" cx="50%" cy="55%" r="45%">
    <stop offset="0%"   stop-color="#ffffff" stop-opacity="0.18"/>
    <stop offset="100%" stop-color="#ffffff" stop-opacity="0"/>
  </radialGradient>
  <filter id="jshadow" x="-20%" y="-20%" width="140%" height="140%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="6"/>
    <feOffset dx="0" dy="6" result="off"/>
    <feComponentTransfer><feFuncA type="linear" slope="0.35"/></feComponentTransfer>
    <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <clipPath id="squircle"><rect x="0" y="0" width="512" height="512" rx="120" ry="120"/></clipPath>
</defs>`;

// Noise dots for texture
const NOISE = (() => {
  let s = "";
  for (let i = 0; i < 24; i++) {
    s += `<circle cx="${(i*79)%512}" cy="${(i*137)%512}" r="${(i%3)+0.6}"/>`;
  }
  return `<g opacity="0.18" fill="#ffffff">${s}</g>`;
})();

// ---------- 1. Static 512 SVG ----------
const STATIC_SVG = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <title>JUGAAD App Icon</title>
  ${DEFS}
  <g clip-path="url(#squircle)">
    <rect width="512" height="512" fill="url(#bg)"/>
    <rect width="512" height="512" fill="url(#glow)"/>
    <rect width="512" height="512" fill="url(#gloss)"/>
    ${NOISE}
    <circle cx="${CX}" cy="${CY}" r="${RING}" fill="none" stroke="${P.ring}" stroke-opacity="0.55" stroke-width="2.5" stroke-dasharray="5 9"/>
    ${satellitesSVG(false)}
    <g filter="url(#jshadow)">
      <path d="${J_PATH}" fill="${P.letter}"/>
    </g>
  </g>
</svg>
`;

// ---------- 2. Animated SVG (CSS keyframes, works in browsers) ----------
const ANIMATED_STYLE = `
<style><![CDATA[
  .orbit { transform-origin: 256px 256px; animation: jg-orbit 26s linear infinite; }
  .ring  { transform-origin: 256px 256px; animation: jg-orbit 80s linear infinite reverse; }
  .letter{ transform-origin: 256px 256px; animation: jg-pulse 4.6s ease-in-out infinite; }
  @keyframes jg-orbit { from { transform: rotate(0); } to { transform: rotate(360deg); } }
  @keyframes jg-pulse { 0%,100% { transform: scale(1); } 50% { transform: scale(1.035); } }
]]></style>`;

function satellitesAnim() {
  return positions.map((p, i) => {
    const color = P.icons[i];
    const inner = SAT[SAT_ORDER[i]];
    return `
    <g transform="translate(${p.x} ${p.y})">
      <circle r="22" fill="#ffffff" fill-opacity="0.06"/>
      <g stroke="${color}" fill="none" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" color="${color}">
        ${inner}
      </g>
    </g>`;
  }).join("\n");
}

const ANIMATED_SVG = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <title>JUGAAD App Icon - Animated</title>
  ${ANIMATED_STYLE}
  ${DEFS}
  <g clip-path="url(#squircle)">
    <rect width="512" height="512" fill="url(#bg)"/>
    <rect width="512" height="512" fill="url(#glow)"/>
    <rect width="512" height="512" fill="url(#gloss)"/>
    ${NOISE}
    <circle class="ring" cx="${CX}" cy="${CY}" r="${RING}" fill="none" stroke="${P.ring}" stroke-opacity="0.55" stroke-width="2.5" stroke-dasharray="5 9"/>
    <g class="orbit">
      ${satellitesAnim()}
    </g>
    <g class="letter" filter="url(#jshadow)">
      <path d="${J_PATH}" fill="${P.letter}"/>
    </g>
  </g>
</svg>
`;

// ---------- 3. Foreground layer (432x432, transparent) ----------
// Adaptive icons: foreground sits on top, scaled & cropped by launcher mask.
// We render at viewBox 0 0 512 512 (no bg) and let the file size be 432.
const FG_SVG = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="432" height="432">
  <title>JUGAAD Foreground</title>
  <defs>
    <filter id="js2" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur in="SourceAlpha" stdDeviation="6"/>
      <feOffset dx="0" dy="6"/>
      <feComponentTransfer><feFuncA type="linear" slope="0.35"/></feComponentTransfer>
      <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
  </defs>
  <circle cx="${CX}" cy="${CY}" r="${RING}" fill="none" stroke="${P.ring}" stroke-opacity="0.55" stroke-width="2.5" stroke-dasharray="5 9"/>
  ${satellitesSVG(false)}
  <g filter="url(#js2)">
    <path d="${J_PATH}" fill="${P.letter}"/>
  </g>
</svg>
`;

// ---------- 4. Background layer (432x432) ----------
const BG_SVG = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="432" height="432">
  <title>JUGAAD Background</title>
  <defs>
    <linearGradient id="bg2" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%"   stop-color="${P.bgFrom}"/>
      <stop offset="55%"  stop-color="${P.bgVia}"/>
      <stop offset="100%" stop-color="${P.bgTo}"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" fill="url(#bg2)"/>
</svg>
`;

fs.writeFileSync(path.join(OUT, "jugaad-static-512.svg"), STATIC_SVG);
fs.writeFileSync(path.join(OUT, "jugaad-animated.svg"), ANIMATED_SVG);
fs.writeFileSync(path.join(OUT, "jugaad-foreground.svg"), FG_SVG);
fs.writeFileSync(path.join(OUT, "jugaad-background.svg"), BG_SVG);

console.log("Wrote 4 svg assets to", OUT);


