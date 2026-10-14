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

// Where the page links to. Change a destination here, not in the template.
// The feedback destination is a stand-in: GitHub Issues is public and needs a GitHub account. Swap it for a form when one exists,
// and change the note under the feedback button in the template to match.
const APP_URL = 'https://yurune-4ffhhe3pra-an.a.run.app';
const LINKS = {
  app_url: APP_URL,
  github_url: 'https://github.com/yusei-onodera-ac/yurune',
  privacy_url: `${APP_URL}/privacy`,
  terms_url: `${APP_URL}/terms`,
  feedback_url: 'https://github.com/yusei-onodera-ac/yurune/issues/new',
};

function readTemplate() {
  return fs.readFileSync(path.join(SOURCE_DIR, 'template.html'), 'utf8');
}

function buildDot(kind, startsGroup, order) {
  const classes = ['night-dot'];
  if (kind === 'changed') { classes.push('night-dot--changed'); }
  if (startsGroup) { classes.push('night-dot--gap'); }
  return `<li class="${classes.join(' ')}" style="--i:${order}"></li>`;
}

function buildNightDots() {
  const dots = [];
  for (let index = 0; index < BASELINE_NIGHT_COUNT; index++) { dots.push(buildDot('standard', false, dots.length)); }
  for (let group = 0; group < PATTERN_GROUP_COUNT; group++) {
    NIGHT_PATTERN.forEach((kind, position) => dots.push(buildDot(kind, group === 0 && position === 0, dots.length)));
  }
  return dots.join('');
}

function readPotParts() {
  return fs.readFileSync(path.join(SOURCE_DIR, 'pot-parts.html'), 'utf8').trim();
}

function replacePot(template) {
  return template.replace(/\{\{pot_parts\}\}/g, readPotParts());
}

// The second page (about the maker) shares the style, the header and the footer with the main page, so they are cut out of it.
// In the header and the footer, in-page links point back to the main page.
function buildAboutPage(mainTemplate) {
  const pick = (pattern) => mainTemplate.match(pattern)[0];
  const toMainPage = (fragment) => fragment.replace(/href="#/g, 'href="index.html#');
  const aboutMain = replaceLinks(replacePot(fs.readFileSync(path.join(SOURCE_DIR, 'about.template.html'), 'utf8')));
  const title = aboutMain.match(/<title>.*?<\/title>/)[0];
  const main = aboutMain.replace(/<title>.*?<\/title>\n?/, '');
  return [
    title,
    pick(/<link rel="stylesheet" href="https:\/\/fonts[^>]*>/),
    pick(/<style>[\s\S]*?<\/style>/),
    '<div class="landing" lang="ja">',
    toMainPage(pick(/<header class="site-header">[\s\S]*?<\/header>/)),
    main,
    toMainPage(pick(/<footer class="site-footer">[\s\S]*?<\/footer>/)),
    '</div>',
  ].join('\n');
}

function replaceLinks(template) {
  return template.replace(/\{\{(app_url|github_url|privacy_url|terms_url|feedback_url)\}\}/g, (match, key) => LINKS[key]);
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
  return `<!doctype html>\n<html lang="ja">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n<link rel="icon" type="image/png" sizes="192x192" href="images/favicon.png">\n${titleMatch[0]}\n</head>\n<body>\n${body}\n</body>\n</html>\n`;
}

// The folder is emptied first, so an image that was removed from shots/ does not stay in the hosted files.
function copyImages() {
  fs.rmSync(IMAGES_DIR, { recursive: true, force: true });
  fs.mkdirSync(IMAGES_DIR, { recursive: true });
  fs.readdirSync(SHOTS_DIR).filter((file) => file.endsWith('.webp') || file.endsWith('.png'))
    .forEach((file) => fs.copyFileSync(path.join(SHOTS_DIR, file), path.join(IMAGES_DIR, file)));
}

function main() {
  const withDots = replaceLinks(replacePot(readTemplate().replace('{{night_dots}}', buildNightDots())));
  fs.writeFileSync(path.join(SOURCE_DIR, 'artifact.html'), replaceImages(withDots, toDataUri));
  copyImages();
  const hosted = replaceImages(withDots, (name) => `images/${name}.webp`);
  fs.mkdirSync(DIST_DIR, { recursive: true });
  fs.writeFileSync(path.join(DIST_DIR, 'index.html'), wrapAsDocument(hosted));
  fs.writeFileSync(path.join(DIST_DIR, 'about.html'), wrapAsDocument(buildAboutPage(withDots)));
  console.log('built: artifact.html, dist/index.html and dist/about.html');
}

main();
