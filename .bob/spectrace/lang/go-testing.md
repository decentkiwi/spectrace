# SpecTrace tracer — Go / go test language supplement

Read this file in addition to the main `.bob/spectrace/tracer-playbook.md`.
It overrides the Java-specific steps for Go projects using `go test`.

## Requirement tagging (line comment)

Tags use a `// req:` comment on the line immediately before the test function:

```go
// req: REQ-AUTH-01
func TestLoginWrongPassword(t *testing.T) {
    resp := mustPost(t, "/auth/login", `{"username":"alice","password":"wrong"}`)
    if resp.StatusCode != 401 {
        t.Fatalf("expected 401, got %d", resp.StatusCode)
    }
}
```

## Step 4 (Go): Write missing tests

- File: `{pkg}/requirements_test.go` (create if absent; use the same package)
- Use `net/http/httptest` for HTTP-level tests; call functions directly for unit tests.
- Place `// req: REQ-MOD-NN` on the line immediately above the `func Test...` signature.
- Setup pattern:
  ```go
  package auth_test

  import (
      "net/http"
      "net/http/httptest"
      "testing"
      "myapp/auth"
  )

  // req: REQ-AUTH-01
  func TestLoginRejectsWrongPassword(t *testing.T) {
      svc := auth.NewService()
      rec := httptest.NewRecorder()
      // ...
      if rec.Code != http.StatusUnauthorized {
          t.Fatalf("want 401, got %d", rec.Code)
      }
  }
  ```

## Step 5 (Go): Run your module's tests

```bash
go test ./{pkg}/... -v 2>&1 | \
  go-junit-report -set-exit-code > spectrace-results/results-{module}.xml; \
  true
```

Results are in `spectrace-results/results-{module}.xml`.

## Step 6 (Go): Triage failures

Go test failure:
```
--- FAIL: TestLoginRejectsWrongPassword (0.00s)
    auth_test.go:22: want 401, got 200
```
If the expected value is the literal acceptance criterion, the code is wrong — status `FAILING`.
Never change the expected value to make the test pass.
