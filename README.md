# Adventure Book

## Prerequisites

Install JDK 21 and set `JAVA_HOME` to its installation directory. The Maven Wrapper downloads Maven and dependencies on the first build, so an internet connection is required initially.

## Queue the demo books

The operator must move the ten `demo-*.json` seeds from `fixtures/books/demo` into `books/ready`. Run these commands from the repository root before starting the backend.

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path .\books\ready | Out-Null
Move-Item -Path .\fixtures\books\demo\demo-*.json -Destination .\books\ready\
```

macOS / Linux:

```sh
mkdir -p books/ready
mv fixtures/books/demo/demo-*.json books/ready/
```

Queue the seeds once. Imported books remain in the local database across restarts. To exercise rejected imports manually, move one of the supplied original JSON files from `fixtures/books` into `books/ready` as well.

The importer checks the inbox at startup and every 30 seconds. Processed files move to `books/added` or `books/failed`; rejected files have a matching `<UUID>-report.json`. When adding files while the backend is running, write them with a temporary extension and rename them to `.json` after writing is complete.

## Build, test, and run

From the repository root, run the appropriate commands below.

Windows PowerShell:

```powershell
Set-Location backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```sh
cd backend
./mvnw clean verify
./mvnw spring-boot:run
```

The backend listens at `http://localhost:8080`. Stop it with `Ctrl+C`. For subsequent starts, run the last command from the `backend` directory.

## Optional configuration

Set environment variables before starting the backend:

| Variable | Default | Purpose |
| --- | --- | --- |
| `BOOK_IMPORT_ENABLED` | `true` | Enable the folder importer. Set to `false` on instances that should not process files. |
| `BOOK_IMPORT_DIRECTORY` | `../books` | Parent directory of `ready`, `added`, and `failed`. |
| `BOOK_IMPORT_SCAN_INTERVAL_MS` | `30000` | Delay between inbox scans, in milliseconds. |
| `ADVENTURE_DB_PATH` | `./data/adventure-book` | H2 database path without the `.mv.db` extension. |
| `PORT` | `8080` | Backend HTTP port. |

Relative paths resolve from the backend's working directory. If you change the import directory, move the seeds into that directory's `ready` subdirectory instead.
