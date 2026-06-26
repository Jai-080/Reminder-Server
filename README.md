# Reminder Server

The backend REST API and WebSocket synchronizer for the Reminder Ecosystem (supporting both Android and Windows clients).

## Technical Overview
* Built with Spring Boot 3.x, Spring Security, Spring Web, JPA/Hibernate, and WebSocket STOMP.
* Uses JWT (JSON Web Tokens) for stateless authentication.
* Enforces "Last-Write-Wins" (LWW) conflict resolution logic during synchronization.

## Prerequisites
* Java 17 or higher
* Maven 3.x
* MySQL 8.x Database

## Getting Started

### 1. Database Setup
Create a local schema for the application:
```sql
CREATE DATABASE reminder CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configuration & Environment
The application parameters are configured in `server/src/main/resources/application.properties`. 
Before launching, copy the template config:
```bash
cp server/src/main/resources/application.properties.example server/src/main/resources/application.properties
```

Define the required credentials in your environment variables:
* `DB_PASSWORD`: Password for the configured MySQL database user.
* `JWT_SECRET`: Hex-encoded signature key of at least 256 bits (64 hex characters).

### 3. Compilation & Build
To compile the package:
```bash
mvn clean package -DskipTests
```

### 4. Running the Server
Pass the environment variables when executing the built jar:
```bash
DB_PASSWORD="your_password" JWT_SECRET="your_64_character_hex_key" java -jar server/target/server-1.0.0.jar
```

## Setup Guides
For detailed profiles, environment setup, database configurations, and HTTPS production setups, please refer to [CONFIGURATION.md](CONFIGURATION.md).
