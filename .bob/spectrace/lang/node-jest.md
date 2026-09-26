# SpecTrace tracer — Node.js / Jest language supplement

Read this file in addition to the main `.bob/spectrace/tracer-playbook.md`.
It overrides the Java-specific steps for Node.js projects using Jest.

## Requirement tagging (JSDoc comment)

Tags use a `// @req` comment on the line immediately before the test:

```javascript
// @req REQ-AUTH-01
test('login with wrong password returns 401', async () => {
  const res = await request(app).post('/auth/login')
    .send({ username: 'alice', password: 'wrong' });
  expect(res.statusCode).toBe(401);
});
```

TypeScript works the same way:
```typescript
// @req REQ-AUTH-01
it('rejects expired tokens', async () => {
  // ...
  expect(response.status).toBe(401);
});
```

## Step 4 (Node): Write missing tests

- File: `src/{module}/{module}.requirements.test.{js,ts}` (create if absent)
- Use Jest + Supertest for HTTP-level tests; import modules directly for unit tests.
- Place `// @req REQ-MOD-NN` on the line immediately above the `test(` or `it(` call.
- Setup pattern:
  ```javascript
  const request = require('supertest');
  const app = require('../../app');

  // @req REQ-AUTH-01
  test('login rejects missing password', async () => {
    const res = await request(app).post('/auth/login').send({ username: 'alice' });
    expect(res.statusCode).toBe(400);
  });
  ```

## Step 5 (Node): Run your module's tests

```bash
npx jest --testPathPattern="src/{pkg}" \
  --forceExit --passWithNoTests 2>/dev/null || true
```

Results are in `spectrace-results/` (configured by `jest-junit` reporter in `package.json`).

## Step 6 (Node): Triage failures

Jest failure output:
```
Expected: 401
Received: 200
```
If the expected value is the literal acceptance criterion, the code is wrong — status `FAILING`.
Never change the expected value to make the test pass.
