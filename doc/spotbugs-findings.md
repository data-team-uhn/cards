# SpotBugs findings

Enabling `spotbugs-maven-plugin` on the root pom surfaced **417 findings** across 32 modules and 33 bug
patterns. This document records what was fixed, what was deliberately suppressed and why, and what is
left as a backlog.

The suppressions themselves live in [`spotbugs-exclude.xml`](../spotbugs-exclude.xml) at the root of the
repository, each with its rationale inline. That file is the source of truth; this document is the
narrative around it.

## Summary

| Outcome | Findings |
| --- | ---: |
| Fixed here | 56 |
| Fixed by `CARDS-2832`, which this branch is based on | 6 |
| Suppressed - pattern does not apply to this codebase | 355 |
| **Total** | **417** |

This branch is based on `CARDS-2832-datetimeformatter`, so the date-format findings are genuinely fixed
rather than suppressed. Nothing in `spotbugs-exclude.xml` is temporary.

Of the 417, 12 were rank 12 or better on SpotBugs' 1-20 scale (1 is the scariest). Eleven of those are
fixed. The twelfth is a rank-11
`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` in `DeleteServlet`, which falls inside the suppressed
nullable-dereference backlog described at the end of this document - it is the single highest-ranked
finding this change leaves unaddressed, and is worth looking at first when that backlog is picked up.

## What was fixed

### Genuine bugs

These change behaviour. They are worth reviewing individually.

| Bug | Where | What was wrong |
| --- | --- | --- |
| `EC_UNRELATED_TYPES_USING_POINTER_EQUALITY` (rank 1) | `PatientLocalStorage.updateVisitInformationForm` | `nameObj == JsonValue.NULL` compared a `JsonObject` against a `JsonValueImpl`, so the guard against a JSON `null` could never fire. Worse, it was unreachable: `getJsonObject("name")` casts, so a JSON `null` throws `ClassCastException` before the comparison. Now reads the raw `JsonValue` and tests its `ValueType`. |
| `RC_REF_COMPARISON_BAD_PRACTICE_BOOLEAN` | `AnswerValidator.removeIfNotExplicitlySet` | `flags.getOrDefault(flag, TRUE) == Boolean.FALSE` compared `Boolean` by identity. Works only for values that happen to come from the `valueOf` cache. Replaced with `Boolean.FALSE.equals(...)`. |
| `NP_NULL_PARAM_DEREF` | `FileUploadIndexer.index` | With no uploaded file, `description` was set to `null` and then dereferenced - an unhandled NPE in a method that otherwise reports missing parameters cleanly. A missing upload is now rejected with a `VocabularyIndexException` like the other mandatory parameters. |
| `ICAST_INTEGER_MULTIPLY_CAST_TO_LONG` | `S3DataStore.getPartSize` | `size * 1024 * 1024` was `int` arithmetic widened to `long`, overflowing for a configured chunk size of 2048 MB or more. |
| `NP_NULL_ON_SOME_PATH` | `StatisticQueryServlet.getFormNode` | The loop above it exits on `answerParent == null`, then `answerParent.getDepth()` dereferences it. |
| `NP_NULL_ON_SOME_PATH_EXCEPTION` x2 | `OwlParser.parse`, `NCITOWLIndexer.parseNCIT` | `finally` blocks call `temporaryDatasetPath.toFile()`, but the path is still `null` if `createTempDirectory` threw - masking the original exception with an NPE. |
| `AT_STALE_THREAD_WRITE_OF_PRIMITIVE`, `AT_NONATOMIC_OPERATIONS_ON_SHARED_VARIABLE` x4 | `ClarityImportTask` | `discardedVisits` and `importedVisits` were plain `int` fields on a shared scheduled task, while every other piece of per-run state in the class is already a `ThreadLocal`. Two concurrent runs raced. Converted to `ThreadLocal` and added to `cleanupState()`. |
| `AT_STALE_THREAD_WRITE_OF_PRIMITIVE` | `DataMigratorManager` | `activated` is written during activation and read by the threads binding late migrators. Now `volatile`. |
| `ODR_OPEN_DATABASE_RESOURCE`, `OBL_UNSATISFIED_OBLIGATION` x2 | `ClarityImportTask.run` | The `PreparedStatement` and `ResultSet` were never closed. Moved into try-with-resources, extracted to `importVisits` to keep nesting within the checkstyle limit. |
| `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` x2 | `FilesystemDataStore.store` | `mkdirs()` and `createNewFile()` return values were discarded, so a failure to create the target directory surfaced later as a confusing `FileNotFoundException`. Now throws a clear `IOException`; the redundant `createNewFile()` is gone since `FileOutputStream` creates the file. |
| `IT_NO_SUCH_ELEMENT` x2 | `QueryBuilder$EmptyIterator`, `AppointmentUtils$EmptyNodeIterator` | `next()` returned `null` instead of throwing `NoSuchElementException`, breaking the `Iterator` contract. The sibling `nextRow()`/`nextNode()` methods had the same problem and were fixed alongside. |
| `RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE` | `PeriodicExportManager.configRemoved` | The catch block guarded against a `removedConfig` that had already been dereferenced on the line above, and never logged the exception it caught. Simplified and now logs `e`. |
| `RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE` | `SurveyEventsFormListener.handleNonSurveyEventsForm` | **Behaviour change on legacy data, see below.** An unconditional `break` made the "most recently created" date comparison dead code. Removed the `break`, and rewrote the stale comment that made the tiebreak look like the primary selection mechanism. |
| `BC_VACUOUS_INSTANCEOF` | `AbstractTokenAuthenticationHandler.getTokenExpirationDate` | `TokenManager.parse` already returns `CardsToken`, so the `instanceof CardsToken` check and the cast were dead. |

