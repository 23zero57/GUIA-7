# ADR 001: Adoption of Hexagonal Architecture, DuckDB, and Resilience Patterns for EvalDoc

## Status
Accepted

## Context
The EvalDoc project requires an enterprise-grade backend capable of processing academic evaluation surveys, integrating with external public Colombian open data APIs, and executing analytical queries with descriptive statistics. To achieve a robust, testable, and maintainable software design, the system must decouple business logic from technical infrastructure (such as databases and HTTP clients) and gracefully handle network failures or third-party service outages.

## Decision
We have decided to implement the following architectural and technical choices:
1. **Hexagonal Architecture (Ports and Adapters):** The codebase is strictly partitioned into `domain` (pure business logic and rules), `application` (use cases and validation), and `infrastructure` (database managers, resilience policies, and HTTP adapters).
2. **DuckDB for Analytical Processing:** Embedded DuckDB via JDBC is adopted as the analytical data store to perform high-performance SQL queries, aggregations, and descriptive statistical computations directly within the application environment without heavy relational database servers.
3. **Resilience Patterns (Circuit Breaker & Retry with Exponential Backoff):** External integrations with Colombian open data sources are protected using a custom three-state Circuit Breaker (`CLOSED`, `OPEN`, `HALF_OPEN`) combined with an automatic Retry helper featuring exponential backoff to handle transient network errors safely.
4. **Dependency Injection & Testing:** All components leverage constructor-based dependency injection to facilitate comprehensive unit testing using JUnit 5 and Mockito.

## Consequences
### Positive
* **High Testability:** Business rules and adapters can be tested in isolation using mocks without external dependencies.
* **Fault Tolerance:** The system prevents cascading failures when external public APIs become unresponsive or return errors (e.g., HTTP 404 or timeouts).
* **Maintainability:** Clear separation of concerns simplifies code navigation, refactoring, and adherence to SOLID principles.
* **Embedded Analytics:** DuckDB allows complex analytical queries with minimum configuration and zero external database installation overhead.

### Negative
* **Boilerplate Overhead:** Hexagonal architecture introduces additional package structures and mapping layers compared to traditional monolithic scripts.
* **Learning Curve:** Maintaining strict boundaries between domain models and infrastructure data transfer objects requires careful dependency management.