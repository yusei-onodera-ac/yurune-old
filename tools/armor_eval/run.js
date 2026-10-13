// Model Armor（アプリが使うテンプレート）が、日本語の文を、どう見つけるかを測る。
// 使い方（Node.js 24。gcloud auth login を済ませておく）:
//   set GOOGLE_CLOUD_PROJECT=（プロジェクト ID）
//   node tools/armor_eval/run.js
// 環境変数 ARMOR_LOCATION（既定 us-central1）、ARMOR_TEMPLATE（既定 yurune-template）、GOOGLE_ACCESS_TOKEN（なければ gcloud から取る）。
// 結果は、このフォルダの results.json に書く。トークンは、どこにも出さない。
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const PROJECT = process.env.GOOGLE_CLOUD_PROJECT;
const LOCATION = process.env.ARMOR_LOCATION || 'us-central1';
const TEMPLATE = process.env.ARMOR_TEMPLATE || 'yurune-template';
const PAUSE_MS = 150;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

if (!PROJECT) { console.error('Set GOOGLE_CLOUD_PROJECT.'); process.exit(1); }
const token = process.env.GOOGLE_ACCESS_TOKEN || execSync('gcloud auth print-access-token', { encoding: 'utf8' }).trim();
const url = `https://modelarmor.${LOCATION}.rep.googleapis.com/v1/projects/${PROJECT}/locations/${LOCATION}/templates/${TEMPLATE}:sanitizeUserPrompt`;

// 結果の中から、見つけた（MATCH_FOUND）検査を、上の階層の名前ごとに集める
function collectMatches(filterResults) {
  const found = [];
  for (const [name, value] of Object.entries(filterResults || {})) {
    const details = [];
    (function walk(node, trail) {
      if (!node || typeof node !== 'object') return;
      if (node.matchState === 'MATCH_FOUND') details.push(trail.concat(node.confidenceLevel ? [node.confidenceLevel] : []).join('/'));
      for (const [key, child] of Object.entries(node)) walk(child, trail.concat([key]));
    })(value, []);
    if (details.length > 0) found.push({ filter: name, details });
  }
  return found;
}

async function sanitize(text) {
  const startedAt = Date.now();
  const response = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'x-goog-user-project': PROJECT },
    body: JSON.stringify({ userPromptData: { text } }),
  });
  const latencyMs = Date.now() - startedAt;
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${(await response.text()).slice(0, 200)}`);
  const result = (await response.json()).sanitizationResult || {};
  return { latencyMs, invocationResult: result.invocationResult, matchState: result.filterMatchState, matches: collectMatches(result.filterResults) };
}

(async () => {
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'cases.json'), 'utf8')).cases;
  const rows = [];
  for (const item of cases) {
    const outcome = await sanitize(item.text);
    rows.push({ category: item.category, expect: item.expect, filter: item.filter || null, text: item.text, ...outcome });
    await sleep(PAUSE_MS);
  }

  const byCategory = new Map();
  for (const row of rows) {
    if (!byCategory.has(row.category)) byCategory.set(row.category, []);
    byCategory.get(row.category).push(row);
  }
  const summary = [];
  for (const [category, items] of byCategory) {
    const flagged = items.filter((row) => row.matchState === 'MATCH_FOUND');
    const byExpected = items.filter((row) => row.filter && row.matches.some((match) => match.filter === row.filter));
    summary.push({ category, expect: items[0].expect, total: items.length, flagged: flagged.length, flaggedByExpectedFilter: byExpected.length });
  }
  const latencies = rows.map((row) => row.latencyMs).sort((a, b) => a - b);
  const stats = { calls: rows.length, latencyMedianMs: latencies[Math.floor(latencies.length / 2)], latencyMaxMs: latencies[latencies.length - 1] };

  fs.writeFileSync(path.join(__dirname, 'results.json'),
    JSON.stringify({ measuredAt: new Date().toISOString(), location: LOCATION, template: TEMPLATE, stats, summary, rows }, null, 2) + '\n');

  console.log(`template=${TEMPLATE} location=${LOCATION} calls=${stats.calls} latency median=${stats.latencyMedianMs}ms max=${stats.latencyMaxMs}ms\n`);
  for (const line of summary) console.log(`${line.category.padEnd(20)} expect=${line.expect.padEnd(5)} flagged ${line.flagged}/${line.total}  (by the expected filter: ${line.flaggedByExpectedFilter})`);
  console.log('\n-- not flagged (expected: match) --');
  rows.filter((row) => row.expect === 'match' && row.matchState !== 'MATCH_FOUND').forEach((row) => console.log(`[${row.category}] ${row.text}`));
  console.log('\n-- flagged (expected: pass) --');
  rows.filter((row) => row.expect === 'pass' && row.matchState === 'MATCH_FOUND').forEach((row) => console.log(`[${row.category}] ${row.text}  -> ${JSON.stringify(row.matches)}`));
  console.log('\n-- info --');
  rows.filter((row) => row.expect === 'info').forEach((row) => console.log(`[${row.category}] ${row.matchState || 'n/a'} ${JSON.stringify(row.matches)}  ${row.text}`));
})().catch((error) => { console.error('FAILED', error.message); process.exit(1); });
