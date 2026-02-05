# Security Vulnerability Analysis Report

**Codebase:** `aggregated-frontend/src/main/frontend/src/`
**Application:** CARDS -- Medical data collection platform (React 19, MUI 7, React Router 7)
**Date:** 2026-02-05
**Total Findings:** 36

## Executive Summary

The CARDS frontend contains several critical and high-severity vulnerabilities. The most concerning are: five instances of `new Function()` enabling remote code execution, a critical authentication token bug that breaks the patient portal login flow, multiple unsanitized HTML injection points with no sanitization library in the project dependencies, JCR-SQL2 query injection in at least eight locations, open redirect vulnerabilities in the login system, and pervasive logging of Protected Health Information (PHI) to the browser console. The complete absence of CSRF token handling on the frontend is also noteworthy.

## Severity Summary

| Severity | Count | Key Areas |
|----------|-------|-----------|
| CRITICAL | 3 | `new Function()` RCE (ExtensionPoint, AssetManager), Auth token bug (PatientIdentification) |
| HIGH | 10 | `new Function()` (ComputedQuestion, PrototypeShim), Open redirects, JCR injection, No sanitization library, Auth token in URL, PHI console logging |
| MEDIUM | 17 | `dangerouslySetInnerHTML`/`innerHTML` (6), JCR injection (4), CSRF absent, Event listener mismatch, Router incompatibilities, `window.editor` global |
| LOW | 6 | Datepicker innerHTML, DOM selector injection (2), AbortController missing, Missing `rel="noopener"`, Computed value logging |

---

## 1. XSS Vulnerabilities (12 findings)

### 1.1 Remote Code Execution via `new Function()` in Extension System

- **Severity:** CRITICAL
- **File:** `uiextension/ExtensionPoint.jsx:87`
- **Code:**
  ```javascript
  return new Function(text)();
  ```
- **Risk:** JavaScript fetched from a remote UIXP endpoint is executed directly. If the endpoint is compromised or a man-in-the-middle attack occurs, arbitrary code runs in the user's authenticated session. No integrity checks or CSP nonces are applied.
- **Fix:** Use subresource integrity hashes for loaded scripts, load extensions only from a pre-approved allowlist, or move to a sandboxed iframe approach for untrusted extensions.

### 1.2 Remote Code Execution via `new Function()` in Asset Manager

- **Severity:** CRITICAL
- **File:** `assetManager.jsx:156`
- **Code:**
  ```javascript
  var returnVal = new Function("module", "exports", remoteComponentSrc + "\nreturn module.exports || exports;")(module, module.exports);
  ```
- **Risk:** Remote source code fetched over HTTP is evaluated as a JavaScript function. Compromise of the asset server grants full JavaScript execution in any client session, which on a medical platform means potential exfiltration of PHI/PII.
- **Fix:** Implement subresource integrity (SRI) verification. Ensure assets are served from same-origin with proper CSP headers. Consider Webpack dynamic imports for code splitting instead of runtime evaluation.

### 1.3 Code Injection via `new Function()` in Computed Questions

- **Severity:** HIGH
- **File:** `questionnaire/ComputedQuestion.jsx:250`
- **Code:**
  ```javascript
  result = new Function(expressionArguments, parsedExpression)(...expressionValues);
  ```
- **Risk:** Server-defined questionnaire expressions are executed as JavaScript. If an attacker can modify a questionnaire definition (via admin access or a compromised backend), they can execute arbitrary code in patients' browsers. The `form` context containing all answer data is passed as an available variable.
- **Fix:** Use a safe expression evaluator library (e.g., `mathjs`, `expr-eval`) that only allows known mathematical and logical operations.

### 1.4 Remote Code Execution via `new Function()` in Prototype Shim

- **Severity:** HIGH
- **File:** `pedigree/shims/prototypeShim.js:1034-1036`
- **Code:**
  ```javascript
  return new Function('return (' + responseText + ')')();
  // fallback:
  return new Function(responseText)();
  ```
- **Risk:** Ajax response text is executed as code directly within the `evalResponse` method. Any compromised server response or MITM attack enables RCE in the browser.
- **Fix:** Replace with `JSON.parse()` for JSON responses.

### 1.5 Stored XSS via `dangerouslySetInnerHTML` in Extension Point

