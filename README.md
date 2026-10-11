
# Multi-Tenant Parallel Job Processing Engine

A database-backed asynchronous job processing engine built with
Java 21, Spring Boot, PostgreSQL, and Java concurrency.

## Core goals

- Accept jobs through REST APIs.
- Persist jobs before acknowledging submission.
- Discover pending jobs using configurable polling.
- Execute jobs asynchronously using a bounded worker pool.
- Process different tenants concurrently.
- Enforce sequential execution within each tenant.
- Support retries, idempotency, and crash recovery.

## Technology stack

- Java 21
- Spring Boot
- PostgreSQL
- Spring JDBC
- Flyway
- Docker Compose
- JUnit and Testcontainers

## Prerequisites

- JDK 21
- Docker Desktop
- Git

## Run locally

Start PostgreSQL:

    docker compose up -d postgres

Start the application:

    ./mvnw spring-boot:run

On Windows PowerShell:

    .\mvnw.cmd spring-boot:run

Check application health:

    http://localhost:8080/actuator/health

## Development status

- [x] Initial project setup
- [x] PostgreSQL container configuration
- [x] Initial database migration
- [ ] Job submission API
- [ ] Job executor registry
- [ ] Configurable polling and dispatch
- [ ] Tenant-aware parallel execution
- [ ] Retry and recovery mechanisms
- [ ] Integration and concurrency tests
- [ ] Performance benchmarks

## Design principle

The database is the source of truth for job state.
Job claiming must be atomic, and tenant-level execution
must remain safe across multiple application instances.