#### The SurveyEventsFormListener `break`, in detail

This is the only behaviour change in the branch, and the history is worth recording because the code's own
comment pointed the wrong way.

`588ce99b6f1` ("Improve logic to fix potential issues with links being created on unintended nodes")
introduced the exact-identification mechanism: match the Survey Events form whose own `assigned_survey`
clinic contains the questionnaire being linked. Before that commit there was no clinic filter at all - every
Survey Events form on the visit matched - so "keep the most recently created one" was the only way to
disambiguate.

That same commit added the `break`, which is consistent with the new mechanism: if the clinic match is exact,
the first match is the right one. But it left the older comment and the older `getCreatedDate` comparison in
place, so the file ended up describing a most-recent-wins policy that the `break` had made unreachable.

The `break` is nevertheless not quite right. Forms created *before* the exact-identification mechanism can
still have several Survey Events forms on one visit whose clinics all contain the questionnaire, and there
`break` takes whichever `getReferences()` yields first - arbitrary iteration order, not the most recent. So
the tiebreak still has a job on exactly that legacy data.

Resolution: drop the `break`, keep the comparison, and rewrite the comment to say that the clinic match is
primary and the date is a legacy tiebreak. Behaviour is identical to the `break` version whenever the clinic
match is unique, which is the normal case now; it differs only on pre-`588ce99b6f1` data, where it becomes
deterministic instead of arbitrary. The cost is draining `visitReferences` on every non-Survey-Events form
creation, bounded by the number of forms on a single visit.

### Mechanical cleanups

No behaviour change intended.

- `WMI_WRONG_MAP_ITERATOR` x8 - `keySet()` iteration followed by `get(key)`, in `FilterServlet`,
  `PaginationServlet`, `SearchUtils`, `StatisticQueryServlet`, `VocabularyTermInfoServlet`. Replaced with
  `entrySet()` streams or `Map.forEach`.
- `DM_BOXED_PRIMITIVE_FOR_PARSING` x3 - `Long.valueOf`/`Double.valueOf` where a primitive was wanted
  (`DataImportServlet`, `UnsubscribeServlet`).
- `BX_UNBOXING_IMMEDIATELY_REBOXED` - `MinMaxValueValidator`.
- `SBSC_USE_STRINGBUFFER_CONCATENATION` x2 - string `+=` in a loop, in `ConditionalSectionUtils` and
  `ModifiedFileAnswersRetriever` (the latter became a `Collectors.joining`, which also removed a
  trailing-`OR`-strip hack).
- `SE_COMPARATOR_SHOULD_BE_SERIALIZABLE` x2 - `DeleteServlet$NodeComparator` and
  `AbstractFormToStringSerializer$DefinitionComparator`. The latter also became `static`, since it used no
  outer state.
- `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` x3 - `SearchUtils.getMatch`, `TermsOfUseServlet.doPost`,
  `EmailAlertEventListener.answerMatchesExpression`.
- `MS_MUTABLE_COLLECTION_PKGPROTECT` - `DateUtils.DATETIME_FORMATS`, see the CARDS-2832 section below.
- `DE_MIGHT_IGNORE` x2 - empty catch blocks in `ComputedAnswersEditor` and `BareFormProcessor`, both
  swallowing "the user can't see this node". Now logged at debug.
