// Prints the visible text of the built page, one line per element, and flags words that must not appear.
const fs = require('fs');
const FORBIDDEN_WORDS = ['治療', '不眠症', '改善', '効く', '睡眠の質', '診断し', '処方', '病気が治'];
const NEGATED_ALLOWED = ['病気の診断は、しません', '効果ではありません'];
const html = fs.readFileSync('dist/index.html', 'utf8');
const body = html.slice(html.indexOf('<body>'));
const withoutNoise = body.replace(/<style[\s\S]*?<\/style>/g, '').replace(/<script[\s\S]*?<\/script>/g, '').replace(/<svg[\s\S]*?<\/svg>/g, '');
const lines = withoutNoise.replace(/<(p|h1|h2|h3|li|dt|dd|summary|figcaption|a|span|div)[^>]*>/g, '\n').replace(/<[^>]+>/g, '').split('\n').map((line) => line.trim()).filter(Boolean);
lines.forEach((line) => console.log(line));
const flagged = lines.filter((line) => FORBIDDEN_WORDS.some((word) => line.includes(word)));
const alt = (html.match(/alt="[^"]*"/g) || []);
console.log('\n--- alt texts');
alt.forEach((text) => console.log(text));
console.log('\n--- flagged lines:', flagged.length);
flagged.forEach((line) => console.log(line));