- **Severity:** HIGH
- **File:** `uiextension/ExtensionPoint.jsx:101`
- **Code:**
  ```javascript
  setRenderedResponse((<div dangerouslySetInnerHTML={{ __html: text }}/>));
  ```
- **Risk:** HTML fetched from a remote extension endpoint is rendered without sanitization. An attacker controlling the extension content can inject script tags or event handlers.
- **Fix:** Sanitize the HTML using DOMPurify before rendering.

### 1.6 Stored XSS via `dangerouslySetInnerHTML` in Vocabulary Details

- **Severity:** MEDIUM
- **File:** `VocabularyDetails.jsx:94`
- **Code:**
  ```javascript
  <Typography><span dangerouslySetInnerHTML={{ __html: vocabulary.description }} /></Typography>
  ```
- **Risk:** Vocabulary descriptions from the server are rendered as raw HTML. A compromised vocabulary source or backend enables XSS in admin browsers.
- **Fix:** Sanitize with DOMPurify or use the existing `FormattedText` markdown component.

### 1.7 XSS via `dangerouslySetInnerHTML` in Pedigree Question

- **Severity:** MEDIUM
- **File:** `questionnaire/PedigreeQuestion.jsx:86`
- **Code:**
  ```javascript
  var image_div = <div className={classes.thumbnail} dangerouslySetInnerHTML={{ __html: displayedImage }}/>;
  ```
- **Risk:** SVG image data from pedigree answers is rendered as raw HTML. SVGs can contain script tags and event handlers. Tampered stored pedigree data becomes a stored XSS vector.
- **Fix:** Sanitize SVG content using DOMPurify with SVG-specific configuration.

### 1.8 DOM XSS via `innerHTML` in Pedigree Save/Load Engine

- **Severity:** MEDIUM
- **File:** `pedigree/saveLoadEngine.js:32`
- **Code:**
  ```javascript
  tempNode.innerHTML = data.replace(/&amp;/, '&');
  ```
- **Risk:** Server data is assigned to `innerHTML` for HTML entity decoding. The regex only replaces the first `&amp;` occurrence (missing the `g` flag). Crafted input can inject HTML.
- **Fix:** Use `textContent` for text extraction, or use a proper HTML entity decoder.

### 1.9 DOM XSS via `innerHTML` in Pedigree Template Selector

- **Severity:** MEDIUM
- **File:** `pedigree/view/templateSelector.js:48`
- **Code:**
  ```javascript
  pictureBox.innerHTML = template.image;
  ```
- **Risk:** Template image content (potentially SVG with embedded scripts) is assigned directly via innerHTML.
- **Fix:** Sanitize template images with DOMPurify before DOM insertion.

### 1.10 DOM XSS via String Concatenation in Template Selector Fallback

- **Severity:** MEDIUM
- **File:** `pedigree/view/templateSelector.js:58`
- **Code:**
  ```javascript
  pictureBox.innerHTML = '<table ...>' + pictureBox.description + '</table>';
  ```
- **Risk:** `pictureBox.description` is concatenated into raw HTML without escaping. HTML metacharacters in the description become a DOM XSS vector.
- **Fix:** Escape `pictureBox.description` or use `textContent`.

### 1.11 DOM XSS via `innerHTML` in Pedigree Datepicker

- **Severity:** LOW
- **File:** `pedigree/view/datepicker.js:44-51`
- **Code:**
  ```javascript
  optionsHTML += '<option value="' + item.value + '"';
  this.span.innerHTML = optionsHTML;
  ```
- **Risk:** Values are concatenated into HTML option elements without escaping. If item values contain quotes or angle brackets, they can break out of the attribute context.
- **Fix:** Use `document.createElement('option')` and set `.value`/`.textContent` programmatically.

### 1.12 Multiple `insertAdjacentHTML` and `innerHTML` Calls in Prototype Shim

- **Severity:** MEDIUM
- **File:** `pedigree/shims/prototypeShim.js:412, 424, 434, 445`
- **Code:**
  ```javascript
  this.innerHTML = content;                       // line 412
  this.insertAdjacentHTML("afterbegin", content);  // line 424
  this.insertAdjacentHTML("beforeend", content);   // line 434
  this.insertAdjacentHTML("afterend", content);    // line 445
  ```
