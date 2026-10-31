# Postman collection

`medfold.postman_collection.json` covers every endpoint of the API, grouped as System,
Authentication, Documents, Records & search and Account. Run top to bottom, it is a complete
scenario: register a fresh account, upload a synthetic report, wait for processing, read and
correct the results, search, export, then delete the document and the account. Every request has
assertions, and tokens and ids are passed along automatically.

| Environment | `baseUrl` |
|---|---|
| `medfold.local.postman_environment.json` | `http://localhost:8080/api/v1` (`scripts/start-dev.sh`) |
| `medfold.docker.postman_environment.json` | `http://localhost:3000/api/v1` (`docker compose up`) |

**In Postman:** import the collection and an environment, select the environment, and use
*Run collection*. For the upload request, select a file from `tests/sample-pdfs/`.

**From the command line** (this is what CI runs against the Docker stack):

```bash
npx newman run postman/medfold.postman_collection.json \
  -e postman/medfold.local.postman_environment.json --working-dir .
```

The refresh token is an HttpOnly cookie; Postman and Newman keep it in their cookie jar, so
*Refresh session* and *Log out* work without extra setup. See [API design](../docs/03-design/api-design.md)
for the full API reference.
