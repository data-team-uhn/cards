# Code Duplication Analysis Report

**Codebase:** `aggregated-frontend/src/main/frontend/src/`
**Application:** CARDS -- Medical data collection platform (React 19, MUI 7, React Router 7)
**Date:** 2026-02-05
**Total Findings:** 14 (covering 200+ duplication points across 40+ files)

## Impact Summary

| Impact | Count | Est. Duplication Points |
|--------|-------|------------------------|
| HIGH | 4 | ~150 |
| MEDIUM | 6 | ~45 |
| LOW | 4 | ~20 |

---

## 1. Repeated Fetch Response Handling Pattern

- **Impact:** HIGH
- **Occurrences:** 77 (72 JSON + 5 text) across 40 files
- **Code:**
  ```javascript
  .then(response => response.ok ? response.json() : Promise.reject(response))
  ```
- **Files (sampling):**
  - `assetManager.jsx:44, 63, 151`
  - `SearchBar.jsx:111, 151`
  - `dataHomepage/LiveTable.jsx:156`
  - `dataHomepage/ExportButton.jsx:158, 168`
  - `dataHomepage/Filters.jsx:123`
  - `questionnaire/Subject.jsx` (4 occurrences)
  - `patient-portal/QuestionnaireSet.jsx` (5 occurrences)
  - ...and 30+ more files
- **Suggested Fix:** Create utility functions:
  ```javascript
  export const parseJsonResponse = (response) =>
    response.ok ? response.json() : Promise.reject(response);
  export const parseTextResponse = (response) =>
    response.ok ? response.text() : Promise.reject(response);
  ```

---

## 2. Duplicated Loading Spinner Pattern

- **Impact:** HIGH
- **Occurrences:** 32 total (9 exact matches, 23 variants)
- **Code:**
  ```jsx
  <Grid container justifyContent="center"><Grid><CircularProgress/></Grid></Grid>
  ```
- **Files:**
  - `questionnaire/Form.jsx:455`
  - `questionnaire/Subject.jsx:229, 463, 749`
  - `questionnaire/Questionnaire.jsx:380`
  - `questionnaire/QuestionnairePreview.jsx:70`
  - `patient-portal/ClinicDashboard.jsx:183`
  - `dataHomepage/UserDashboard.jsx:84`
  - `adminDashboard/AdminDashboard.jsx:57`
- **Suggested Fix:** Create a `<CenteredSpinner />` component.

---

## 3. Duplicated `useContext(GlobalLoginContext)` Boilerplate

- **Impact:** HIGH
- **Occurrences:** 47 across 43 files
- **Code:**
  ```jsx
  const globalLoginDisplay = useContext(GlobalLoginContext);
  // ...later:
  fetchWithReLogin(globalLoginDisplay, url, options)
  ```
- **Files (sampling):**
  - `SearchBar.jsx:101`
  - `questionnaire/Form.jsx:175`
  - `questionnaire/Subject.jsx:196, 275, 421, 725` (4 sub-components)
  - `questionnaire/SubjectSelector.jsx:103, 288, 480, 782, 1022` (5 sub-components)
  - ...and 38 more files
- **Suggested Fix:** Create a `useAuthFetch()` custom hook:
  ```javascript
  export function useAuthFetch() {
    const ctx = useContext(GlobalLoginContext);
    return useCallback((url, opts) => fetchWithReLogin(ctx, url, opts), [ctx]);
  }
  ```
  This would eliminate both the context boilerplate AND the fetchWithReLogin call pattern simultaneously.

---

## 4. Repeated `capitalize` / String Case Transformation

- **Impact:** MEDIUM
- **Occurrences:** 7 across 6 files
- **Code:**
  ```javascript
  text.charAt(0).toUpperCase() + text.slice(1)
  ```
- **Files:**
  - `dataHomepage/DeleteButton.jsx:114-116` (local function)
  - `questionnaireEditor/NumberInput.jsx:35` (inline)
  - `questionnaireEditor/LabeledField.jsx:36` (exported `camelCaseToWords`)
  - `questionnaire/QuestionMatrix.jsx:81` (inline)
  - `questionnaire/TextQuestion.jsx:64` (inline)
  - `pedigree/view/person.js:123, 145` (inline)
