# Contributing to SD Prompt Studio

Thanks for wanting to help! This document explains how to get set up, what the
code looks like, and how to get a change merged.

---

## Table of Contents

- [Ways to contribute](#ways-to-contribute)
- [Development setup](#development-setup)
- [Project layout](#project-layout)
- [Coding guidelines](#coding-guidelines)
- [Testing your changes](#testing-your-changes)
- [Submitting a pull request](#submitting-a-pull-request)
- [Adding tags or scenes](#adding-tags-or-scenes)
- [Translations](#translations)
- [Commit convention](#commit-convention)

---

## Ways to contribute

You don't have to write code to help:

| Contribution | How |
|---|---|
| 🐛 **Report bugs** | Open a [bug report](.github/ISSUE_TEMPLATE/bug_report.yml) with reproduction steps |
| 💡 **Suggest features** | Open a [feature request](.github/ISSUE_TEMPLATE/feature_request.yml) |
| 📖 **Improve docs** | Fix typos, clarify the README, add screenshots |
| 🌍 **Translations** | Extend the `CN` dictionary in `src/index.html` |
| 🏷️ **Tag library** | Add missing danbooru tags, improve the scene templates |
| 🧪 **Test** | Try the APK on your device and report what breaks |
| ⭐ **Star the repo** | Genuinely helps visibility |

---

## Development setup

The project intentionally has **no build system to install**. The whole frontend
is one HTML file you can open in a browser.

### Frontend only (fastest loop)

```bash
# just open it — no server, no npm, no bundler
xdg-open src/index.html      # Linux
open src/index.html          # macOS
start src/index.html         # Windows
```

The UI works in a desktop browser; only the native-backed features
(rendering, gallery, AI Chat, completion sound) are no-ops there. You'll see a
toast saying "APK only" if you tap them.

**Recommended**: open DevTools and use device emulation at **400 × 860** to match
a typical phone viewport.

### Full APK build

See [Building from Source](README.md#-building-from-source) in the README. Short version:

```bash
# Linux / macOS / CI
export ANDROID_SDK_ROOT=/path/to/android-sdk
bash android/build.sh

# Windows
.\android\build.ps1
```

Requirements: Android SDK Build-Tools 34.0.0, Platform android-33, JDK 17.

---

## Project layout

```
src/index.html            All frontend logic. Single file — see the module table in the README.
android/
  build.sh                POSIX build (CI + Linux/macOS)
  build.ps1               PowerShell build (Windows)
  gen_icon.py             Procedural icon generator
  src/.../MainActivity.java   WebView host + every native capability
.github/workflows/        CI: build + release
docs/screenshots/         README images
```

### Frontend architecture in one paragraph

`src/index.html` holds several large data tables (`P` tag pools, `CN` dictionary,
`SCENARIOS` scene templates, `EXCLUSIVE` exclusion groups, `CHARACTERS`,
`AI_PROVIDERS`) followed by pure functions that operate on them. State lives in a
handful of module-level `let` variables. There is **no framework and no virtual
DOM** — rendering functions rebuild small subtrees directly. Keep it that way;
adding a dependency would break the "single file, open and run" property.

### Native architecture in one paragraph

`MainActivity` hosts a `WebView` and exposes a `Bridge` inner class annotated with
`@JavascriptInterface`. Every method that touches the network, the filesystem or
system services lives there and pushes results back by calling
`webView.evaluateJavascript(...)` on the UI thread. **All strings passed back must
be escaped with `JSONObject.quote()`** — raw interpolation will break on quotes and
newlines.

---

## Coding guidelines

### Frontend

- **ES5-compatible syntax.** The app must run on Android 5.0's WebView
  (Chrome 37). No arrow functions, no `let`/`const` inside hot paths if you can
  avoid it, no template literals, no optional chaining. The existing code uses
  `var`-era style `let`/`const` and `function () {}` — match it.
- **No external dependencies.** No CDN links, no fonts, no libraries.
- **Keep it in one file.** See above.
- Escape user-controlled text before injecting into `innerHTML`. Prefer
  `textContent` unless you actually need markup.

### Native (Java)

- Target **`--release 8`**, `minSdk 21`.
- Never block the UI thread. Network and disk I/O go on a background thread.
- Escape every string handed to JavaScript with `JSONObject.quote()`.
- Use `Proxy.NO_PROXY` for localhost requests — see the comment in `openPost()`.
- Test the deprecated-API warnings; the CI build treats stderr as non-fatal, but
  prefer the modern overload.

### Data tables

When adding to `P`, `SCENARIOS` or `EXCLUSIVE`:

- **Never** put two tags from the same `EXCLUSIVE` group into one scene.
- Every new tag **must** get an entry in `CN` if you want Chinese output to work.
- Run the coherence check before opening a PR (see below).

---

## Testing your changes

There is no test framework. Changes are verified with small Node scripts that
load `src/index.html`, stub the DOM, and assert on generated output. This is the
fastest way to catch tag-logic regressions.

### Minimal harness

```js
const fs = require('fs');
const html = fs.readFileSync('src/index.html', 'utf8');
const js = html.match(/<script>([\s\S]*)<\/script>/)[1];

// 1. syntax check
new Function(js);

// 2. stub just enough DOM for the module to load
const store = {};
const mk = (id) => ({
  id, value: '', textContent: '', innerHTML: '', checked: false, className: '',
  style: { setProperty(){}, display: '' }, selectedIndex: 0, options: [],
  addEventListener(){}, appendChild(){}, removeChild(){}, select(){}, focus(){},
  getAttribute(){ return null; }, setAttribute(){}, querySelector(){ return null; },
  classList: { add(){}, remove(){}, contains(){ return false; } }
});
globalThis.document = {
  documentElement: { style: { setProperty(){} } },
  getElementById: (id) => (store[id] = store[id] || mk(id)),
  createElement: () => mk('tmp'),
  querySelectorAll: () => [],
  body: { appendChild(){}, removeChild(){} },
  execCommand(){}
};
globalThis.window = globalThis;
globalThis.localStorage = { getItem: () => null, setItem(){}, removeItem(){} };

// 3. expose internals for assertions
eval(js.replace('  // initial\n  loadStore();',
  '  globalThis.__T = { generateTags, makeVariation, SCENARIOS, EXCLUSIVE, CN };\n' +
  '  // initial\n  loadStore();'));
```

### What to assert when touching tag logic

| Change | Assert |
|---|---|
| New scene | Every `core` tag appears in 30+ generated prompts |
| New scene | At most one tag from the pose exclusion group |
| New scene | At most one tag from the activity exclusion group |
| New tag | `CN[tag]` is defined |
| Variation engine | No `EXCLUSIVE`-protected / core tag is ever dropped |
| Variation engine | Preserved-tag ratio stays above ~80% |

Run the same scenario **30 times** per case — the generator is stochastic, and a
single run proves nothing.

### Manual test on device

```bash
adb install -r PromptGenerator.apk
adb logcat -c && adb logcat | grep -i promptgen
```

---

## Submitting a pull request

1. **Fork** the repo and create a branch:
   ```bash
   git checkout -b fix/upscaler-path-probe
   ```
2. **Make the change.** Keep it focused — one logical change per PR.
3. **Verify the build passes:**
   ```bash
   bash android/build.sh
   ```
4. **Update docs** if behaviour changed (README, FAQ, screenshots).
5. **Open the PR** and fill in the template.

### What reviewers look for

- Does it work on the stated `minSdk` (21)?
- Does it keep the single-file frontend property?
- Are new tags present in `CN`?
- Does it avoid introducing contradictory tag combinations?
- Is the change actually verified (not just "should work")?

### What is likely to be rejected

- Adding a build system, framework, or npm dependency
- Raising `minSdk` without a very good reason
- Bundling or hardcoding third-party API keys (**never** acceptable)
- Large unrelated refactors mixed into a feature PR
- Changes that remove the offline-first property

---

## Adding tags or scenes

This is the highest-value, lowest-barrier way to contribute.

### Adding a scene

Find the `SCENARIOS` array in `src/index.html` and add an entry:

```js
{ id: "your_scene", name: "Display Name", min: "explicit", people: 1,
  core: ["primary_action", "supporting_tag"],   // always emitted
  body: ["a", "b", "c", "d"],                   // pick 2
  pose: ["a", "b", "c", "d"],                   // pick 2
  face: ["a", "b", "c", "d"],                   // pick 2
  fluid: ["a", "b"],                            // pick 1
  loc: ["a", "b"],                              // pick 1
  cam: ["a", "b"],                              // pick 1
  light: ["a", "b"] },                          // pick 1
```

Rules:
- `min` is the **content-rating tier** the scene belongs to. The tiers are
  `mild` / `soft` / `medium` / `explicit` / `extreme` — they work like a
  content rating (comparable to "Explicit" labels on music), letting users
  filter what is appropriate for their platform and jurisdiction.
- `people` drives the subject tag (1 → `1girl`, 2 → `2girls`, 3+ → `multiple girls`)
- **Never** place two tags from the same `EXCLUSIVE` group in one scene
- Prefer tags that already exist in `P` or in another scene (they're already in `CN`)
- If you introduce a brand-new tag, add its `CN` entry in the same PR

### Adding tags to a pool

Append to the relevant array in `P`. If a tag is mutually exclusive with others,
add it to the matching group in `EXCLUSIVE` too.

### Adding Chinese mappings

The `CN` object is a flat `"english": "中文"` map. Keys are **lowercase English
tags**. Add your entry near the other entries of the same category.

---

## Translations

Two things can be translated:

1. **The `CN` dictionary** — English tag → Chinese label. Adding entries is
   straightforward; see above.
2. **UI strings** — currently inline Chinese in the HTML. Extracting them into a
   proper i18n table is a welcome refactor; open an issue first so we can agree on
   the approach.

---

## Commit convention

[Conventional Commits](https://www.conventionalcommits.org/) — the history is
readable and it makes release notes trivial to generate.

```
feat:     a new feature
fix:      a bug fix
docs:     documentation only
refactor: no behaviour change
perf:     performance improvement
build:    build script / CI changes
chore:    maintenance
i18n:     translation additions
```

Examples:

```
feat(scenes): add 6 new pose scenarios
fix(upscaler): probe candidate paths before failing
docs(readme): clarify the LocalDream upscaler prerequisite
perf(gallery): move thumbnail generation off the UI thread
i18n(cn): add 40 missing Chinese mappings
```

Scope is optional but helpful: `scenes`, `variation`, `chat`, `ld` (LocalDream),
`gallery`, `sound`, `theme`, `ci`.

---

## Code of Conduct

Be respectful. This is a hobby project maintained by volunteers.

Discussion of the app's subject matter should stay **technical**. Do not post
explicit imagery, generated or otherwise, in issues, PRs or discussions.

---

<div align="center">

Thanks for contributing! 🎉

</div>
