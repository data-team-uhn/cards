# Java & Backend Conventions

## Technology Stack

- **Java 11+** - Target version
- **Apache Sling** - REST/servlet framework on JCR
- **Apache Jackrabbit Oak** - JCR implementation
- **OSGi** - Module system via Sling features

## Package Structure

```
io.uhndata.cards.<module>/
├── internal/      # OSGi component implementations
├── api/           # Public interfaces (exported)
└── impl/          # Non-OSGi implementations
```

## OSGi Components

Use declarative services annotations:

```java
@Component(service = MyService.class)
public class MyServiceImpl implements MyService {

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Activate
    protected void activate(ComponentContext context) {
        // initialization
    }
}
```

## Sling Servlets

Register servlets via annotations:

```java
@Component(service = Servlet.class)
@SlingServletResourceTypes(
    resourceTypes = "cards/Resource",
    methods = "GET",
    selectors = "export",
    extensions = "json"
)
public class ExportServlet extends SlingSafeMethodsServlet {
    // implementation
}
```

## JCR Operations

Always use try-with-resources for sessions:

```java
try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(null)) {
    Session session = resolver.adaptTo(Session.class);
    // operations
    session.save();
}
```

## Node Types

Custom node types defined in `*.cnd` files. Main types:
- `cards:Form` - Collected answers
- `cards:Questionnaire` - Question definitions
- `cards:Subject` - Patients, visits, etc.
- `cards:Answer` - Individual answer values

## Logging

Use SLF4J:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(MyClass.class);
```

## Maven Module POM

Each module needs:
- `<packaging>bundle</packaging>` for OSGi bundles
- `maven-bundle-plugin` configuration
- Proper export/import package declarations
