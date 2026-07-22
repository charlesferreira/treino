// Gera os ícones do PWA a partir de um SVG de halter. Rodar: npm run icons
import sharp from 'sharp'
import { mkdirSync } from 'node:fs'

const barbell = (pad) => `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="${pad ? 0 : 96}" fill="#0e1013"/>
  <g transform="translate(256 256) scale(${pad ? 0.72 : 1}) translate(-256 -256)">
    <rect x="76" y="240" width="360" height="32" rx="16" fill="#9aa4b2"/>
    <rect x="112" y="176" width="40" height="160" rx="12" fill="#4ade80"/>
    <rect x="164" y="196" width="32" height="120" rx="10" fill="#4ade80"/>
    <rect x="360" y="176" width="40" height="160" rx="12" fill="#4ade80"/>
    <rect x="316" y="196" width="32" height="120" rx="10" fill="#4ade80"/>
  </g>
</svg>`

mkdirSync('public', { recursive: true })

const jobs = [
  ['public/pwa-192.png', 192, false],
  ['public/pwa-512.png', 512, false],
  ['public/pwa-maskable-512.png', 512, true],
  ['public/apple-touch-icon.png', 180, true],
]

for (const [file, size, padded] of jobs) {
  await sharp(Buffer.from(barbell(padded))).resize(size, size).png().toFile(file)
  console.log(`✓ ${file}`)
}
