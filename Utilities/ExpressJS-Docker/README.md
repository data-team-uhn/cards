Provides a Docker container for running ExpressJS servers

Build
-----

```bash
docker build -t cards/expressjs .
```

Using
-----

- Read-only volume mount the script which you wish to run and specify it
as the entrypoint.

- For example:

```bash
cd Utilities/Development
docker run --rm --name mockslack -v $(realpath mock_slack.js):/mock_slack.js:ro -p 8000:8000 -d cards/expressjs nodejs /mock_slack.js
docker logs mockslack
docker stop mockslack
```
