# Testing Patterns

## Unit Tests

Located in each module's `src/test/java/` directory.

```bash
mvn test                    # Run unit tests
mvn test -Pquick            # Skip tests
```

## Integration Tests

Located in `tests/` module, only active with profile:

```bash
mvn verify -PintegrationTests
```

## Test Frameworks

- **JUnit** - Unit testing
- **Mockito** - Mocking
- **Sling Testing** - JCR/Sling mocks

## Mocking JCR

```java
@Mock
private ResourceResolver resolver;

@Mock
private Resource resource;

@Before
public void setup() {
    when(resolver.getResource("/path")).thenReturn(resource);
    when(resource.adaptTo(Node.class)).thenReturn(mockNode);
}
```

## Test Questionnaires

Test questionnaire definitions in `test-resources/`.
Enable with `--test` flag or `ENABLE_TEST_FEATURES=true`.

## CI/CD

GitHub Actions workflows in `.github/workflows/`:
- `ci-test.yml` - Main test pipeline
- `ci-test_smtps.yml` - Email integration tests
