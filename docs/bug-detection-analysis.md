# Bug Detection Analysis Report

**Codebase:** `aggregated-frontend/src/main/frontend/src/`
**Application:** CARDS -- Medical data collection platform (React 19, MUI 7, React Router 7)
**Date:** 2026-02-05
**Total Findings:** 37

## Severity Summary

| Severity | Count |
|----------|-------|
| Critical | 2 |
| High | 15 |
| Medium | 18 |
| Low | 2 |

---

## 1. Typos and Wrong Variable References

### 1.1 `setAuthToken(authToken)` Uses Wrong Variable Name

- **Severity:** Critical
- **File:** `patient-portal/PatientIdentification.jsx:203`
- **Code:**
  ```jsx
  let auth_token = new URLSearchParams(window.location.search).get("auth_token");
  setAuthToken(authToken);  // BUG: uses `authToken` (state, undefined) instead of `auth_token` (local)
  ```
- **Description:** The local variable `auth_token` is extracted from the URL search params on line 202, but on line 203 `setAuthToken(authToken)` references the state variable `authToken` (declared on line 108 as `useState()`, so it's `undefined`). The auth token from the URL is never stored in state, breaking the entire patient authentication flow.
- **Fix:** Change `setAuthToken(authToken)` to `setAuthToken(auth_token)`.

### 1.2 Temporal Dead Zone in SessionExpiryWarningModal

- **Severity:** Medium
- **File:** `questionnaire/SessionExpiryWarningModal.jsx:65`
- **Code:**
  ```jsx
  warningTimer && clearTimeout(warningTimer);  // Used before declaration
  const warningTimer = setTimeout(() => {
  ```
- **Description:** `warningTimer` is referenced before its `const` declaration on line 68. Due to the temporal dead zone of `const`/`let`, this will throw a `ReferenceError` at runtime.
- **Fix:** Store `warningTimer` in a `useRef` instead of a local `const`.

---

## 2. Operator Errors

### 2.1 Bitwise OR `|` Instead of Logical OR `||`

- **Severity:** Critical
- **File:** `questionnaire/SelectableAreaQuestion.jsx:396`
- **Code:**
  ```jsx
  [[notApplicableOption.label | notApplicableOption.value, notApplicableOption.value]]
  ```
- **Description:** The bitwise OR operator `|` coerces strings to integers, resulting in `0 | 0 = 0` or `NaN` being sent as the answer value instead of the intended label string.
- **Fix:** Change `|` to `||`.

### 2.2 Operator Precedence Issue with Null Guard

- **Severity:** Medium
- **File:** `patient-portal/PatientIdentification.jsx:169`
- **Code:**
  ```jsx
  if (!dob.isValid || !mrn && !hc) {
  ```
- **Description:** `&&` has higher precedence than `||`, so this evaluates as `(!dob.isValid) || ((!mrn) && (!hc))`, which matches likely intent. However, `dob` can be `null` (line 111), so `!dob.isValid` would throw on `null.isValid`.
- **Fix:** Add explicit parentheses and a null guard: `if (!dob?.isValid || (!mrn && !hc))`.

---

## 3. Event Listener Leaks

### 3.1 window/document Mismatch in LiveTable

- **Severity:** High
- **File:** `dataHomepage/LiveTable.jsx:190-193`
- **Code:**
  ```jsx
  window.addEventListener("LivetableRefresh", refresh);
  return () => {
    document.removeEventListener("LivetableRefresh", refresh);  // Wrong target
  };
  ```
- **Description:** The event listener is added to `window` but removed from `document`. The listener is never properly removed, causing a memory leak.
- **Fix:** Change `document.removeEventListener` to `window.removeEventListener`.

### 3.2 window/document Mismatch in PrintButton

- **Severity:** High
- **File:** `dataHomepage/PrintButton.jsx:94-97`
- **Description:** Same window/document mismatch pattern as LiveTable for the keydown handler.
- **Fix:** Change `document.removeEventListener` to `window.removeEventListener`.

### 3.3 window/document Mismatch in AdminResourceListing

- **Severity:** High
- **File:** `adminDashboard/AdminResourceListing.jsx:80-83`
- **Description:** Same window/document mismatch. Additionally, this useEffect has NO dependency array, meaning it runs on every render.
- **Fix:** Change `document.removeEventListener` to `window.removeEventListener` and add a proper dependency array.

### 3.4 beforeunload Listener Never Removed

- **Severity:** Medium
- **File:** `patient-portal/QuestionnaireSet.jsx:321-324`
- **Code:**
  ```jsx
  window.addEventListener("beforeunload", (e) => fetch('/system/sling/logout', { "redirect": "manual" }), true);
  ```
- **Description:** Anonymous arrow function can never be removed. Every time `isSubmitted` changes to `true`, a new listener is added without removing the old one.
- **Fix:** Store the handler in a variable and return a cleanup function from useEffect.

### 3.5 Missing useEffect Dependency Array in VocabularyBranch

- **Severity:** Medium
- **File:** `vocabQuery/VocabularyBranch.jsx:95-108`
- **Description:** useEffect has no dependency array, meaning it runs on every render, constantly adding and removing event listeners.
- **Fix:** Add an appropriate dependency array, e.g., `[maxAnswers]`.

### 3.6 Missing useEffect Dependency Array in DragAndDrop

- **Severity:** Medium
- **File:** `components/DragAndDrop.jsx:111-131`
- **Description:** Listener add/remove on every render due to missing dependency array.
- **Fix:** Add `[]` dependency array.

### 3.7 Missing useEffect Dependency Array in AdminResourceListing

- **Severity:** Medium
- **File:** `adminDashboard/AdminResourceListing.jsx:78-85`
- **Description:** No dependency array causes constant listener churn (in addition to the window/document mismatch).
- **Fix:** Add appropriate dependency array.

---

## 4. React Router Incompatibility

### 4.1 `props.history.push` Incompatible with React Router v7

- **Severity:** High
- **File:** `questionnaire/Form.jsx:777`
- **Code:**
  ```jsx
  onExit={() => props.history.push("/")}
  ```
- **Description:** React Router v7 does not pass `history` as a prop. This will throw `TypeError: Cannot read properties of undefined (reading 'push')` when the user clicks to exit on session expiry.
- **Fix:** Use the `navigate` function from `useNavigate()` (already imported on line 140).

### 4.2 `<Router history={hist}>` is React Router v5 API

- **Severity:** High
- **File:** `patient-portal/index.jsx:111`, `themePage/index.jsx:179`
- **Code:**
  ```jsx
  <Router history={hist}>
  ```
- **Description:** React Router v7 uses `<BrowserRouter>` and does not accept a `history` prop. The `createBrowserHistory` pattern is the React Router v5 API.
- **Fix:** Replace with `<BrowserRouter>` from React Router v7.

---

## 5. Async/Promise Issues

### 5.1 Fire-and-Forget Checkout POST

- **Severity:** High
- **File:** `questionnaire/Questionnaire.jsx:113-116`
- **Description:** The JCR checkout operation is a POST request with absolutely no error handling. If checkout fails, the user edits a form that was never checked out, leading to data loss.
- **Fix:** Add `.then()` and `.catch()` handlers.

### 5.2 Fire-and-Forget Checkin POST

- **Severity:** High
- **File:** `questionnaire/Questionnaire.jsx:121-124`
- **Description:** The checkin POST has no error handling. If checkin fails on beforeunload, the questionnaire remains checked out, potentially blocking other users.
- **Fix:** Add error handling.

### 5.3 Missing `.catch()` in ExportButton

- **Severity:** Medium
- **File:** `dataHomepage/ExportButton.jsx:155-173`
- **Description:** Both fetches reject on non-OK responses but there is no `.catch()` handler, creating unhandled promise rejections.
- **Fix:** Add `.catch()` handlers.

### 5.4 Missing `.catch()` in SearchBar

- **Severity:** Medium
- **File:** `SearchBar.jsx:109-117`
- **Description:** Missing error handling on the config fetch.
- **Fix:** Add `.catch()` handler.

### 5.5 No AbortController Usage Anywhere

- **Severity:** Medium
- **File:** Multiple files
- **Description:** The codebase contains dozens of fetch calls within `useEffect` hooks, but zero uses of `AbortController`. LiveTable.jsx even has a `// TODO: abort previous request` comment at line 126.
- **Fix:** Implement AbortController in useEffect fetch calls.

### 5.6 DowntimeBanner Missing `response.ok` Check

- **Severity:** Medium
- **File:** `DowntimeBanner.jsx:38-39`
- **Code:**
  ```jsx
  fetch("/apps/cards/config/DowntimeWarning.deep.json")
      .then((response) => response.json())  // No response.ok check
  ```
- **Description:** If the server returns a non-JSON error, `response.json()` will throw.
- **Fix:** Add `response.ok` check.

### 5.7 PrincipalsContainer Missing `response.ok` Check

- **Severity:** Medium
- **File:** `Userboard/PrincipalsContainer.jsx:42, 57`
- **Description:** Same issue as DowntimeBanner.
- **Fix:** Add `response.ok` check.

### 5.8 Missing `.catch()` in ClinicDashboard

- **Severity:** Medium
- **File:** `patient-portal/ClinicDashboard.jsx:135-142`
- **Description:** Missing error handling on the clinic config fetch.
- **Fix:** Add `.catch()` handler.

---

## 6. Logic Errors

### 6.1 `.concat()` Result Discarded

- **Severity:** High
- **File:** `questionnaire/MultipleChoice.jsx:140`
- **Code:**
  ```jsx
  all_options.concat(["", ""]);  // Result discarded!
  const [options, setOptions] = useState(all_options);
  ```
- **Description:** `Array.concat()` returns a new array; it does not mutate the original. The empty option is never added to `all_options`, meaning multi-select questions are missing the blank/empty option.
- **Fix:** Change to `all_options = all_options.concat(["", ""]);`.

### 6.2 Stale Closure in `setSelection` Callback

- **Severity:** High
- **File:** `questionnaire/SelectableAreaQuestion.jsx:201-209`
- **Code:**
  ```jsx
  setSelection(oldSelection => {
      if (maxAnswers == 1) {
          if (selection.length == 1 && selection[0][VALUE_POS] === clickedEntry[VALUE_POS]) {
              // BUG: uses `selection` instead of `oldSelection`
  ```
- **Description:** Inside the `setSelection` callback, the code uses `selection` (the captured outer state, which may be stale) instead of `oldSelection` (the fresh value). This can cause incorrect toggle behavior.
- **Fix:** Change `selection` to `oldSelection` throughout the callback.

### 6.3 HTTP 500 Treated as Auth Failure

- **Severity:** Medium
- **File:** `login/ReLoginDialog.js:34`
- **Code:**
  ```jsx
  if (response.status == 401 || response.status == 500) {
      displayLoginCtx.dialogOpen(fetchFunc, discardOnFailure);
  }
  ```
- **Description:** HTTP 500 (Internal Server Error) triggers the re-login dialog. Users see a misleading login prompt for server errors.
- **Fix:** Remove `response.status == 500` or handle it separately with an error message.

### 6.4 Direct State Mutation via `++`

- **Severity:** Medium
- **File:** `dataHomepage/LiveTable.jsx:133`
- **Code:**
  ```jsx
  url.searchParams.set("req", ++fetchStatus.currentRequestNumber);
  ```
- **Description:** `fetchStatus` is a state object. `++fetchStatus.currentRequestNumber` directly mutates it. React will not detect this change.
- **Fix:** Use proper state setter pattern.

### 6.5 Triple Negation `!!!`

- **Severity:** Low
- **Files:** `QuestionnaireSet.jsx:376`, `Questionnaire.jsx:366`, `PrintPreview.jsx:241`, and 6 more
- **Description:** `!!!x` is equivalent to `!x`. Confusing and suggests developer confusion about truthiness.
- **Fix:** Replace `!!!x` with `!x`.

### 6.6 Data Fetching in Render Body

- **Severity:** Medium
- **File:** `dataHomepage/SubjectView.jsx:114-116`
- **Code:**
  ```jsx
  if (tabsLoading === null) {
      fetchSubjectTypes();   // Side effect during render!
      setTabsLoading(true);
  }
  ```
- **Description:** Network request called directly in the render body (not inside useEffect). This violates React's rules and can cause infinite loops in React 18+ Strict Mode.
- **Fix:** Move the fetch trigger into a `useEffect`.

### 6.7 `useEffect` with `[0]` as Dependency Array

- **Severity:** Medium
- **File:** `VocabularyActions.jsx:109`
- **Code:**
  ```jsx
  useEffect(() => {props.addSetter(setPhase);},[0]);
  ```
- **Description:** The dependency `[0]` is a constant literal. While this effectively makes it run once, it's confusing and `props.addSetter` is missing from dependencies.
- **Fix:** Change to `[]`.

---

## 7. Null/Undefined Access Issues

### 7.1 Null Dereference on Regex Match in FileQuestion

- **Severity:** High
- **File:** `questionnaire/FileQuestion.jsx:232-233`
- **Code:**
  ```jsx
  let match = response.match(uploadFinder);
  let fileURL = match[1] + "/" + file["name"];  // match could be null!
  ```
- **Description:** `String.match()` returns `null` if no match is found. If the response format changes, `match[1]` throws `TypeError`.
- **Fix:** Add a null check before accessing `match[1]`.

### 7.2 Null Dereference on Regex Match in Questionnaire

- **Severity:** High
- **File:** `questionnaire/Questionnaire.jsx:75`
- **Code:**
  ```jsx
  let baseUrl = /((.*)\/Questionnaires)\/([^.]+)/.exec(location.pathname)[1];
  ```
- **Description:** If `location.pathname` does not match, `.exec()` returns `null` and `[1]` throws.
- **Fix:** Add null checks.

### 7.3 Null Dereference on Regex Match in Form

- **Severity:** Medium
- **File:** `questionnaire/Form.jsx:125`
- **Code:**
  ```jsx
  let id = props.id || /Forms\/([^./]+)/.exec(location.pathname)[1];
  ```
- **Description:** If `props.id` is falsy and pathname doesn't match, `.exec()` returns `null`.
- **Fix:** Guard with optional chaining.

### 7.4 Potential Null Access in ResizeObserver Cleanup

- **Severity:** Medium
- **File:** `questionnaire/SelectableAreaQuestion.jsx:337`
- **Description:** `questionRef.current` may be `null` when cleanup runs during unmount.
- **Fix:** Use `observer.disconnect()` in cleanup instead.

---

## 8. State Management Bugs

### 8.1 Direct Mutation of State Object

- **Severity:** High
- **File:** `questionnaire/SelectableAreaQuestion.jsx:245, 253`
- **Code:**
  ```jsx
  mapEntry.isHovered = true;   // Direct state mutation!
  mapEntry.isHovered = false;  // Direct state mutation!
  ```
- **Description:** `mapEntry` is an element of the `map` state array. Directly modifying it bypasses React's state management. React will not detect this change and may not re-render.
- **Fix:** Create a new copy of the map entry with the updated value and call `setMap`.

### 8.2 State Mutation During Render

- **Severity:** Medium
- **File:** `dataHomepage/SubjectView.jsx:100-102`
- **Description:** `setColumns` called inside `fetchSubjectTypes` which is called during render (see 6.6). Calling state setters during render is problematic.
- **Fix:** Move this logic into a useEffect.

---

## 9. Type Coercion Bugs

### 9.1 Loose Equality with String Comparison

- **Severity:** Medium
- **File:** `SearchBar.jsx:115`
- **Code:**
  ```jsx
  setShowTotalRows(json["showTotalRows"] == 'true');
  ```
- **Description:** Using loose equality `==` to compare with the string `'true'`. Fragile if the server returns a boolean.
- **Fix:** Use `=== 'true'` or handle both string and boolean.

### 9.2 Pervasive Loose Equality

- **Severity:** Low
- **Files:** Multiple instances across PatientIdentification, QuestionnaireSet, FormContext, ReLoginDialog, and many more.
- **Description:** The codebase uses `==` rather than `===` in many comparisons, risking unexpected type coercion.
- **Fix:** Replace `==` with `===` throughout.
