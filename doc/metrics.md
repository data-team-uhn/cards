# Metrics

Named counters kept in the repository — emails sent, appointments imported, surveys submitted — so counts survive
restarts and are shared by every node of a cluster. They live in `modules/metrics`, behind the
`io.uhndata.cards.metrics.api` API (`MetricsManager`, `Metric`).

## Recording

Inject `MetricsManager`, define the metric at activation (defining is idempotent), then count through the returned
handle:

```java
@Reference
private MetricsManager metricsManager;

this.metric = this.metricsManager.createMetric("submittedSurveys")
    .withLabel("Submitted surveys")
    .withDescription("How many surveys patients submitted")
    .withCategory("Surveys")
    .withDefaultOrder(10)                        // placement within the category; 0 if unset
    .withAccessLevel(Metric.AccessLevel.ADMIN)   // PUBLIC unless restricted
    .withRolloverSchedule(MetricsManager.END_OF_DAY) // closes a period every day; manual-only if unset
    .create();

// when the counted event happens
this.metric.increment();
```

Code that is told what to count by its configuration counts **by name** instead:
`metricsManager.increment(name, amount)` adds to the metric with that name if one is defined, counts nothing
otherwise, and never throws. This is how the platform's configurable counters work:

- every `AppointmentEmailNotificationsFactory` configuration defines a metric named after its `name`, labeled with its
  `metricName`, and counts the emails each run sends;
- a Clarity mapping's `incrementMetricOnCreation` names a metric to count each subject it creates, which something
  else has to define — the patient portal defines `ImportedAppointments`;
- `SubmissionCounter` counts `AppointmentSurveysSubmitted` and `TotalSurveysSubmitted`, also defined by the patient
  portal.

Labels in configurations may start with a number in braces, `{007} Initial Emails Sent`, which orders the metric:
`withNumberedLabel` reads that form into the label `Initial Emails Sent` and the order `7`.

The manager only defines (`createMetric(name)…create()`), looks up (`getMetric`, `getMetrics`) and counts by name.
Everything else is on the handle:

```java
long          getCurrentValue();     // ever-growing total
long          getPreviousValue();    // the value at the last roll-over
long          getCurrentDelta();     // current − previous: this period so far
long          getLastDelta();        // the period that was closed
ZonedDateTime getLastUpdated();
ZonedDateTime getLastRollover();
String        getRolloverSchedule();
void          increment();           // + increment(long amount)
void          rollOver();
```

### Handles and staleness

A handle asked for **by name** — from `create()` or `getMetric(name)` — reads the repository on every access, so it
never goes stale and can be held for a component's lifetime.

A handle from **`getMetrics()`** reports the values read while listing, because a listing is a point-in-time view:
reading each value separately would cost a repository session each, and rendering ten metrics would open seventy
sessions instead of one.

Either kind *writes* correctly. A roll-over closes the period against the counter's current value, never against the
value its handle happens to be reporting.

### Behaviours to count on

- **Counting never throws.** A failure to count is logged and swallowed, so recording can never break the operation
  being counted. Everything else — `rollOver`, reads, `create` — reports failure as an unchecked `MetricsException`.
- **Increments may be negative**, to correct over-counting; incrementing by `0` does nothing.
- **Reading never rolls over.** Every accessor is a plain read with no side effects, so any number of reports,
  dashboards and probes can look without influencing each other.
- **Writes retry.** An update losing a race against a concurrent change is retried on a fresh session a few times
  before raising `MetricsException`.
- **Names are node names.** Anything usable as a single node name works, which is what configurations have always
  used; `createMetric` refuses the rest.

## Storage

Each metric is a `cards:Metric` under `/Metrics`, a `cards:MetricsHomepage`:

| Property | Holds |
|---|---|
| `label` | **Mandatory.** Display name |
| `description`, `category` | Descriptive metadata; a blank category is no category |
| `cards:defaultOrder` | LONG, default `0`, negatives allowed |
| `accessLevel` | `public` (default) or `admin` — constrained by the node type |
| `oak:counter` | The count itself |
| `previousValue` | The counter at the last roll-over — the baseline for "this period" |
| `lastDelta` | The amount accumulated in the period that was closed |
| `lastRollover`, `lastUpdated` | When those happened |
| `rolloverSchedule` | Quartz cron expression, if periods close automatically |