- **Suggested Fix:** Add a `capitalize` utility to the existing `escape.jsx` file and import everywhere needed.

---

## 5. Monolithic `QuestionnaireStyle` Object

- **Impact:** HIGH
- **Occurrences:** 45 components import it via `withStyles`
- **File:** `questionnaire/QuestionnaireStyle.jsx` (747 lines)
- **Description:** A single massive style object is applied to 45 different components. Each component receives ALL styles but typically uses only 1-3.
- **Internal duplication:**
  - `headerSection` (lines 407-429) and `footerSection` (lines 430-451) are nearly identical, differing only in `position: sticky; top: 0` vs `bottom: 0`
  - `subjectFilterInput: { width: "100%" }` appears in both `QuestionnaireStyle.jsx:521` and `statisticsStyle.jsx:36`
- **Suggested Fix:** Split into domain-specific style modules (`questionCardStyles`, `subjectStyles`, `timelineStyles`, `formPaginationStyles`). Extract header/footer into a factory function parameterized by `top`/`bottom`.

---

## 6. Near-Duplicate Filter Components

- **Impact:** MEDIUM
- **Occurrences:** 6 components
- **Files:**
  - `dataHomepage/FilterComponents/BooleanFilter.jsx` (93 lines)
  - `dataHomepage/FilterComponents/ListFilter.jsx` (101 lines)
  - `dataHomepage/FilterComponents/NumericFilter.jsx` (88 lines)
  - `dataHomepage/FilterComponents/TextFilter.jsx` (97 lines)
  - `dataHomepage/FilterComponents/DateFilter.jsx` (106 lines)
  - `dataHomepage/FilterComponents/VocabularyFilter.jsx` (79 lines)
- **Description:** All six follow the same pattern: identical imports, identical propTypes, identical comparator setup, identical `withStyles` wrapping, identical `FilterComponentManager.registerFilterComponent()` registration boilerplate. Only the render body differs.
- **Suggested Fix:** Create a `createFilter(renderFn, { comparators, priority, matchFn })` higher-order function.

---

## 7. Repeated `new URL(..., window.location.origin)` Pattern

- **Impact:** MEDIUM
- **Occurrences:** 48+ across 25+ files (plus 15+ using direct string concatenation)
- **Files (sampling):**
  - `dataHomepage/DeleteButton.jsx:145`
  - `dataHomepage/LiveTable.jsx:99, 101, 129`
  - `questionnaire/SubjectSelector.jsx:46, 794`
  - `dataHomepage/SubjectView.jsx:93`
  - `dataHomepage/Filters.jsx:120`
  - `questionnaireEditor/ReferenceInput.jsx:63, 175, 179, 186, 207`
- **Suggested Fix:** Create a `buildUrl(path, params)` utility.

---

## 8. Duplicated JQL Query Building Patterns

- **Impact:** MEDIUM
- **Occurrences:** 9+ across 9+ files
- **Code:**
  ```javascript
  '/query?query=' + encodeURIComponent('select * from [cards:' + entryType + ']')
  ```
- **Files:**
  - `VocabulariesAdminPage.jsx:79`
  - `adminDashboard/AdminResourceListing.jsx:45`
  - `questionnaireEditor/ListInput.jsx:49`
  - `dataHomepage/NewFormDialog.jsx:147, 220`
  - `VocabularyAction.jsx:103`
  - `Statistics/UserStatistics.jsx:47`
- **Suggested Fix:** Create a `buildQueryUrl(jql, options)` utility.

---

## 9. Duplicated Logout URL Pattern

- **Impact:** LOW
- **Occurrences:** 3 across 2 files
- **Code:**
  ```javascript
  '/system/sling/logout?resource=' + encodeURIComponent(window.location.pathname)
  ```
- **Files:**
  - `patient-portal/PatientIdentification.jsx:269, 279`
  - `patient-portal/Header.jsx:155`
