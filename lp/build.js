// Builds the landing page in two forms from one template:
//  dist/index.html + dist/images/*  : files for hosting
//  artifact.html                    : one self-contained page (images inlined) for the preview
const fs = require('fs');
const path = require('path');

const SOURCE_DIR = __dirname;
const SHOTS_DIR = path.join(SOURCE_DIR, 'shots');
const DIST_DIR = path.join(SOURCE_DIR, 'dist');
const IMAGES_DIR = path.join(DIST_DIR, 'images');
const IMAGE_TYPE = 'image/webp';
const BASELINE_NIGHT_COUNT = 7;
const PATTERN_GROUP_COUNT = 5;
const NIGHT_PATTERN = ['changed', 'changed', 'standard'];

function readTemplate() {
  return fs.readFileSync(path.join(SOURCE_DIR, 'template.html'), 'utf8');
}

function buildDot(kind, startsGroup) {
  const classes = ['night-dot'];
  if (kind === 'changed') { classes.push('night-dot--changed'); }
  if (startsGroup) { classes.push('night-dot--gap'); }
  return `<li class="${classes.join(' ')}"></li>`;
}

function buildNightDots() {
  const dots = [];
  for (let index = 0; index < BASELINE_NIGHT_COUNT; index++) { dots.push(buildDot('standard', false)); }
  for (let group = 0; group < PATTERN_GROUP_COUNT; group++) {
    NIGHT_PATTERN.forEach((kind, position) => dots.push(buildDot(kind, group === 0 && position === 0)));
  }
  return dots.join('');
}

function replaceImages(template, resolveSource) {
  return template.replace(/\{\{image:([a-z-]+)\}\}/g, (match, name) => resolveSource(name));
}

function toDataUri(name) {
  const bytes = fs.readFileSync(path.join(SHOTS_DIR, `${name}.webp`));
  return `data:${IMAGE_TYPE};base64,${bytes.toString('base64')}`;
}

function wrapAsDocument(fragment) {
  const titleMatch = fragment.match(/<title>.*?<\/title>/);
  const body = fragment.replace(/<title>.*?<\/title>\n?/, '');
  return `<!doctype html>\n<html lang="ja">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n${titleMatch[0]}\n</head>\n<body>\n${body}\n</body>\n</html>\n`;
}

// The folder is emptied first, so an image that was removed from shots/ does not stay in the hosted files.
function copyImages() {
  fs.rmSync(IMAGES_DIR, { recursive: true, force: true });
  fs.mkdirSync(IMAGES_DIR, { recursive: true });
  fs.readdirSync(SHOTS_DIR).filter((file) => file.endsWith('.webp'))
    .forEach((file) => fs.copyFileSync(path.join(SHOTS_DIR, file), path.join(IMAGES_DIR, file)));
}

function main() {
  const withDots = readTemplate().replace('{{night_dots}}', buildNightDots());
  fs.writeFileSync(path.join(SOURCE_DIR, 'artifact.html'), replaceImages(withDots, toDataUri));
  copyImages();
  const hosted = replaceImages(withDots, (name) => `images/${name}.webp`);
  fs.mkdirSync(DIST_DIR, { recursive: true });
  fs.writeFileSync(path.join(DIST_DIR, 'index.html'), wrapAsDocument(hosted));
  console.log('built: artifact.html and dist/index.html');
}

main();
