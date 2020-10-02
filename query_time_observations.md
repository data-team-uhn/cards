# Query Time Observations

- Loaded 10,846 TissueMetrix Forms

### Observation 1

- Queries are much faster in Composum because of the limit of 500 that it imposes on these queries

#### Evidence

- Filter for `"Life status" = "Deceased"` with:
    - `Rows per page`: `10`
    - `Query Offset: 0`
    - `Greater than date`: `01/01/1970 12:00AM`

- Filter executes in about 1.38 seconds

- Filter for `"Life status" = "Deceased"` with:
    - `Rows per page`: `1000`
    - `Query Offset: 0`
    - `Greater than date`: `01/01/1970 12:00AM`

- Filter executes in about 2.12 minutes

### Observation 2

- Increasing the query offset results in longer queries but the same effect
can be obtained at a higher performance if a `Greater than date` is used to
define an offset (given that we are ordering by `jcr:created`).

#### Evidence

- Filter for `"Life status" = "Deceased"` with:
    - `Rows per page`: `10`
    - `Query Offset: 0`
    - `Greater than date`: `01/01/1970 12:00AM`

- Filter executes in about 1.38 seconds

- Filter for `"Life status" = "Deceased"` with:
    - `Rows per page`: `10`
    - `Query Offset: 500`
    - `Greater than date`: `01/01/1970 12:00AM`

- Filter executes in about 5.42 seconds

- Filter for `"Life status" = "Deceased"` with:
    - `Rows per page`: `10`
    - `Query Offset: 750`
    - `Greater than date`: `01/01/1970 12:00AM`

- Filter executes in about 2.05 minutes

- Revert back to `Query Offset: 0` and instead adjust the
`Greater than date` to seek through the results `10` at a time.
Performance will be greatly improved.