- **Suggested Fix:** Create a `getLogoutUrl()` utility function.

---

## 10. Pedigree Legend Near-Duplicate Classes

- **Impact:** MEDIUM
- **Occurrences:** 3 files
- **Files:**
  - `pedigree/view/disorderLegend.js` (172 lines)
  - `pedigree/view/geneLegend.js` (91 lines)
  - `pedigree/view/hpoLegend.js` (102 lines)
- **Description:** All three extend `Legend` and duplicate: `_getPrefix()`, `_generateElement()`, `_generateColor()`, cache patterns with `hasOwnProperty` checks, and `addCase()` override. The `_generateColor` methods share the same Raphael color generation logic with only different preferred color arrays.
- **Suggested Fix:** Move shared logic into the `Legend` base class, parameterize the prefix, event name, and preferred colors.

---

## 11. Duplicated Admin Configuration Screen Pattern

- **Impact:** MEDIUM
- **Occurrences:** 4 files
- **Files:**
  - `patient-portal/SurveyInstructionsConfiguration.jsx`
  - `patient-portal/PatientAccessConfiguration.jsx`
  - `patient-portal/ToUConfiguration.jsx`
  - `patient-portal/DashboardSettingsConfiguration.jsx`
- **Description:** All four share: identical `hasChanges` state, identical `buildConfigData` function iterating over config keys, identical `AdminConfigScreen` props pattern, and lists of `<ListItem>` with `<TextField>` or `<Checkbox>` based on field type.
- **Suggested Fix:** Create a declarative config screen component that accepts a schema of fields and generates the form automatically.

---

## 12. Repeated Dialog Open/Close State Management

- **Impact:** MEDIUM
- **Occurrences:** 7+ across 7+ files
- **Code:**
  ```jsx
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  let openDialog = () => { if (!open) setOpen(true); }
  let closeDialog = () => { if (open) setOpen(false); }
  ```
- **Files:**
  - `dataHomepage/DeleteButton.jsx:52-91`
  - `locking/SubjectLockAction.jsx:65`
  - `dataHomepage/ExportButton.jsx:116`
  - `dataHomepage/UserDashboard.jsx:61`
  - `Statistics/AdminStatistics.jsx:72`
  - `Userboard/Groups/CreateGroupDialog.jsx:40-66`
  - `Userboard/Users/ChangeUserPasswordDialog.jsx:148-199`
- **Suggested Fix:** Create a `useDialog()` custom hook.

---

## 13. Duplicated Close Button Styles in Dialog Components

- **Impact:** LOW
- **Occurrences:** 2 files
- **Files:**
  - `components/ErrorDialog.jsx:37-41`
  - `components/ResponsiveDialog.jsx:67-71`
- **Code (identical in both):**
  ```javascript
  closeButton: {
    position: 'absolute',
    right: theme.spacing(1),
    top: theme.spacing(1),
  },
  ```
- **Suggested Fix:** Extract a `DialogCloseButton` component with shared styles.

---

## 14. Color Box Shadow Template Duplication in themeStyle.jsx

- **Impact:** LOW
- **Occurrences:** 14 definitions in 1 file
- **File:** `themeStyle.jsx:103-194`
- **Description:** 7 nearly identical box shadow objects and 7 nearly identical card header objects, each differing only in the color value.
- **Suggested Fix:** Create factory functions:
  ```javascript
  const makeBoxShadow = (color) => ({
    boxShadow: `0 4px 20px 0 rgba(${hexToRgb(blackColor)},.14), 0 7px 10px -5px rgba(${hexToRgb(color)},.4)`
  });
  ```

---

## Highest-ROI Refactorings

1. **Create `useAuthFetch()` hook** -- eliminates findings 1 and 3 simultaneously (120+ duplication points across 43+ files)
2. **Create `<CenteredSpinner />` component** -- simple extraction, eliminates 32 duplication points
3. **Split `QuestionnaireStyle.jsx`** -- the 747-line monolith applied to 45 components is the biggest maintainability concern
4. **Create URL/query builder utilities** -- eliminates findings 7 and 8 (57+ duplication points)