The count is maintained by Oak's atomic counter support: committing an `oak:increment` value adds it to `oak:counter`
atomically, so concurrent increments from different threads or cluster nodes all apply without conflicting or getting
lost.

**`mix:atomicCounter` is not part of the node type.** It only works when listed explicitly in a node's
`jcr:mixinTypes`, so the manager sets it at creation rather than declaring it on the type — and gives it back to a
metric that lost it, since without it the counter silently never counts.

Only the `cards-metrics` service user can reach the metric nodes. Everything else goes through the services above,
which enforce `accessLevel` at the HTTP boundary.

## Roll-overs

`rollOver()` closes the current period: the amount accumulated so far is frozen as `lastDelta`, the current value
becomes the new baseline, and the time is recorded. **The current value is never modified** — the counter keeps growing
across roll-overs, and the period deltas always sum to it.

Metrics declaring a `rolloverSchedule` are rolled over by `MetricRolloverScheduler`, which keeps one job per metric.
The jobs are scheduled with `onLeaderOnly(true)` and `canRunConcurrently(false)`, so in a cluster each period is closed
exactly once. Schedules are re-read whenever anything under `/Metrics` changes, so new metrics and edited expressions
are picked up without a restart. An expression the scheduler refuses is logged and recorded through error tracking: a
metric that never rolls over keeps reporting its first period forever.

The platform's own metrics close their periods to suit the daily status report:

- `MetricsManager.END_OF_DAY`, **23:59**, for the emails sent and the surveys submitted: a minute before midnight, the
  default time of the report, so that the report always sees the day that just ended instead of racing the roll-over;
- **08:55** for the imported appointments, just before a 9 AM report, so that it shows whether that night's imports did
  their job.

Both are the defaults of the `PatientPortalMetrics` configuration (`surveysRolloverSchedule`,
`importsRolloverSchedule`), for deployments whose report goes out at another time.

## Display order

`getMetrics()` returns metrics in the order they should be shown, and everything listing them — the endpoint, the
status report — follows that order rather than sorting again:

1. by **category**, alphabetically, **uncategorized last**;
2. by **`cards:defaultOrder`** within the category, lower first;
3. by **name**, so a listing is stable when the first two agree.

Category is compared first, so a low order never lifts a metric out of its group.

## Reading

`GET /Metrics.json` returns `{ "metrics": [ … ] }` in display order, each entry holding `name`, `label`, `description`,
`category`, `defaultOrder`, `accessLevel`, `currentValue`, `previousValue`, `currentDelta`, `lastDelta`, the
`lastUpdated`/`lastRollover` dates, and `rolloverSchedule` when set. The endpoint is deliberately reachable without
authentication — the servlet registers `sling.auth.requirements=-/Metrics` — so dashboards can poll it. Metrics with
the `admin` access level are listed only when the administrator is asking.

Metrics also appear in the status reports, `/system/status` and the Slack notifications: the **Metrics** reporter
(tags `metrics`, `activity`) lists them as an `INFO` report, one line per label, with the count of the last closed
period named after the day it closed on, relative to the report:

```
*Imported Appointments* -- _Today_: 42, _Total_: 12345
*UHN-IP Initial Emails Sent* -- _Yesterday_: 58, _Total_: 20318
```

- Metrics sharing a label are added up into one line, since configurations routinely spread one reported number over
  several metrics, for example one per clinic.
- A period that closed before yesterday shows its date instead, so a stalled roll-over is not mistaken for a recent
  count; a metric never rolled over shows only its total.
- Categories become headings when there is more than one; admin-only metrics are left out of unprivileged reports; and
  generating a report never changes a counter.

## Upgrading from the old layout

Up to 0.9.41, each metric was a plain folder under `/Metrics` holding a `name` node with the label, a `prevTotal` node
with the baseline, and a `total` node with the counter, and every status report reset the baseline. These are converted
in place, keeping the count: the label and its `{NNN}` order come from `name`, the baseline from `prevTotal`, the count
moves into the metric's own counter, and the metric gets the end-of-day schedule, which a component defining it may
then change. `/Metrics` itself becomes a `cards:MetricsHomepage`.

The conversion happens in a sweep when the metrics service starts, and whenever the service touches an old metric
before the sweep got to it. It cannot be a data migrator: the components that define and count metrics start before
the migrators run. It doesn't happen inside the service's activation either, since the components waiting for the
service would time out on a slow start. Until its first roll-over, a converted metric reports only its total.
