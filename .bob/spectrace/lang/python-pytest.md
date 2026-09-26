# SpecTrace tracer — Python / pytest language supplement

Read this file in addition to the main `.bob/spectrace/tracer-playbook.md`.
It overrides the Java-specific steps for Python projects using pytest.

## Requirement tagging (pytest marks)

Tags use pytest marks. The pattern from `spectrace.yaml` is:
```
@pytest\.mark\.(REQ_[A-Z]+_\d+)
```
Note: marks use **underscores**, not hyphens. `build_report.py` normalises
`REQ_AUTH_01` → `REQ-AUTH-01` when `tag_normalise: underscore-to-hyphen` is set.

```python
import pytest

@pytest.mark.REQ_AUTH_01
def test_login_rejects_wrong_password():
    response = client.post("/auth/login", json={"user": "alice", "password": "wrong"})
    assert response.status_code == 401
```

Multiple criteria on one function = multiple marks:
```python
@pytest.mark.REQ_AUTH_01
@pytest.mark.REQ_AUTH_02
def test_login_and_token():
    ...
```

## Step 4 (Python): Write missing tests

- File: `tests/<module>/test_<module>_requirements.py`  (create if absent)
- Use `pytest`, `httpx` or `requests` for HTTP-level tests; import the service
  directly for unit tests.
- Add `@pytest.mark.REQ_MOD_NN` (underscores) above each test function.
- Fixture pattern for a service under test:
  ```python
  import pytest
  from myapp.auth import AuthService, UserStore

  @pytest.fixture
  def svc():
      return AuthService(UserStore())
  ```

## Step 5 (Python): Run your module's tests

```bash
python -m pytest tests/{module}/ \
  --junit-xml=spectrace-results/results-{module}.xml \
  -p no:warnings -q \
  || true
```

Read results from `spectrace-results/results-{module}.xml`.

## Step 6 (Python): Triage failures

A failing assert in pytest looks like:
```
AssertionError: assert 200 == 401
```
If the expected value matches the acceptance criterion exactly, the code is wrong —
status `FAILING`. Never change the expected value to make the test pass.
