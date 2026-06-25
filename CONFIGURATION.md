# Configuration Guide — Reminder Server

This document outlines the configuration variables, security requirements, profiles, and deployment steps for the Reminder backend server.

---

## 1. Required Environment Variables

To protect credentials in a public GitHub repository, sensitive properties are parameterized. You must define these variables in your host environment before startup:

| Environment Variable | Target Property | Description | Requirements |
|---|---|---|---|
| `DB_PASSWORD` | `spring.datasource.password` | Password of the MySQL database user. | Matching local/production MySQL account |
| `JWT_SECRET` | `jwt.secret` | Signing key for stateless JWT tokens. | 256-bit hex string (64 characters) |

> [!CAUTION]
> If `JWT_SECRET` is not set in the environment, the server will fail to initialize. Do not hardcode fallbacks in production.

---

## 2. Datasource Configuration

Modify the default JDBC settings in `application.properties` as needed:

* **Default JDBC URL**: `jdbc:mysql://localhost:3306/reminder`
* **Default Username**: `reminder_app`

If using a different username or custom URL, update properties accordingly:
```properties
spring.datasource.url=${DB_URL:jdbc:mysql://your-db-host:3306/reminder}
spring.datasource.username=${DB_USER:reminder_app}
```

---

## 3. Development vs. Production Setup

### Development Profile
* Local database (`localhost:3306`).
* Hibernate DDL Auto-generation set to `update` to dynamically sync schema.
* SQL logging enabled (`spring.jpa.show-sql=true`).

### Production Profile (Recommended Changes)
1. **Disable show-sql**: Set `spring.jpa.show-sql=false` to prevent query parameters and schema details from writing to application logs.
2. **Restrict DDL Auto**: Change `spring.jpa.hibernate.ddl-auto` to `validate` or `none` to prevent automatic schema modification on startup.
3. **Use a Secure Supervisor**: Run the Spring Boot process using `systemd` or Docker, passing credentials securely instead of exposing them in command history.

---

## 4. HTTPS / SSL Migration Guide

For secure mobile and desktop client connections, the server should run behind HTTPS.

### Option A: Reverse Proxy (Recommended)
Set up a reverse proxy (like Nginx, Apache, or Caddy) in front of the Spring Boot container/service:
1. Obtain an SSL certificate via Let's Encrypt (Certbot).
2. Configure Nginx to listen on port `443`, enforce SSL, and forward traffic to the Spring Boot port (default `8080`):
   ```nginx
   server {
       listen 443 ssl;
       server_name your-domain.com;

       ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
       ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

       location / {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
       
       # WebSocket upgrade support
       location /ws {
           proxy_pass http://localhost:8080;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
       }
   }
   ```

### Option B: Embedded Tomcat SSL
Configure SSL directly inside Spring Boot's properties by adding these to your custom properties file:
```properties
server.port=8443
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${SSL_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=tomcat
```
