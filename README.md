# Adventure Book

## Prerequisites

- JDK 21 with `JAVA_HOME` set to its installation directory.
- Bash for the backend commands below. Git Bash works on Windows.
- Node.js 24 with npm.
- Internet access on the first build to download dependencies.

## Commands to download dependencies and run

From the repository root, start the backend in a Bash terminal.

```sh
cd backend
bash mvnw clean verify
bash mvnw spring-boot:run
```

The included Maven Wrapper downloads Maven 3.9.11 and the backend dependencies on the first run. A separate Maven installation is optional. The `verify` command runs the tests, and the backend listens on `http://localhost:8080`.

From the repository root, start the frontend in a second terminal.

```sh
cd frontend
npm ci
npm run build
npm start
```

Open [the catalogue](http://localhost:4200). The Angular development server forwards API requests to the backend.

## Stack

- Java 21, Spring Boot 4.1.2, and Maven for the backend.
- Angular 21.2.26, TypeScript, and RxJS for the frontend.
- Local H2 database with Spring Data JPA and Flyway migrations.

## Features and how to test

### Import books

The `books/ready` directory simulates an FTP drop folder on your machine. Move JSON files from `fixtures/books` or `fixtures/books/demo` into `books/ready` at the repository root. The backend scans the folder at startup and then 30 seconds after each scan finishes. When dropping a file into the folder while the backend is running, use a temporary extension and rename it to `.json` after the transfer completes.

All ten demo books in `fixtures/books/demo` are valid. The four original files directly under `fixtures/books` are invalid and can be used to test rejection. Valid books appear in the catalogue, and their files move to `books/added`. Files with duplicate content also move to `books/added` without adding another catalogue entry. Invalid files move to `books/failed` with a matching `<UUID>-report.json` that identifies where each error occurred and describes it. Refresh the catalogue after the scan to see newly imported books.

### Filter the catalogue

Search matches part of a book's title or author name, regardless of case. Select **Easy**, **Medium**, or **Hard** to narrow the results. Select the same difficulty again to clear it. Try combining a title or author search with a difficulty.

### Browse with Load more

The catalogue shows three books initially. With all ten demos imported, select **Load more** three times to see the count grow from 3 to 6 to 9 to 10. The button disappears when there are no more results. Filtering keeps the number of books requested so far and shows up to that many matches.

### Play an adventure

Choose **Begin quest** to start at the book's opening section. Choose an option to move through the story. You start with 10 health, and choices can add or remove health. A consequence notification shows the health change and what happened. Use **Show hint** to reveal the consequences for the choices on the current page. Each quest has three hints. The adventure ends when you reach an ending or run out of health.

| Action | What it does |
| --- | --- |
| **Back to the library** | Returns to the catalogue and drops changes made since the last save. |
| **Restart** | Asks for confirmation, clears the saved progress for this book, and starts the quest again. |
| **Stop** | Offers **Discard and stop** or **Cancel**. Discarding clears progress for this book and returns to the catalogue. |
| **Save Progress** | Saves the current state and keeps the quest open. |
| **Pause** | Asks for confirmation, then saves progress and returns to the catalogue. |

### Resume a saved adventure

The browser stores a separate checkpoint for each saved book in `localStorage`. **Save Progress** and confirmed **Pause** save during play. Completing a quest or dying saves the outcome automatically. To test it, make a choice, save, reload the catalogue, and use **Return to quest** on the book card to pick up where you left off.

With user identification in place, a future version could store these checkpoints in the database so players can resume across browsers and devices.