- **Risk:** General-purpose DOM manipulation utilities used throughout the pedigree module. Any caller passing unsanitized data through them enables XSS.
- **Fix:** Add DOMPurify sanitization in the `toHTML()` function that preprocesses content before these assignments.

---

## 2. Injection Risks -- JCR-SQL2 Query Injection (10 findings)

The codebase includes an `escapeJQL()` function in `escape.jsx:24`, but it is only used in `VariantFilesContainer.jsx` and parts of `SubjectSelector.jsx`. Many other query construction sites skip this escaping entirely.

### 2.1 JCR Query Injection in NewFormDialog (questionnaire name)

- **Severity:** HIGH
- **File:** `dataHomepage/NewFormDialog.jsx:146`
- **Code:**
  ```javascript
  let query = `select * from [cards:Questionnaire] as n where name()='${questionnaireName}'`;
  ```
- **Risk:** `questionnaireName` is interpolated directly into a JCR-SQL2 query without using `escapeJQL()`.
- **Fix:** Import and use `escapeJQL(questionnaireName)`.

### 2.2 JCR Query Injection in NewFormDialog (subject UUID)

- **Severity:** HIGH
- **File:** `dataHomepage/NewFormDialog.jsx:220`
- **Code:**
  ```javascript
  `...where f.'subject'='${(currentSubject || selectedSubject)?.['jcr:uuid']}'...`
  ```
- **Risk:** UUID interpolated directly without escaping.
- **Fix:** Escape with `escapeJQL()`.

### 2.3 JCR Query Injection in SubjectFilter (user search input)

- **Severity:** HIGH
- **File:** `dataHomepage/FilterComponents/SubjectFilter.jsx:65`
- **Code:**
  ```javascript
  ` WHERE lower(s.'fullIdentifier') LIKE '%25${formattedQuery}%25'`
  ```
- **Risk:** `formattedQuery` derives from user search input and is interpolated directly into a LIKE clause. This is the most directly exploitable injection point since it comes from a text input field.
- **Fix:** Use `escapeJQL(formattedQuery)`.

### 2.4 JCR Query Injection in ReferenceInput

- **Severity:** MEDIUM
- **File:** `questionnaireEditor/ReferenceInput.jsx:179`
- **Risk:** `field` value interpolated without escaping.
- **Fix:** Use `escapeJQL(field)`.

### 2.5 JCR Query Injection in SubjectLockAction

- **Severity:** MEDIUM
- **File:** `locking/SubjectLockAction.jsx:182`
- **Risk:** Array of UUIDs joined and interpolated into an IN clause without per-element escaping.
- **Fix:** Map each subject through `escapeJQL()` before joining.

### 2.6 JCR Query Injection in SubjectSelector (path and UUID)

- **Severity:** MEDIUM
- **File:** `questionnaire/SubjectSelector.jsx:1061`
- **Risk:** Both `@path` and UUID values are interpolated without escaping.
- **Fix:** Escape both values with `escapeJQL()`.

### 2.7 JCR Query Injection in SubjectSelector (form subject filter)

- **Severity:** MEDIUM
- **File:** `questionnaire/SubjectSelector.jsx:1076`
- **Risk:** UUID values from data array interpolated without escaping.
- **Fix:** Use `escapeJQL()`.

### 2.8 JCR Query Injection in ListInput

- **Severity:** MEDIUM
- **File:** `questionnaireEditor/ListInput.jsx:49`
- **Risk:** `type.primaryType` and `type.orderProperty` interpolated into a JCR query.
- **Fix:** Validate that type values match expected patterns (alphanumeric/dots only).

### 2.9 DOM Selector Injection in DroppableAnswerOptionList

- **Severity:** LOW
- **File:** `questionnaireEditor/DroppableAnswerOptionList.jsx:76`
- **Code:**
  ```javascript
  document.querySelector(`[data-option-id="${sourceData.valueId}"]`);
  ```
- **Risk:** Special CSS selector characters could break the selector or match unintended elements.
- **Fix:** Use `CSS.escape(sourceData.valueId)`.

### 2.10 DOM Selector Injection in Pedigree NodeMenu

- **Severity:** LOW
- **File:** `pedigree/view/nodeMenu.js:264, 282, 442, 467, 492`
- **Risk:** ID values concatenated into CSS selectors without escaping.
- **Fix:** Use `CSS.escape(id)`.

---

## 3. Authentication / Authorization Issues (6 findings)