- `REC_CATCH_EXCEPTION` - `GoogleApiKeyServlet.doGet` was wrapping both the writer and the generator in
  try-with-resources and then swallowing every exception from the resulting double close. Only the
  generator is managed now, and the catch-all is gone, so a genuine failure surfaces instead of returning
  truncated JSON.
- `VA_FORMAT_STRING_USES_NEWLINE` - `SelectorServlet.writeMarkdownDetails`. The `\n` is deliberate
  (markdown over HTTP, not platform line endings), so it moved out of the format string rather than
  becoming `%n`.
- `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` x10 - defensive copies for the mutable value objects in exported
  `api`/`spi` packages: `VocabularyTermSource` (`parents`, `ancestors`), `SelectorDetails` (`options`),
  and the `Calendar` getters on `CardsTokenImpl`, `CardsJwtTokenImpl`, `QuestionnaireSetUtilsImpl` and
  `VisitInformationAdapterImpl`. This matches how the same findings were handled in IAP.

## What was suppressed, and why

Full rationale is inline in `spotbugs-exclude.xml`. In brief:

| Pattern | Count | Why it does not apply |
| --- | ---: | --- |
| `SE_BAD_FIELD` | 75 | Sling servlets and endpoints extend `GenericServlet`, which implements `Serializable`, so every `@Reference` and `ThreadLocal` they hold gets flagged. They are container-managed singletons that are never serialized, and marking the service references `transient` would break injection. |
| `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` | 65 remaining | Overwhelmingly live repository handles - `javax.jcr.Node`, `Session`, Oak `Tree` and `NodeBuilder`, `ResourceResolver` - which are shared by reference by design and cannot be meaningfully copied. The rest is internal state of commit-time `Editor`s, where copying on every repository write would cost more than it protects in a single-trust-domain application. The exported value objects were fixed instead, see above. |
| `CT_CONSTRUCTOR_THROW` | 5 | The stated risk is a finalizer attack. None of these classes override `finalize()`, and finalization has been deprecated for removal since Java 18. |
| `DCN_NULLPOINTER_EXCEPTION` | 11 | Catching NPE as a terse alternative to null-checking every step of a long JSON or JCR navigation chain. Worth revisiting when those helpers are next touched, but restructuring them purely to satisfy the checker is churn. |
| `REC_CATCH_EXCEPTION` | 1 | `EmailAlertEventListener.onEvent` - SpotBugs is simply wrong here. The guarded block calls methods declaring `LoginException` and `RepositoryException`; narrowing the catch does not compile. |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | 198 | A backlog, not a decision. See below. |

### The date-format findings, fixed by CARDS-2832

Seven findings - 5 `STCAL_*` (rank 8 and 12, genuine thread-safety bugs: static `SimpleDateFormat`
instances shared across request threads) and 2 `MS_MUTABLE_COLLECTION_PKGPROTECT` - live in
`DateUtils`, `CountServlet` and `DiscardExistingVisitsFilter`.

Rather than write that fix a second time, this branch is **based on `CARDS-2832-datetimeformatter`**, which
replaces every one of them with an immutable `DateTimeFormatter`. So they are fixed, not suppressed.

Six of the seven, that is. CARDS-2832 deletes `DateUtils.CALENDAR_FORMATS` outright but leaves
`DATETIME_FORMATS` a `public static final` list built with `Arrays.asList`, which is only fixed-*size* -
callers can still replace individual entries and change how every date in the application is parsed. That
one is wrapped in `Collections.unmodifiableList` here.

Two consequences for whoever merges this:

- **CARDS-2832 has to land first, or land as part of this.** The branch history is
  `dev` -> CARDS-2832 -> enable SpotBugs -> these fixes.
- CARDS-2832 was originally cut from an older `dev` (`959b37bf9`, 21 commits behind). It was replayed onto
  `b6e3ef1c9` - the commit the SpotBugs branch started from - before this work was rebased on top, so no
  `dev` history is lost. That replay was itself conflict-free.

The one place the two changes actually collided was `ClarityImportTask`: CARDS-2832 changed
`catch (ParseException | ...)` to `catch (DateTimeParseException | ...)` in the row-import loop, while this
work extracted that loop into `importVisits` to keep the new try-with-resources within the checkstyle
nested-try limit. The resolution keeps both.

## The remaining backlog: 198 unchecked nullable dereferences

Every one has the same shape: a Sling or Oak API annotated `@Nullable` is dereferenced without a null
check.

