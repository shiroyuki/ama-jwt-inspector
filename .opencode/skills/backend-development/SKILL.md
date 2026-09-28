---
name: backend-development
description: Use when the user wants to build backend services using Python (FastAPI, Django) or Java (Spring Boot), adhering to PEP8/Java standards, SOLID, and 12-factor app principles.
---

# Backend Development Skill (Python & Java)

This skill provides guidance on building production-ready backend services using Python (FastAPI, Django) or Java (Spring Boot), ensuring high-quality code through SOLID principles and 12-factor app methodology.

## 12-Factor App Methodology (Backend Focus)
Ensure the application follows these key principles for portability and scalability:
- **Codebase:** One codebase, multiple deploys (production, staging, etc.).
- **Dependencies:** Explicitly declare and isolate dependencies (e.g., `requirements.txt`, `pom.xml`).
- **Config:** Store configuration in environment variables.
- **Backing Services:** Treat databases, caches, and queues as attached resources accessed via URLs/credentials.
- **Build, Release, Run:** Separate build stage from the execution of the application.
- **Logs:** Treat logs as event streams (let the execution environment handle rotation/storage).
- **Processes:** Execute each scale unit as the smallest amount of work.
- **Port Binding:** Export an app by binding to a port.
- **Distributed Applications:** Scale out via the scheduler (adding more instances).
- **Dev/Prod Parity:** Keep development, staging, and production as similar as possible.
- **Elasticity:** Scale horizontally easily.
- **Disposability:** Fast startup and graceful shutdown behavior.

## SOLID Principles in Backend
- **Single Responsibility (SRP):** Keep controllers/endpoints thin. Move business logic to dedicated Service layers or Command handlers.
- **Open/Closed (OCP):** Use Interfaces/Abstract classes to define types of operations (e.g., `PaymentProcessor` interface).
- **Liskov Substitution (LSP):** Ensure sub-classes of Service implementations can be swapped without breaking functionality.
- **Interface Segregation (ISP):** Split large interfaces into smaller, specific ones (e.g., `ReadOnlyRepository` vs `ReadWriteRepository`).
- **Dependency Inversion (DIP):** Inject dependencies (Dependency Injection) into services rather than hardcoding database or external service clients.

---

## Python (FastAPI / Django)
- **Python 3.13+ Standards:** Follow **PEP 8** strictly. Use type hints consistently.
- **FastAPI:** Use Pydantic models for request/response validation, Dependency Injection for database sessions, and BackgroundTasks for side effects.
- **Django:** Use the ORM efficiently. Keep views simple and logic in models or services.
- **Design Pattern:** Repository Pattern is highly recommended to abstract DB access.

## Java (Spring Boot)
- **Java 25+ Standards:** Use modern features (Records, Sealed Classes, Virtual Threads).
- **Spring Boot:** Use `@Service`, `@Repository`, and `@RestController` annotations correctly.
- **Architecture:** Use the Service-Repository-Controller pattern. Ensure proper Exception Handling using `@ControllerAdvice`.
- **Dependency Injection:** Leverage Spring's `ApplicationContext` for managing component lifecycles.

---

## Best Practices
- **Security:** Always validate input, never trust user data. Use environment variables for secrets.
- **Testing:** Implement Unit and Integration tests (PyTest for Python, JUnit/Mockito for Java).
- **Database:** Use migrations. Never perform raw SQL unless absolutely necessary for performance.
- **Logging:** Use structured logging. Avoid printing to stdout in production.