### 3.1 Critical Auth Token Bug: Wrong Variable in `setAuthToken`

- **Severity:** CRITICAL
- **File:** `patient-portal/PatientIdentification.jsx:203`
- **Code:**
  ```javascript
  let auth_token = new URLSearchParams(window.location.search).get("auth_token");
  setAuthToken(authToken);  // BUG: passes the state variable (undefined), not the local variable
  ```
- **Risk:** `setAuthToken(authToken)` passes the React state variable `authToken` (which is `undefined` at this point) instead of the just-parsed local `auth_token`. The auth token is never stored, breaking token-based patient authentication entirely.
- **Fix:** Change `setAuthToken(authToken)` to `setAuthToken(auth_token)`.

### 3.2 Open Redirect via `resource` Query Parameter in Login

- **Severity:** HIGH
- **File:** `login/LoginForm.js:59`
- **Code:**
  ```javascript
  return new URLSearchParams(window.location.search).get("resource") || currentPath;
  // later: window.location = loginRedirectPath();
  ```
- **Risk:** After successful login, the browser is redirected to whatever URL is in the `resource` query parameter with no validation. An attacker crafting `/login?resource=https://evil.com/phish` redirects authenticated users to a phishing site.
- **Fix:** Validate that the `resource` parameter is a relative path (starts with `/`) and does not contain `//` or protocol schemes.

### 3.3 Open Redirect via `resource` Query Parameter in Registration

- **Severity:** HIGH
- **File:** `login/RegistrationForm.js:186`
- **Code:**
  ```javascript
  window.location = new URLSearchParams(window.location.search).get('resource') || '/';
  ```
- **Risk:** Same open redirect pattern as the login form.
- **Fix:** Validate the redirect URL is same-origin.

### 3.4 Auth Token Exposed in URL Query Parameters

- **Severity:** HIGH
- **File:** `patient-portal/PatientIdentification.jsx:202`
- **Risk:** Patient authentication tokens are passed via URL query parameters. URLs appear in browser history, server access logs, HTTP Referer headers, and can be captured by browser extensions or third-party analytics scripts.
- **Fix:** Use POST requests with tokens in the body, or exchange the URL token for a session cookie immediately and clear the token from the URL using `history.replaceState`.

### 3.5 Google API Key Stored in Module-Level Global Variable

- **Severity:** MEDIUM
- **File:** `questionnaire/AddressQuestion.jsx:36, 44`
- **Risk:** The Google API key is stored in a module-level variable accessible from browser dev tools. While this is a client-side key by nature, it should have proper domain restrictions.
- **Fix:** Ensure the Google API key has HTTP referrer restrictions in the Google Cloud Console.

### 3.6 Patient ID Passed Directly in Query String for Unsubscribe

- **Severity:** MEDIUM
- **File:** `patient-portal/Unsubscribe.jsx:72`
- **Risk:** The `patient` parameter is taken directly from the URL query string. If the backend does not validate ownership, any user who knows a patient ID can query or modify subscription status.
- **Fix:** Ensure the backend validates the requesting user is authorized for the given patient ID.

---

## 4. Insecure Data Handling -- PHI/PII Exposure (8 findings)

### 4.1 Full Pedigree Medical Data Logged on Save

- **Severity:** HIGH
- **File:** `pedigree/saveLoadEngine.js:161`
- **Code:**
  ```javascript
  console.log('[SAVE] data: ' + JSON.stringify(jsonData));
  ```
- **Risk:** Complete pedigree JSON (family medical history, genetic data, disorders, phenotypes) logged to browser console on every save. This is PHI under HIPAA.
- **Fix:** Remove or gate behind a `DEBUG` environment variable disabled in production.

### 4.2 Full Pedigree JSON Logged on Load

- **Severity:** HIGH
- **File:** `pedigree/saveLoadEngine.js:173`
- **Code:**
  ```javascript
  console.log('[LOAD] recived JSON: ' + initialPedigreeJSONString);
  ```
- **Risk:** Complete patient pedigree medical data logged on every load.
- **Fix:** Remove or gate behind debug flag.

### 4.3 Disorder Data Logged to Console

- **Severity:** MEDIUM
- **File:** `pedigree/disorder.js:75`
- **Risk:** Patient disorder names and IDs logged to console.
- **Fix:** Remove or gate behind debug flag.