| Called method | Findings |
| --- | ---: |
| (inferred from a CARDS method that can return null) | 75 |
| `NodeBuilder.getProperty` | 23 |
| `ResourceResolver.getResource` | 22 |
| `Resource.adaptTo` | 20 |
| `Tree.getProperty` | 16 |
| `ResourceResolver.adaptTo` | 12 |
| `NodeState.getProperty` | 11 |
| `ResourceMetadata.getResolutionPathInfo` | 7 |
| `ResourceResolverFactory.getThreadResourceResolver` | 4 |
| others | 8 |

Most are safe in context - the resource was just created, or a caller already validated it - but some are
genuine unguarded dereferences. Telling them apart needs a judgement call per call site, across 98 files,
and a blanket `Objects.requireNonNull` sweep would add noise without changing any outcome. So this is
suppressed as a whole and left as tracked work.

Note that the higher-confidence members of the same family are **not** suppressed:
`NP_NULL_ON_SOME_PATH`, `NP_NULL_PARAM_DEREF` and `NP_NULL_ON_SOME_PATH_EXCEPTION` still fail the build,
and the four instances that existed were real bugs (fixed, see above).

### Suggested burn-down order

Highest concentration first, since these are the files where a null dereference is most likely to be a
real reachable path rather than a provably-safe one:

| Module | Findings | Worst files |
| --- | ---: | --- |
| `data-model/forms/impl` | 41 | `QuestionMatrixEditor` (13) |
| `patient-portal` | 39 | `ClinicsServlet`, `TermsOfUseServlet`, `ClinicDataFilterFactory`, `UnsubmittedFormsCleanupTask` (4 each) |
| `data-entry` | 20 | `DataImportServlet` (9) |
| `torch-import` | 14 | `PatientLocalStorage` (13) |
| `locking` | 11 | `LockManagerImpl` (6) |
| `permissions` | 11 | spread across 8 restriction patterns |
| `clarity-integration` | 9 | `ClarityImportTask` (6) |
| `form-completion-status` | 7 | |
| `data-model/subjects/impl`, `vocabularies` | 6 each | |
| `export`, `versioning` | 5 each | |
| 12 further modules | 1-3 each | |

### The real long-term fix

SpotBugs' null analysis is largely dormant without nullability annotations, and CARDS currently uses none
(`@Nullable`/`@NotNull`/`@Nonnull`: zero occurrences). These 198 findings exist only because the Sling API
itself is annotated with `org.jetbrains.annotations`.

Annotating CARDS' own API - starting with the getters whose javadoc already says "or null if not set" -
would let SpotBugs find the null bugs that matter inside CARDS rather than only at the framework
boundary. Use `org.jetbrains.annotations`, not jsr305: measured against SpotBugs 4.10.3,
`org.jetbrains.@Nullable` and `javax.annotation.@CheckForNull` are both honoured, but
`javax.annotation.@Nullable` is silently ignored - the spelling you would reach for first is the one that
does nothing. It is also `CLASS`-retention, so bnd adds no `Import-Package` for it.

## Notes on the configuration

- The exclude file is located with `${maven.multiModuleProjectDirectory}`, which needs a `.mvn` directory
  to be pinned to the reactor root - otherwise it silently resolves to whichever directory `mvn` was
  invoked from. That is what the new [`.mvn/README.md`](../.mvn/README.md) is for.
- `spotbugs:check` fails the module it runs in, and the reactor is fail-fast, so enabling it without an
  exclude file stops the build at the first offending module and never analyses the rest. Producing a full
  inventory needs `-Dspotbugs.failOnError=false` and then parsing `*/target/spotbugsXml.xml`; in that mode
  the per-finding lines are not logged at `ERROR` level, so grepping the build log finds nothing.
- Analysis costs roughly 3.5 s per Java module, and the plugin also runs on `pom`-packaging modules
  (harmless, about 0.3 s each).
- `includeTests` defaults to false, so test code is not analysed. The default threshold also hides
  Low-priority findings; `-Dspotbugs.threshold=Low` surfaces more.

## Adjacent issues noticed but not fixed

Out of scope for this change, but worth filing:

- `ModifiedFileAnswersRetriever.getResourcesToExport` logs every generated query at **ERROR** level via an
  inline `LoggerFactory.getLogger(...).error("Query: {}", query)`. That looks like a debugging leftover.
- `UnsubscribeServlet.doPost` has the same `answer == null && form != null` shape that was flagged as
  redundant in `TermsOfUseServlet`, but without the preceding null check - so there the form really can be
  null and `unsubscribeAnswer.setProperty` on the next line would NPE.
