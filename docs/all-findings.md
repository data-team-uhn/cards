# CARDS Frontend -- Combined Analysis Findings

**Codebase:** `aggregated-frontend/src/main/frontend/src/`
**Date:** 2026-02-05
**Total Findings:** 87 (5 Critical, 29 High, 41 Medium, 12 Low)

All file paths are relative to `aggregated-frontend/src/main/frontend/src/`.

---

## Table of Contents

- [Critical Findings](#critical-findings)
- [High Findings](#high-findings)
- [Medium Findings](#medium-findings)
- [Low Findings](#low-findings)

---

## Critical Findings

### C-01: Auth Token Variable Typo Breaks Patient Authentication

| Field | Value |
|-------|-------|
| **Type** | Bug -- Typo / Wrong Variable |
| **File** | `patient-portal/PatientIdentification.jsx:203` |
| **Short Description** | `setAuthToken(authToken)` references the state variable (undefined) instead of the local `auth_token` parsed from the URL. |

**Detailed Description:**
On line 202, the local variable `auth_token` is extracted from the URL search parameters. On line 203, `setAuthToken(authToken)` is called, but `authToken` is the React state variable (declared via `useState()` on line 108, initially `undefined`), not the just-parsed `auth_token`. As a result, the auth token from the URL is never stored in state. Downstream on line 173, `authToken && requestData.append("auth_token", authToken)` always evaluates to false, so the auth token is never sent in subsequent `identify()` requests. This completely breaks the token-based patient authentication flow in the patient portal.

**Suggestion:**
Change `setAuthToken(authToken)` to `setAuthToken(auth_token)` on line 203.

---

### C-02: Bitwise OR Corrupts Medical Answer Data

| Field | Value |
|-------|-------|
| **Type** | Bug -- Operator Error |
| **File** | `questionnaire/SelectableAreaQuestion.jsx:396` |
| **Short Description** | Bitwise OR `\|` used instead of logical OR `\|\|`, coercing string labels to `0` or `NaN`. |

**Detailed Description:**
The expression `notApplicableOption.label | notApplicableOption.value` uses the bitwise OR operator `|`. When applied to strings (which label and value are), JavaScript coerces both operands to integers via `Number()`, producing `0 | 0 = 0` or `NaN`. The intended behavior is a fallback: use `label` if truthy, otherwise `value`. Instead, the stored answer value for "not applicable" selections on medical questionnaires is always `0`, corrupting patient data.

**Suggestion:**
Change `|` to `||`: `notApplicableOption.label || notApplicableOption.value`.

---

### C-03: Remote Code Execution via `new Function()` in Extension System

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS / RCE |
| **File** | `uiextension/ExtensionPoint.jsx:87` |
| **Short Description** | JavaScript fetched from a remote endpoint is executed directly via `new Function(text)()`. |

**Detailed Description:**
The `ExtensionPoint` component fetches content from UIXP endpoints and, when the content type is JavaScript, executes it via `new Function(text)()`. This is functionally equivalent to `eval()`. If the endpoint is compromised, returns unexpected content, or a man-in-the-middle attack intercepts the request, arbitrary JavaScript runs in the user's authenticated browser session with full access to the DOM, cookies, and patient data. No Content Security Policy, subresource integrity hashes, or origin validation is applied.

**Suggestion:**
Use subresource integrity (SRI) hashes for loaded scripts. Load extensions only from a pre-approved allowlist. Consider moving to a sandboxed iframe approach for untrusted extensions. Long-term, implement a CSP header that restricts `unsafe-eval`.

---

### C-04: Remote Code Execution via `new Function()` in Asset Manager

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS / RCE |
| **File** | `assetManager.jsx:156` |
| **Short Description** | Remote component source code fetched via HTTP is executed as a JavaScript function body. |

**Detailed Description:**
The `assetManager` fetches remote component source code as text and evaluates it via `new Function("module", "exports", remoteComponentSrc + "\nreturn module.exports || exports;")(module, module.exports)`. Compromise of the asset server or a MITM attack grants full JavaScript execution in any client session. On a medical platform, this means potential exfiltration of all patient data visible to the current user.

**Suggestion:**
Implement subresource integrity (SRI) verification for fetched assets. Ensure assets are served from same-origin with proper CSP headers. Consider Webpack dynamic imports (`import()`) for code splitting instead of runtime evaluation.

---

### C-05: Remote Code Execution via `new Function()` in Computed Questions

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS / RCE |
| **File** | `questionnaire/ComputedQuestion.jsx:250` |
| **Short Description** | Server-defined questionnaire expressions are executed as JavaScript with access to patient answer data. |

**Detailed Description:**
The `ComputedQuestion` component takes an `expression` property from the questionnaire definition and executes it via `new Function(expressionArguments, parsedExpression)(...expressionValues)`. The expression has access to `form` context containing all current answer values, `setError` function, and other form data. If an attacker gains admin access to modify questionnaire definitions, or the backend is compromised, arbitrary JavaScript executes in patients' browsers with access to their medical data.

**Suggestion:**
Replace `new Function()` with a safe expression evaluator library such as `mathjs` or `expr-eval` that only allows known mathematical and logical operations. Implement an allowlist of permitted functions.

---

## High Findings

### H-01: `new Function()` RCE in Prototype Shim

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS / RCE |
| **File** | `pedigree/shims/prototypeShim.js:1034-1036` |
| **Short Description** | Ajax response text is executed as code via `new Function()` in the `evalResponse` method. |

**Detailed Description:**
The Prototype.js compatibility shim includes an `evalResponse` method that executes HTTP response text directly: `new Function('return (' + responseText + ')')()` with a fallback to `new Function(responseText)()`. Any compromised server response or MITM attack results in arbitrary code execution in the browser. This is part of the legacy pedigree module.

**Suggestion:**
Replace with `JSON.parse()` for JSON responses. For non-JSON responses, parse the data format explicitly rather than executing it as code.

---

### H-02: Stored XSS via Unsanitized `dangerouslySetInnerHTML` in ExtensionPoint

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `uiextension/ExtensionPoint.jsx:101` |
| **Short Description** | HTML fetched from remote extension endpoints is rendered without sanitization. |

**Detailed Description:**
When the fetched extension content type is HTML, it is rendered via `<div dangerouslySetInnerHTML={{ __html: text }}/>` without any sanitization. An attacker who controls extension content can inject `<script>` tags, event handlers (`onerror`, `onload`), or other XSS payloads that execute in the context of the authenticated user's session.

**Suggestion:**
Add `dompurify` to the project dependencies and sanitize the HTML before rendering: `DOMPurify.sanitize(text)`.

---

### H-03: No HTML Sanitization Library in Project Dependencies

| Field | Value |
|-------|-------|
| **Type** | Security -- Missing Control |
| **File** | `package.json` |
| **Short Description** | The project has zero sanitization libraries despite 3x `dangerouslySetInnerHTML`, 7x `innerHTML`, 3x `insertAdjacentHTML`. |

**Detailed Description:**
A search of `package.json` and `node_modules` reveals no sanitization library (DOMPurify, sanitize-html, xss, etc.) is installed. The codebase has at least 13 points where raw HTML is injected into the DOM. Every single one of these is a potential XSS vector with no defense. For a medical platform handling PHI/PII, this is a significant gap.

**Suggestion:**
Add `dompurify` to dependencies. Create a shared utility function `sanitizeHTML(html)` that wraps `DOMPurify.sanitize()` and use it at all HTML injection points.

---

### H-04: Open Redirect in LoginForm via `resource` Parameter

| Field | Value |
|-------|-------|
| **Type** | Security -- Open Redirect |
| **File** | `login/LoginForm.js:59` |
| **Short Description** | After login, the user is redirected to the unvalidated `resource` query parameter. |

**Detailed Description:**
The `loginRedirectPath()` function reads `new URLSearchParams(window.location.search).get("resource")` and assigns it to `window.location` after successful authentication. There is no validation that the URL is same-origin or even relative. An attacker can craft `/login?resource=https://evil.com/phish` to redirect authenticated users to a phishing page that mimics the application.

**Suggestion:**
Validate that the `resource` parameter starts with `/` and does not contain `//` or `://`. Reject absolute URLs and protocol-relative URLs.

---

### H-05: Open Redirect in RegistrationForm via `resource` Parameter

| Field | Value |
|-------|-------|
| **Type** | Security -- Open Redirect |
| **File** | `login/RegistrationForm.js:186` |
| **Short Description** | After registration, the user is redirected to the unvalidated `resource` query parameter. |

**Detailed Description:**
Same pattern as H-04. After registration and auto-login, `window.location` is set to `new URLSearchParams(window.location.search).get('resource') || '/'`.

**Suggestion:**
Apply the same validation as H-04: reject absolute URLs, require the path to start with `/`.

---

### H-06: JCR-SQL2 Injection in SubjectFilter (User Search Input)

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `dataHomepage/FilterComponents/SubjectFilter.jsx:65` |
| **Short Description** | User search input is interpolated directly into a JCR-SQL2 LIKE clause without escaping. |

**Detailed Description:**
The `formattedQuery` variable, derived from the user's search text input, is interpolated directly into: `` WHERE lower(s.'fullIdentifier') LIKE '%25${formattedQuery}%25' ``. The existing `escapeJQL()` function in `escape.jsx` is not imported or used. An attacker can craft search input that breaks out of the LIKE clause and injects additional JCR-SQL2 clauses to extract unauthorized data.

**Suggestion:**
Import `escapeJQL` from `escape.jsx` and apply it: `escapeJQL(formattedQuery)`.

---

### H-07: JCR-SQL2 Injection in NewFormDialog (Questionnaire Name)

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `dataHomepage/NewFormDialog.jsx:146` |
| **Short Description** | Questionnaire name interpolated into JCR-SQL2 query without escaping. |

**Detailed Description:**
The query `` `select * from [cards:Questionnaire] as n where name()='${questionnaireName}'` `` interpolates `questionnaireName` directly. If the name contains single quotes, the query structure breaks and allows injection.

**Suggestion:**
Use `escapeJQL(questionnaireName)`.

---

### H-08: JCR-SQL2 Injection in NewFormDialog (Subject UUID)

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `dataHomepage/NewFormDialog.jsx:220` |
| **Short Description** | Subject UUID interpolated into JCR-SQL2 query without escaping. |

**Detailed Description:**
A UUID value is interpolated into `` where f.'subject'='${...['jcr:uuid']}'`` without escaping.

**Suggestion:**
Use `escapeJQL()` on the UUID value.

---

### H-09: Auth Token Exposed in URL Query Parameters

| Field | Value |
|-------|-------|
| **Type** | Security -- Insecure Data Handling |
| **File** | `patient-portal/PatientIdentification.jsx:202` |
| **Short Description** | Patient authentication tokens are passed via URL query parameters, leaking through browser history, referer headers, and logs. |

**Detailed Description:**
The auth token is read from `window.location.search.get("auth_token")`. URLs containing tokens appear in browser history, server access logs, HTTP Referer headers sent to external resources, and can be captured by browser extensions or third-party analytics scripts. For a medical platform, token leakage could grant unauthorized access to patient surveys and data.

**Suggestion:**
Exchange the URL token for a session cookie immediately upon reading it and clear the token from the URL using `history.replaceState(null, '', window.location.pathname)`. Alternatively, use POST requests with tokens in the body.

---

### H-10: PHI Logged to Console on Pedigree Save

| Field | Value |
|-------|-------|
| **Type** | Security -- Insecure Data Handling |
| **File** | `pedigree/saveLoadEngine.js:161` |
| **Short Description** | Complete pedigree JSON (family medical history, genetic data, disorders) logged to browser console on every save. |

**Detailed Description:**
`console.log('[SAVE] data: ' + JSON.stringify(jsonData))` outputs the entire pedigree data structure to the browser console. This includes patient family medical history, genetic information, disorders, and phenotypes -- all classified as PHI under HIPAA. Browser extensions, shared screens, or dev tools access expose this data.

**Suggestion:**
Remove the console.log statement or gate it behind a `DEBUG` environment variable that is disabled in production builds.

---

### H-11: PHI Logged to Console on Pedigree Load

| Field | Value |
|-------|-------|
| **Type** | Security -- Insecure Data Handling |
| **File** | `pedigree/saveLoadEngine.js:173` |
| **Short Description** | Complete patient pedigree medical data logged on every load operation. |

**Detailed Description:**
`console.log('[LOAD] recived JSON: ' + initialPedigreeJSONString)` outputs the full pedigree JSON on load. Same PHI exposure risk as H-10.

**Suggestion:**
Remove or gate behind debug flag.

---

### H-12: `.concat()` Result Discarded in MultipleChoice

| Field | Value |
|-------|-------|
| **Type** | Bug -- Logic Error |
| **File** | `questionnaire/MultipleChoice.jsx:140` |
| **Short Description** | `all_options.concat(["", ""])` creates a new array that is immediately discarded; the empty option is never added. |

**Detailed Description:**
`Array.concat()` returns a new array and does not mutate the original. The call `all_options.concat(["", ""])` creates and discards a new array. The empty option `["", ""]` is never added to `all_options`, which is then used to initialize the `options` state on line 143. Multi-select questions are missing the blank/empty option that allows users to clear their selection.

**Suggestion:**
Change to `all_options = all_options.concat(["", ""]);` or use `all_options.push("", "");`.

---

### H-13: Stale Closure in setState Callback

| Field | Value |
|-------|-------|
| **Type** | Bug -- Logic Error / Stale Closure |
| **File** | `questionnaire/SelectableAreaQuestion.jsx:201-209` |
| **Short Description** | Uses captured `selection` instead of `oldSelection` parameter inside `setSelection` callback. |

**Detailed Description:**
Inside the `setSelection(oldSelection => { ... })` callback, the code references the outer `selection` variable (captured from a previous render, potentially stale) instead of `oldSelection` (the fresh current state provided by the setter). This causes incorrect toggle behavior -- clicking a selected area may not deselect it because `selection` shows stale data.

**Suggestion:**
Replace all references to `selection` with `oldSelection` inside the callback: `oldSelection.length`, `oldSelection[0][VALUE_POS]`.

---

### H-14: window/document Event Listener Mismatch in LiveTable

| Field | Value |
|-------|-------|
| **Type** | Bug -- Event Listener Leak |
| **File** | `dataHomepage/LiveTable.jsx:190-193` |
| **Short Description** | Listener added to `window` but cleanup removes from `document`; listener is never removed. |

**Detailed Description:**
`window.addEventListener("LivetableRefresh", refresh)` is paired with `document.removeEventListener("LivetableRefresh", refresh)` in the useEffect cleanup. Since `window` and `document` are different event targets, `removeEventListener` has no effect. The listener accumulates on every mount/unmount cycle, causing memory leaks and stale refresh callbacks that could display outdated medical data.

**Suggestion:**
Change `document.removeEventListener` to `window.removeEventListener`.

---

### H-15: window/document Event Listener Mismatch in PrintButton

| Field | Value |
|-------|-------|
| **Type** | Bug -- Event Listener Leak |
| **File** | `dataHomepage/PrintButton.jsx:94-97` |
| **Short Description** | Keydown handler added to `window` but cleanup removes from `document`. |

**Detailed Description:**
Same window/document mismatch pattern as H-14. The keydown handler for the print shortcut is added to `window` but the cleanup removes it from `document`, so it is never properly cleaned up.

**Suggestion:**
Change `document.removeEventListener` to `window.removeEventListener`.

---

### H-16: window/document Event Listener Mismatch + Missing Deps in AdminResourceListing

| Field | Value |
|-------|-------|
| **Type** | Bug -- Event Listener Leak |
| **File** | `adminDashboard/AdminResourceListing.jsx:80-83` |
| **Short Description** | Window/document mismatch and no dependency array causes constant listener churn. |

**Detailed Description:**
Same window/document mismatch. Additionally, this useEffect has no dependency array, meaning it runs on every single render -- adding a new listener and failing to remove the old one on each render cycle.

**Suggestion:**
Change `document.removeEventListener` to `window.removeEventListener` and add a proper dependency array.

---

### H-17: `props.history.push` Incompatible with React Router v7

| Field | Value |
|-------|-------|
| **Type** | Bug -- API Incompatibility |
| **File** | `questionnaire/Form.jsx:777` |
| **Short Description** | Uses React Router v5 API `props.history.push` which does not exist in React Router v7. |

**Detailed Description:**
The session expiry handler calls `props.history.push("/")`. In React Router v7, components do not receive `history` as a prop. This will throw `TypeError: Cannot read properties of undefined (reading 'push')` when a user clicks to exit after session expiry, leaving the form in an inconsistent state where unsaved patient data could be lost.

**Suggestion:**
Use the `navigate` function from `useNavigate()` which is already imported and available in the component (line 140): `onExit={() => navigate("/")}`.

---

### H-18: `<Router history={}>` is React Router v5 API

| Field | Value |
|-------|-------|
| **Type** | Bug -- API Incompatibility |
| **File** | `patient-portal/index.jsx:111`, `themePage/index.jsx:179` |
| **Short Description** | Uses React Router v5 `<Router history={hist}>` which is not supported in React Router v7. |

**Detailed Description:**
React Router v7 uses `<BrowserRouter>` and does not accept a `history` prop. The `createBrowserHistory` pattern (lines 24, 105) is the React Router v5 API. The custom history object is silently ignored, so navigation events will not trigger the `beforeunload` handler. Patients can navigate away from partially filled forms without unsaved-data warnings.

**Suggestion:**
Replace with `<BrowserRouter>` from React Router v7. Use the `useBlocker` hook for navigation guards.

---

### H-19: Fire-and-Forget Questionnaire Checkout POST

| Field | Value |
|-------|-------|
| **Type** | Bug -- Missing Error Handling |
| **File** | `questionnaire/Questionnaire.jsx:113-116` |
| **Short Description** | JCR checkout POST has no `.then()` or `.catch()` -- errors are silently lost. |

**Detailed Description:**
The checkout POST `fetch(\`/Questionnaires/${id}\`, { method: "POST", body: checkoutForm })` has no error handling whatsoever. If the checkout fails (network error, auth issue, server error), the user proceeds to edit a form that was never checked out, leading to potential data loss or conflicts when saving.

**Suggestion:**
Add `.then()` and `.catch()` handlers. On failure, display an error or prevent editing.

---

### H-20: Fire-and-Forget Questionnaire Checkin POST

| Field | Value |
|-------|-------|
| **Type** | Bug -- Missing Error Handling |
| **File** | `questionnaire/Questionnaire.jsx:121-124` |
| **Short Description** | JCR checkin POST has no error handling. Failed checkin leaves questionnaire locked. |

**Detailed Description:**
Same as H-19 but for the checkin operation. If checkin fails on `beforeunload`, the questionnaire remains checked out in the repository, potentially blocking other users from editing it.

**Suggestion:**
Add error handling. Consider a retry mechanism for checkin failures.

---

### H-21: Null Dereference on Regex Match in FileQuestion

| Field | Value |
|-------|-------|
| **Type** | Bug -- Null Access |
| **File** | `questionnaire/FileQuestion.jsx:232-233` |
| **Short Description** | `response.match(uploadFinder)` may return `null`; accessing `match[1]` would throw. |

**Detailed Description:**
`String.match()` returns `null` if no match is found. If the server response format changes or returns an unexpected format, `match[1]` will throw `TypeError: Cannot read properties of null (reading '1')`. This crashes the file upload flow.

**Suggestion:**
Add a null check: `if (!match) { setError("Unexpected upload response"); return; }`.

---

### H-22: Null Dereference on Regex Match in Questionnaire

| Field | Value |
|-------|-------|
| **Type** | Bug -- Null Access |
| **File** | `questionnaire/Questionnaire.jsx:75` |
| **Short Description** | Regex `.exec()` on `location.pathname` may return `null` if URL doesn't match expected pattern. |

**Detailed Description:**
`/((.*)\/Questionnaires)\/([^.]+)/.exec(location.pathname)[1]` will throw if `location.pathname` does not contain `/Questionnaires/`. This could happen via direct navigation or a malformed URL, crashing the entire component.

**Suggestion:**
Add null checks and handle the error case with a user-friendly message.

---

### H-23: Direct State Mutation via `isHovered`

| Field | Value |
|-------|-------|
| **Type** | Bug -- State Mutation |
| **File** | `questionnaire/SelectableAreaQuestion.jsx:245, 253` |
| **Short Description** | `mapEntry.isHovered = true/false` directly mutates a state array element. |

**Detailed Description:**
`mapEntry` is an element of the `map` state array. Directly modifying `mapEntry.isHovered` mutates the state object without going through `setMap`. React will not detect this change and may not re-render, leading to stale visual state where areas don't highlight correctly on hover.

**Suggestion:**
Create a new copy of the map entry with the updated `isHovered` value and call `setMap` with the new array.

---

### H-24: Duplicated Fetch Response Pattern (77 occurrences)

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 40 files |
| **Short Description** | `.then(response => response.ok ? response.json() : Promise.reject(response))` copy-pasted 77 times. |

**Detailed Description:**
The fetch response check pattern is the single most duplicated code in the codebase. It appears verbatim in 72 locations (JSON variant) plus 5 more (text variant) across 40 files. This means any change to error handling strategy (e.g., adding structured error extraction) must be made in 77 places.

**Suggestion:**
Create shared utility functions: `parseJsonResponse(response)` and `parseTextResponse(response)` and import them everywhere.

---

### H-25: Duplicated GlobalLoginContext Boilerplate (47 occurrences)

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 43 files |
| **Short Description** | `useContext(GlobalLoginContext)` + `fetchWithReLogin()` boilerplate repeated in 47 locations. |

**Detailed Description:**
Every component that makes authenticated API calls must independently import `GlobalLoginContext`, call `useContext(GlobalLoginContext)`, and then pass the result as the first argument to `fetchWithReLogin()`. This is 3 lines of boilerplate per component, 47 times.

**Suggestion:**
Create a `useAuthFetch()` custom hook that wraps both: `const authFetch = useAuthFetch(); authFetch(url, options)`. This eliminates H-24 and H-25 simultaneously.

---

### H-26: Duplicated Loading Spinner (32 occurrences)

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 20 files |
| **Short Description** | Centered `<CircularProgress/>` spinner pattern repeated 32 times with slight variations. |

**Detailed Description:**
The `<Grid container justifyContent="center"><Grid><CircularProgress/></Grid></Grid>` pattern is used in 9 exact matches and 23 variations across 20 files.

**Suggestion:**
Create a `<CenteredSpinner />` component.

---

### H-27: Monolithic QuestionnaireStyle (747 lines, 45 consumers)

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **File** | `questionnaire/QuestionnaireStyle.jsx` |
| **Short Description** | A single 747-line style object is imported by 45 components, each using only 1-3 styles. |

**Detailed Description:**
`QuestionnaireStyle.jsx` contains a monolithic theme function that produces styles for every questionnaire-related component. All 45 consumers import the entire object via `withStyles(Component, QuestionnaireStyle)`. This creates unnecessary coupling and makes it impossible to tree-shake unused styles. Internally, `headerSection` and `footerSection` (lines 407-451) are near-identical copies.

**Suggestion:**
Split into domain-specific style modules. Extract header/footer into a factory function parameterized by position.

---

## Medium Findings

### M-01: XSS via `dangerouslySetInnerHTML` in VocabularyDetails

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `VocabularyDetails.jsx:94` |
| **Short Description** | Server vocabulary descriptions rendered as raw HTML without sanitization. |

**Detailed Description:**
`<span dangerouslySetInnerHTML={{ __html: vocabulary.description }} />` renders vocabulary description HTML without sanitization. A compromised vocabulary source enables stored XSS.

**Suggestion:**
Sanitize with DOMPurify or use the existing `FormattedText` markdown component.

---

### M-02: XSS via `dangerouslySetInnerHTML` in PedigreeQuestion

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `questionnaire/PedigreeQuestion.jsx:86` |
| **Short Description** | SVG image data from pedigree answers rendered as raw HTML. SVGs can contain scripts. |

**Detailed Description:**
Pedigree answer data containing SVG is rendered via `dangerouslySetInnerHTML`. SVG elements can contain `<script>` tags, `onload` handlers, and other executable content.

**Suggestion:**
Sanitize SVG content using DOMPurify with `ADD_TAGS: ['svg', ...]` configuration.

---

### M-03: DOM XSS via `innerHTML` in saveLoadEngine

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `pedigree/saveLoadEngine.js:32` |
| **Short Description** | Server data assigned to `innerHTML` for HTML entity decoding; regex missing `g` flag. |

**Detailed Description:**
`tempNode.innerHTML = data.replace(/&amp;/, '&')` uses innerHTML for HTML entity decoding. The regex only replaces the first `&amp;` (missing `g` flag). Crafted input can inject HTML.

**Suggestion:**
Use `textContent` for text extraction or a proper HTML entity decoder.

---

### M-04: DOM XSS via `innerHTML` in templateSelector

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `pedigree/view/templateSelector.js:48` |
| **Short Description** | Template image content assigned to `innerHTML` without sanitization. |

**Detailed Description:**
`pictureBox.innerHTML = template.image` renders template SVG/HTML directly. Malicious template content enables XSS.

**Suggestion:**
Sanitize template images with DOMPurify before DOM insertion.

---

### M-05: DOM XSS via String Concatenation in templateSelector

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `pedigree/view/templateSelector.js:58` |
| **Short Description** | `pictureBox.description` concatenated into HTML without escaping. |

**Detailed Description:**
The fallback branch constructs HTML via string concatenation: `'<table ...>' + pictureBox.description + '</table>'`. HTML metacharacters in the description break out of the text context.

**Suggestion:**
Escape `pictureBox.description` or use `textContent`.

---

### M-06: Multiple `insertAdjacentHTML` Calls in prototypeShim

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `pedigree/shims/prototypeShim.js:412, 424, 434, 445` |
| **Short Description** | Four DOM manipulation methods inject content as raw HTML without sanitization. |

**Detailed Description:**
The prototype shim's `_p_update`, `_p_insert_top`, `_p_insert`, and `_p_insert_after` methods all use `innerHTML` or `insertAdjacentHTML` to inject content. These are general-purpose utilities used throughout the pedigree module.

**Suggestion:**
Add DOMPurify sanitization in the `toHTML()` preprocessing function.

---

### M-07: JCR-SQL2 Injection in ReferenceInput

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `questionnaireEditor/ReferenceInput.jsx:179` |
| **Short Description** | `field` value interpolated into JCR-SQL2 query without escaping. |

**Detailed Description:**
A UUID-like `field` value is interpolated into `WHERE n.'jcr:uuid'='${field}'` without using `escapeJQL()`.

**Suggestion:**
Use `escapeJQL(field)`.

---

### M-08: JCR-SQL2 Injection in SubjectLockAction

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `locking/SubjectLockAction.jsx:182` |
| **Short Description** | Array of UUIDs joined and interpolated into IN clause without per-element escaping. |

**Detailed Description:**
`subjects.join("','")` is interpolated directly into a JCR-SQL2 IN clause. If any UUID value is crafted, injection is possible.

**Suggestion:**
Map each subject through `escapeJQL()` before joining.

---

### M-09: JCR-SQL2 Injection in SubjectSelector (path/UUID)

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `questionnaire/SubjectSelector.jsx:1061` |
| **Short Description** | `@path` and UUID values interpolated without escaping. |

**Detailed Description:**
Both `currentSubject["@path"]` and `subjectID` are interpolated into the query. Path values could contain single quotes.

**Suggestion:**
Escape both values with `escapeJQL()`.

---

### M-10: JCR-SQL2 Injection in SubjectSelector (form filter)

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `questionnaire/SubjectSelector.jsx:1076` |
| **Short Description** | UUID from data array interpolated without escaping. |

**Detailed Description:**
`filteredData[i]['jcr:uuid']` is concatenated directly into a query string without escaping.

**Suggestion:**
Use `escapeJQL()`.

---

### M-11: JCR-SQL2 Injection in ListInput

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `questionnaireEditor/ListInput.jsx:49` |
| **Short Description** | `type.primaryType` and `type.orderProperty` interpolated into a JCR query. |

**Detailed Description:**
Type values from configuration are used directly in the query string. While `encodeURIComponent` protects the URL, it does not protect JCR-SQL2 syntax.

**Suggestion:**
Validate type values match expected patterns (alphanumeric/dots only).

---

### M-12: No CSRF Tokens in Frontend

| Field | Value |
|-------|-------|
| **Type** | Security -- CSRF |
| **File** | Entire codebase |
| **Short Description** | Zero CSRF token handling across all state-modifying POST/DELETE requests. |

**Detailed Description:**
A search for `csrf`, `xsrf`, `_token`, `csrfToken`, `x-csrf` returns zero results. All state-modifying requests (login, user creation, form saves, vocabulary installations, unsubscribe actions) are sent without CSRF protection from the frontend.

**Suggestion:**
Implement CSRF protection using synchronizer token or double-submit cookie pattern. Create a centralized fetch wrapper that includes the token automatically.

---

### M-13: No CSRF Token in User Registration

| Field | Value |
|-------|-------|
| **Type** | Security -- CSRF |
| **File** | `login/RegistrationForm.js:204-212` |
| **Short Description** | User creation POST has no CSRF token. |

**Detailed Description:**
The registration POST to `/system/userManager/user.create.html` includes no CSRF token.

**Suggestion:**
Add CSRF token to the request.

---

### M-14: Google API Key Without Domain Restrictions

| Field | Value |
|-------|-------|
| **Type** | Security -- Credential Exposure |
| **File** | `questionnaire/AddressQuestion.jsx:36, 44` |
| **Short Description** | Google API key stored in module-level variable accessible from dev tools. |

**Detailed Description:**
The Google API key is fetched and stored in a module-level `let googleApiKey` variable. While client-side API keys are inherently visible, this key should have HTTP referrer restrictions in the Google Cloud Console.

**Suggestion:**
Ensure proper domain restrictions are configured for the API key.

---

### M-15: Patient ID in Query String for Unsubscribe

| Field | Value |
|-------|-------|
| **Type** | Security -- Authorization |
| **File** | `patient-portal/Unsubscribe.jsx:72` |
| **Short Description** | Patient parameter taken from URL query string without backend ownership validation. |

**Detailed Description:**
The `patient` URL parameter is used directly in the unsubscribe endpoint. If the backend doesn't validate that the requesting user is authorized for this patient, any user knowing a patient ID can modify subscription status.

**Suggestion:**
Ensure backend validates authorization for the given patient ID.

---

### M-16: Disorder Data Logged to Console

| Field | Value |
|-------|-------|
| **Type** | Security -- PHI Exposure |
| **File** | `pedigree/disorder.js:75` |
| **Short Description** | Patient disorder names and IDs logged to browser console. |

**Detailed Description:**
`console.log('LOADED DISORDER: disorder id = ' + this._disorderID + ', name = ' + parsed.label)` outputs PHI.

**Suggestion:**
Remove or gate behind debug flag.

---

### M-17: HPO Phenotype Data Logged to Console

| Field | Value |
|-------|-------|
| **Type** | Security -- PHI Exposure |
| **File** | `pedigree/hpoTerm.js:75` |
| **Short Description** | Patient phenotype identifiers and names logged to console. |

**Detailed Description:**
`console.log('LOADED HPO TERM: id = ' + HPOTerm.desanitizeID(this._hpoID) + ', name = ' + parsed.name[0])` outputs PHI.

**Suggestion:**
Remove or gate behind debug flag.

---

### M-18: GEDCOM Family Data Logged to Console

| Field | Value |
|-------|-------|
| **Type** | Security -- PHI Exposure |
| **File** | `pedigree/model/import.js:765` |
| **Short Description** | Complete GEDCOM family tree data logged to console. |

**Detailed Description:**
`console.log('GEDCOM object: ' + JSON.stringify(gedcom))` outputs full family tree data that may include names, birth dates, and medical conditions.

**Suggestion:**
Remove or gate behind debug flag.

---

### M-19: Pedigree Undo Stack Debug Logging

| Field | Value |
|-------|-------|
| **Type** | Security -- PHI Exposure |
| **File** | `pedigree/undoRedo.js:266-272` |
| **Short Description** | Full serialized pedigree states dumped to console in debug method. |

**Detailed Description:**
The `_debug_print_states` method outputs the complete undo stack including all serialized pedigree states (medical data) to console.

**Suggestion:**
Remove or ensure it is only available in development builds.

---

### M-20: Pedigree Editor Exposed as `window.editor`

| Field | Value |
|-------|-------|
| **Type** | Security -- Data Exposure |
| **File** | `pedigree/pedigree.js:64` |
| **Short Description** | Entire pedigree editor object exposed on `window`, allowing data exfiltration. |

**Detailed Description:**
`window.editor = this` makes the full pedigree editor object globally accessible. Any browser extension or injected script can call `window.editor.getGraph().toJSON()` to exfiltrate patient family medical data.

**Suggestion:**
Use module-scoped variables instead of attaching to `window`.

---

### M-21: Open Redirect in Pedigree Editor

| Field | Value |
|-------|-------|
| **Type** | Security -- Open Redirect |
| **File** | `pedigree/pedigree.js:141` |
| **Short Description** | `returnUrl` from options used for redirect without validation. |

**Detailed Description:**
`window.location = returnUrl` where `returnUrl` comes from constructor options. If an attacker can influence these options, they can redirect to an arbitrary URL.

**Suggestion:**
Validate that `returnUrl` is a relative path or same-origin.

---

### M-22: No ErrorBoundary Components

| Field | Value |
|-------|-------|
| **Type** | Security -- Missing Control |
| **File** | Entire codebase |
| **Short Description** | No React ErrorBoundary components exist; unhandled errors crash the entire UI. |

**Detailed Description:**
Zero ErrorBoundary components in the codebase. Any unhandled error in a React component crashes the entire application, potentially losing unsaved patient form data and exposing internal details.

**Suggestion:**
Add ErrorBoundary wrappers around major UI sections (forms, pedigree editor, admin panels, patient portal).

---

### M-23: Temporal Dead Zone in SessionExpiryWarningModal

| Field | Value |
|-------|-------|
| **Type** | Bug -- TDZ / Variable Scope |
| **File** | `questionnaire/SessionExpiryWarningModal.jsx:65` |
| **Short Description** | `warningTimer` referenced before its `const` declaration, causing `ReferenceError`. |

**Detailed Description:**
`warningTimer && clearTimeout(warningTimer)` on line 65 references `warningTimer` before its `const` declaration on line 68. Due to the temporal dead zone of `const`/`let`, this throws a `ReferenceError` at runtime.

**Suggestion:**
Store `warningTimer` in a `useRef` instead of a local `const`.

---

### M-24: Operator Precedence Ambiguity with Null Guard

| Field | Value |
|-------|-------|
| **Type** | Bug -- Operator Precedence |
| **File** | `patient-portal/PatientIdentification.jsx:169` |
| **Short Description** | `!dob.isValid \|\| !mrn && !hc` lacks parentheses; `dob` can be `null`. |

**Detailed Description:**
The condition works correctly due to operator precedence, but `dob` is initialized as `null` on line 111, so `!dob.isValid` throws when `dob` is null.

**Suggestion:**
Add parentheses and null guard: `if (!dob?.isValid || (!mrn && !hc))`.

---

### M-25: beforeunload Listener Never Removed

| Field | Value |
|-------|-------|
| **Type** | Bug -- Event Listener Leak |
| **File** | `patient-portal/QuestionnaireSet.jsx:321-324` |
| **Short Description** | Anonymous arrow function listener cannot be removed; accumulates on each state change. |

**Detailed Description:**
The anonymous function passed to `addEventListener` has no stored reference. No cleanup function is returned from the useEffect. Listeners accumulate.

**Suggestion:**
Store the handler in a variable and return a cleanup function.

---

### M-26: Missing useEffect Dependency Array in VocabularyBranch

| Field | Value |
|-------|-------|
| **Type** | Bug -- Event Listener Leak |
| **File** | `vocabQuery/VocabularyBranch.jsx:95-108` |
| **Short Description** | useEffect with no dependency array adds/removes listeners on every render. |

**Detailed Description:**
Three event listeners are added/removed on every render cycle. This is a performance concern and can cause missed events.

**Suggestion:**
Add `[maxAnswers]` dependency array.

---

### M-27: Missing useEffect Dependency Array in DragAndDrop

| Field | Value |
|-------|-------|
| **Type** | Bug -- Event Listener Leak |
| **File** | `components/DragAndDrop.jsx:111-131` |
| **Short Description** | Four event listeners added/removed on every render due to missing dependency array. |

**Detailed Description:**
Same pattern as M-26 but with four drag-related event listeners.

**Suggestion:**
Add `[]` dependency array.

---

### M-28: Missing useEffect Dependency Array in AdminResourceListing

| Field | Value |
|-------|-------|
| **Type** | Bug -- Event Listener Leak |
| **File** | `adminDashboard/AdminResourceListing.jsx:78-85` |
| **Short Description** | No dependency array causes constant listener churn alongside window/document mismatch. |

**Detailed Description:**
In addition to the window/document mismatch (H-16), the missing dependency array causes re-registration on every render.

**Suggestion:**
Add appropriate dependency array.

---

### M-29: Missing `.catch()` in ExportButton

| Field | Value |
|-------|-------|
| **Type** | Bug -- Unhandled Promise Rejection |
| **File** | `dataHomepage/ExportButton.jsx:155-173` |
| **Short Description** | Two fetch chains reject on non-OK responses but have no `.catch()` handler. |

**Detailed Description:**
Both useEffect fetches use `Promise.reject(response)` for non-OK responses but lack `.catch()`. This creates unhandled promise rejections.

**Suggestion:**
Add `.catch()` handlers.

---

### M-30: Missing `.catch()` in SearchBar

| Field | Value |
|-------|-------|
| **Type** | Bug -- Unhandled Promise Rejection |
| **File** | `SearchBar.jsx:109-117` |
| **Short Description** | Config fetch rejects on error but has no `.catch()`. |

**Detailed Description:**
The QuickSearch config fetch uses `Promise.reject(response)` but has no `.catch()`.

**Suggestion:**
Add `.catch()` handler.

---

### M-31: No AbortController Usage Anywhere

| Field | Value |
|-------|-------|
| **Type** | Bug -- Race Condition |
| **File** | Multiple files |
| **Short Description** | Zero AbortController usage across dozens of useEffect fetch calls. |

**Detailed Description:**
Fetch calls in useEffect hooks are never aborted on component unmount, causing setState-after-unmount warnings and potential race conditions with stale data.

**Suggestion:**
Implement AbortController in useEffect cleanup functions.

---

### M-32: DowntimeBanner Missing `response.ok` Check

| Field | Value |
|-------|-------|
| **Type** | Bug -- Missing Error Check |
| **File** | `DowntimeBanner.jsx:38-39` |
| **Short Description** | `.json()` called without checking `response.ok`; will throw on non-JSON error responses. |

**Detailed Description:**
Unlike most fetches in the codebase, this one calls `response.json()` directly without checking `response.ok` first.

**Suggestion:**
Add `response.ok` check before calling `.json()`.

---

### M-33: PrincipalsContainer Missing `response.ok` Check

| Field | Value |
|-------|-------|
| **Type** | Bug -- Missing Error Check |
| **File** | `Userboard/PrincipalsContainer.jsx:42, 57` |
| **Short Description** | Two fetches call `.json()` without `response.ok` check. |

**Detailed Description:**
Same issue as M-32 in two fetch calls.

**Suggestion:**
Add `response.ok` check.

---

### M-34: Missing `.catch()` in ClinicDashboard

| Field | Value |
|-------|-------|
| **Type** | Bug -- Unhandled Promise Rejection |
| **File** | `patient-portal/ClinicDashboard.jsx:135-142` |
| **Short Description** | Clinic config fetch has no error handling. |

**Detailed Description:**
The `fetchWithReLogin` call rejects on non-OK responses but there is no `.catch()`.

**Suggestion:**
Add `.catch()` handler.

---

### M-35: HTTP 500 Treated as Auth Failure

| Field | Value |
|-------|-------|
| **Type** | Bug -- Logic Error |
| **File** | `login/ReLoginDialog.js:34` |
| **Short Description** | `response.status == 500` triggers re-login dialog instead of error message. |

**Detailed Description:**
HTTP 500 (Internal Server Error) and 401 (Unauthorized) are handled identically, opening the re-login dialog. Users see a misleading login prompt when the server has an internal error.

**Suggestion:**
Remove `response.status == 500` from the auth-failure condition or handle it separately.

---

### M-36: Direct State Mutation via `++`

| Field | Value |
|-------|-------|
| **Type** | Bug -- State Mutation |
| **File** | `dataHomepage/LiveTable.jsx:133` |
| **Short Description** | `++fetchStatus.currentRequestNumber` mutates state object directly. |

**Detailed Description:**
The pre-increment operator mutates the `fetchStatus` state object in place. React will not detect this change.

**Suggestion:**
Use a proper state setter pattern to increment the counter.

---

### M-37: Side Effect in Render Body

| Field | Value |
|-------|-------|
| **Type** | Bug -- React Violation |
| **File** | `dataHomepage/SubjectView.jsx:114-116` |
| **Short Description** | `fetchSubjectTypes()` network request called during render, not in useEffect. |

**Detailed Description:**
A network request is triggered directly in the render function body. This violates React's rules and can cause infinite loops in Strict Mode.

**Suggestion:**
Move the fetch trigger into a `useEffect`.

---

### M-38: `useEffect` with `[0]` Dependency

| Field | Value |
|-------|-------|
| **Type** | Bug -- Incorrect Dependencies |
| **File** | `VocabularyActions.jsx:109` |
| **Short Description** | `[0]` constant literal used as dependency array instead of `[]`. |

**Detailed Description:**
While functionally similar to `[]`, the `[0]` dependency is confusing and unconventional. `props.addSetter` is missing from dependencies.

**Suggestion:**
Change to `[]`.

---

### M-39: Null Dereference on Regex Match in Form

| Field | Value |
|-------|-------|
| **Type** | Bug -- Null Access |
| **File** | `questionnaire/Form.jsx:125` |
| **Short Description** | Regex `.exec()` may return null if pathname doesn't match pattern. |

**Detailed Description:**
`props.id || /Forms\/([^./]+)/.exec(location.pathname)[1]` -- if `props.id` is falsy and pathname doesn't match, `.exec()` returns `null`.

**Suggestion:**
Guard with optional chaining or try/catch.

---

### M-40: ResizeObserver Cleanup Race Condition

| Field | Value |
|-------|-------|
| **Type** | Bug -- Cleanup Race |
| **File** | `questionnaire/SelectableAreaQuestion.jsx:337` |
| **Short Description** | `questionRef.current` may be null during cleanup, causing `unobserve(null)` to throw. |

**Detailed Description:**
The cleanup function checks `questionRef.current` but there is a TOCTOU (time-of-check-time-of-use) issue where the ref could become null between the check and the `unobserve` call.

**Suggestion:**
Use `observer.disconnect()` in cleanup instead.

---

### M-41: State Mutation During Render

| Field | Value |
|-------|-------|
| **Type** | Bug -- State Mutation |
| **File** | `dataHomepage/SubjectView.jsx:100-102` |
| **Short Description** | `setColumns` called from `fetchSubjectTypes` which runs during render. |

**Detailed Description:**
State setter is called indirectly during the render phase via `fetchSubjectTypes()` (see M-37).

**Suggestion:**
Move into a useEffect.

---

### M-42: Loose Equality with String Comparison

| Field | Value |
|-------|-------|
| **Type** | Bug -- Type Coercion |
| **File** | `SearchBar.jsx:115` |
| **Short Description** | `== 'true'` used instead of `=== 'true'`; fragile if server returns boolean. |

**Detailed Description:**
`setShowTotalRows(json["showTotalRows"] == 'true')` uses loose equality. If the server changes from string to boolean, behavior changes subtly.

**Suggestion:**
Use `=== 'true'` or explicitly handle both types.

---

### M-43: Near-Duplicate Filter Components

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 6 files in `dataHomepage/FilterComponents/` |
| **Short Description** | 6 filter components share identical imports, propTypes, comparator setup, and registration boilerplate. |

**Detailed Description:**
BooleanFilter, ListFilter, NumericFilter, TextFilter, DateFilter, and VocabularyFilter all follow the exact same structural pattern. The propTypes are identical. The comparator construction is duplicated. The `FilterComponentManager.registerFilterComponent()` boilerplate is duplicated.

**Suggestion:**
Create a `createFilter(renderFn, { comparators, priority, matchFn })` higher-order function.

---

### M-44: Repeated URL Construction Pattern

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 25+ files |
| **Short Description** | `new URL(path, window.location.origin)` repeated 48+ times across 25+ files. |

**Detailed Description:**
The URL construction pattern using `new URL(...)` or direct string concatenation with `window.location.origin` appears in 48+ locations.

**Suggestion:**
Create a `buildUrl(path, params)` utility.

---

### M-45: Duplicated JQL Query Building

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 9+ files |
| **Short Description** | `/query?query=` + `encodeURIComponent(...)` pattern repeated in 9+ files. |

**Detailed Description:**
Multiple files construct JQL queries via string concatenation with the same URL prefix pattern.

**Suggestion:**
Create a `buildQueryUrl(jql, options)` utility.

---

### M-46: Pedigree Legend Near-Duplicate Classes

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 3 files in `pedigree/view/` |
| **Short Description** | disorderLegend, geneLegend, and hpoLegend share highly similar methods and structure. |

**Detailed Description:**
All three extend `Legend` and duplicate `_getPrefix()`, `_generateElement()`, `_generateColor()`, cache patterns, and `addCase()` overrides. The `_generateColor` methods share the same algorithm with only different color palettes.

**Suggestion:**
Move shared logic into the `Legend` base class, parameterize prefix, event name, and preferred colors.

---

### M-47: Duplicated Admin Config Screen Pattern

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 4 files in `patient-portal/` |
| **Short Description** | 4 config screens share identical state, buildConfigData, and AdminConfigScreen props pattern. |

**Detailed Description:**
SurveyInstructionsConfiguration, PatientAccessConfiguration, ToUConfiguration, and DashboardSettingsConfiguration all share identical boilerplate for `hasChanges` state, `buildConfigData` iteration, and `AdminConfigScreen` props.

**Suggestion:**
Create a declarative config screen component that accepts a field schema.

---

### M-48: Repeated Dialog Open/Close State Management

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 7+ files |
| **Short Description** | Identical `[open, setOpen]` + `openDialog`/`closeDialog` pattern repeated 7+ times. |

**Detailed Description:**
The dialog state boilerplate (`useState(false)`, `useState("")`, open/close handlers with guards) appears in DeleteButton, SubjectLockAction, ExportButton, UserDashboard, AdminStatistics, CreateGroupDialog, and ChangeUserPasswordDialog.

**Suggestion:**
Create a `useDialog()` custom hook returning `{ open, error, setError, openDialog, closeDialog }`.

---

## Low Findings

### L-01: DOM XSS via `innerHTML` in Datepicker

| Field | Value |
|-------|-------|
| **Type** | Security -- XSS |
| **File** | `pedigree/view/datepicker.js:44-51` |
| **Short Description** | Option values concatenated into HTML string without escaping. |

**Detailed Description:**
Values are concatenated into `<option value="...">` elements and assigned via `innerHTML`. Special characters could break out of the attribute context.

**Suggestion:**
Use `document.createElement('option')` and set `.value`/`.textContent` programmatically.

---

### L-02: DOM Selector Injection in DroppableAnswerOptionList

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `questionnaireEditor/DroppableAnswerOptionList.jsx:76` |
| **Short Description** | `sourceData.valueId` used in CSS selector without escaping. |

**Detailed Description:**
`document.querySelector(\`[data-option-id="${sourceData.valueId}"]\`)` -- special characters could break the selector.

**Suggestion:**
Use `CSS.escape(sourceData.valueId)`.

---

### L-03: DOM Selector Injection in Pedigree NodeMenu

| Field | Value |
|-------|-------|
| **Type** | Security -- Injection |
| **File** | `pedigree/view/nodeMenu.js:264, 282, 442, 467, 492` |
| **Short Description** | ID values concatenated into `querySelectorAll` without escaping. |

**Detailed Description:**
Multiple `querySelectorAll` calls concatenate `id` values directly into CSS selector strings.

**Suggestion:**
Use `CSS.escape(id)`.

---

### L-04: No AbortController Usage

| Field | Value |
|-------|-------|
| **Type** | Security -- Missing Control |
| **File** | Entire codebase |
| **Short Description** | Zero AbortController usage across dozens of useEffect fetch calls. |

**Detailed Description:**
Unmounted components with pending fetch requests attempt state updates, causing memory leaks and race conditions.

**Suggestion:**
Use AbortController in useEffect cleanup functions.

---

### L-05: Missing `rel="noopener"` on External Link

| Field | Value |
|-------|-------|
| **Type** | Security -- Missing Attribute |
| **File** | `vocabQuery/InfoBox.jsx:114` |
| **Short Description** | `target="_blank"` without `rel="noopener noreferrer"`. |

**Detailed Description:**
External link opens in a new tab without `rel="noopener"`. The opened page could access `window.opener`. Modern browsers mitigate this by default.

**Suggestion:**
Add `rel="noopener noreferrer"`.

---

### L-06: Computed Question Values Logged to Console

| Field | Value |
|-------|-------|
| **Type** | Security -- PHI Exposure |
| **File** | `questionnaire/ComputedQuestion.jsx:121` |
| **Short Description** | Computed values (potentially derived from medical data) logged to console. |

**Detailed Description:**
`console.log("Setting value to " + data["label"])` outputs computed question values that may include lab results, BMI, or risk scores.

**Suggestion:**
Remove this log statement.

---

### L-07: Triple Negation `!!!` Used Redundantly

| Field | Value |
|-------|-------|
| **Type** | Bug -- Code Smell |
| **Files** | `QuestionnaireSet.jsx:376`, `Questionnaire.jsx:366`, `PrintPreview.jsx:241`, and 6 more |
| **Short Description** | `!!!x` is equivalent to `!x`; confusing and suggests truthiness confusion. |

**Detailed Description:**
Nine instances of `!!!x` across the codebase. While not a runtime bug, it increases maintenance burden and suggests the developer may have been confused about JavaScript truthiness rules.

**Suggestion:**
Replace `!!!x` with `!x`.

---

### L-08: Pervasive Loose Equality `==`

| Field | Value |
|-------|-------|
| **Type** | Bug -- Type Coercion |
| **Files** | Multiple (PatientIdentification, QuestionnaireSet, FormContext, ReLoginDialog, etc.) |
| **Short Description** | `==` used instead of `===` in many comparisons throughout the codebase. |

**Detailed Description:**
The codebase uses loose equality `==` rather than strict equality `===` in dozens of comparisons. While often harmless, this risks unexpected type coercion in edge cases.

**Suggestion:**
Replace `==` with `===` throughout.

---

### L-09: Duplicated `capitalize` Functions

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 6 files |
| **Short Description** | `text.charAt(0).toUpperCase() + text.slice(1)` implemented independently in 7 locations. |

**Detailed Description:**
The capitalize pattern is implemented as local functions or inline expressions in DeleteButton, NumberInput, LabeledField, QuestionMatrix, TextQuestion, and person.js.

**Suggestion:**
Add a shared `capitalize` utility to `escape.jsx` and import everywhere.

---

### L-10: Duplicated Logout URL Pattern

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | 2 files |
| **Short Description** | Logout URL construction repeated 3 times across 2 files. |

**Detailed Description:**
`'/system/sling/logout?resource=' + encodeURIComponent(window.location.pathname)` appears in PatientIdentification (2x) and Header.

**Suggestion:**
Create a `getLogoutUrl()` utility function.

---

### L-11: Duplicated Close Button Styles

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **Files** | `components/ErrorDialog.jsx`, `components/ResponsiveDialog.jsx` |
| **Short Description** | Identical close button positioning styles in two dialog components. |

**Detailed Description:**
The `closeButton: { position: 'absolute', right: theme.spacing(1), top: theme.spacing(1) }` style is duplicated identically.

**Suggestion:**
Extract a `DialogCloseButton` component with shared styles.

---

### L-12: Color Box Shadow Template Duplication

| Field | Value |
|-------|-------|
| **Type** | Duplication |
| **File** | `themeStyle.jsx:103-194` |
| **Short Description** | 14 nearly identical box shadow and card header definitions differing only in color. |

**Detailed Description:**
Seven box shadow objects and seven card header objects are copy-pasted with only the color value changed.

**Suggestion:**
Create `makeBoxShadow(color)` and `makeCardHeader(color1, color2, shadow)` factory functions.