### 4.4 HPO Phenotype Data Logged to Console

- **Severity:** MEDIUM
- **File:** `pedigree/hpoTerm.js:75`
- **Risk:** Patient phenotype/medical condition identifiers and names logged to console.
- **Fix:** Remove or gate behind debug flag.

### 4.5 GEDCOM Family Tree Data Logged to Console

- **Severity:** MEDIUM
- **File:** `pedigree/model/import.js:765`
- **Risk:** Complete GEDCOM family tree data (potentially including names, birth dates, medical conditions) logged to console.
- **Fix:** Remove or gate behind debug flag.

### 4.6 Pedigree Undo Stack Debug Logging

- **Severity:** MEDIUM
- **File:** `pedigree/undoRedo.js:266-272`
- **Risk:** Full serialized pedigree states (medical data) dumped to console.
- **Fix:** Remove or compile out in production builds.

### 4.7 Computed Question Values Logged to Console

- **Severity:** LOW
- **File:** `questionnaire/ComputedQuestion.jsx:121`
- **Risk:** Computed question values (potentially derived from medical data) are logged.
- **Fix:** Remove this log statement.

### 4.8 Pedigree Editor Exposed as Global `window.editor`

- **Severity:** MEDIUM
- **File:** `pedigree/pedigree.js:64`
- **Code:**
  ```javascript
  window.editor = this;
  ```
- **Risk:** The entire pedigree editor object is exposed on `window`. Any browser extension or injected script can call `window.editor.getGraph().toJSON()` to exfiltrate patient family medical data.
- **Fix:** Use module-scoped variables instead of attaching to the global object.

---

## 5. CSRF Vulnerabilities (2 findings)

### 5.1 No CSRF Tokens Anywhere in the Frontend

- **Severity:** MEDIUM
- **File:** Entire codebase
- **Risk:** The frontend includes zero CSRF token handling. All state-modifying POST and DELETE requests are sent without CSRF protection.
- **Fix:** Implement CSRF protection using the synchronizer token pattern or double-submit cookie pattern.

### 5.2 User Registration Lacks CSRF Protection

- **Severity:** MEDIUM
- **File:** `login/RegistrationForm.js:204-212`
- **Risk:** An attacker could forge user creation requests from a victim's session.
- **Fix:** Add CSRF token.

---

## 6. Open Redirects (3 findings)

### 6.1 Open Redirect in LoginForm

See Finding 3.2.

### 6.2 Open Redirect in RegistrationForm

See Finding 3.3.

### 6.3 Potential Open Redirect in Pedigree Editor

- **Severity:** MEDIUM
- **File:** `pedigree/pedigree.js:141`
- **Code:**
  ```javascript
  var returnUrl = options.returnUrl || null;
  window.location = returnUrl;
  ```
- **Risk:** `returnUrl` comes from options passed to the PedigreeEditor constructor. If an attacker can influence these options, they can redirect the user to an arbitrary URL.
- **Fix:** Validate that `returnUrl` is a relative path or same-origin before navigating.

---

## 7. Missing Security Controls (4 findings)

### 7.1 No HTML Sanitization Library in Project Dependencies

- **Severity:** HIGH
- **File:** `package.json`
- **Risk:** The project has zero sanitization libraries despite 3 uses of `dangerouslySetInnerHTML`, 7+ uses of `innerHTML`, and 3 uses of `insertAdjacentHTML`.
- **Fix:** Add `dompurify` to dependencies. Create a utility function that wraps all HTML injection points.

### 7.2 No React ErrorBoundary Components Anywhere

- **Severity:** MEDIUM
- **File:** Entire codebase
- **Risk:** Unhandled errors crash the entire UI, potentially losing unsaved patient form data.
- **Fix:** Add ErrorBoundary wrappers around major UI sections.

### 7.3 No AbortController Usage for Fetch Requests

- **Severity:** LOW
- **File:** Entire codebase
- **Risk:** Unmounted components with pending fetch requests attempt state updates after unmounting.
- **Fix:** Use AbortController in useEffect cleanup functions.

### 7.4 Missing `rel="noopener"` on External Link

- **Severity:** LOW
- **File:** `vocabQuery/InfoBox.jsx:114`
- **Risk:** External link with `target="_blank"` but without `rel="noopener noreferrer"`.
- **Fix:** Add `rel="noopener noreferrer"`.
