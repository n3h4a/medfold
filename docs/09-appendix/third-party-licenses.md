# Third-party licenses

Runtime dependencies shipped with each service and their declared licenses, generated from the
dependency trees on 2026-10-31 (test-only tools are not shipped and not listed). Medfold itself
is [MIT](../../LICENSE).

Every dependency is under a permissive license (Apache-2.0, MIT, BSD, EDL, PSF) or is
dual-licensed with one available (EPL-2.0 with LGPL-2.1 or GPL-2.0 with the Classpath Exception).

Regenerate:

```bash
(cd backend && ./mvnw org.codehaus.mojo:license-maven-plugin:2.4.0:add-third-party -Dlicense.excludedScopes=test,provided)
(cd frontend && npx license-checker --production --excludePrivatePackages)
(cd ai-service && pip install pip-licenses && pip-licenses)   # in an environment with requirements.txt installed
```

Runtime-only services not bundled in these images: PostgreSQL (PostgreSQL License), Tesseract OCR (Apache-2.0), nginx (BSD-2-Clause), Caddy (Apache-2.0).

## Backend (Maven, runtime) — 105 packages

| Package | License |
|---|---|
| `ch.qos.logback:logback-classic:1.5.38` | EPL-2.0 / LGPL-2.1-only |
| `ch.qos.logback:logback-core:1.5.38` | EPL-2.0 / LGPL-2.1-only |
| `com.fasterxml.jackson.core:jackson-annotations:2.21` | Apache-2.0 |
| `com.fasterxml:classmate:1.7.3` | Apache-2.0 |
| `com.nimbusds:nimbus-jose-jwt:10.9.1` | Apache-2.0 |
| `com.sun.istack:istack-commons-runtime:4.1.2` | EDL-1.0 |
| `com.zaxxer:HikariCP:7.0.2` | Apache-2.0 |
| `commons-logging:commons-logging:1.3.6` | Apache-2.0 |
| `io.micrometer:micrometer-commons:1.17.2` | Apache-2.0 |
| `io.micrometer:micrometer-core:1.17.2` | Apache-2.0 |
| `io.micrometer:micrometer-jakarta9:1.17.2` | Apache-2.0 |
| `io.micrometer:micrometer-observation:1.17.2` | Apache-2.0 |
| `jakarta.activation:jakarta.activation-api:2.1.4` | EDL-1.0 |
| `jakarta.annotation:jakarta.annotation-api:3.0.0` | EPL-2.0 / GPL-2.0-with-classpath-exception |
| `jakarta.inject:jakarta.inject-api:2.0.1` | Apache-2.0 |
| `jakarta.persistence:jakarta.persistence-api:3.2.0` | EDL-1.0 / EPL-2.0 |
| `jakarta.transaction:jakarta.transaction-api:2.0.1` | EPL-2.0 / GPL-2.0-with-classpath-exception |
| `jakarta.validation:jakarta.validation-api:3.1.1` | Apache-2.0 |
| `jakarta.xml.bind:jakarta.xml.bind-api:4.0.5` | EDL-1.0 |
| `net.bytebuddy:byte-buddy:1.18.15` | Apache-2.0 |
| `org.antlr:antlr4-runtime:4.13.2` | BSD-3-Clause |
| `org.apache.logging.log4j:log4j-api:2.25.5` | Apache-2.0 |
| `org.apache.logging.log4j:log4j-to-slf4j:2.25.5` | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-core:11.0.27` | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-el:11.0.27` | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-websocket:11.0.27` | Apache-2.0 |
| `org.aspectj:aspectjweaver:1.9.25.1` | EPL-2.0 |
| `org.checkerframework:checker-qual:3.55.1` | MIT |
| `org.eclipse.angus:angus-activation:2.0.3` | EDL-1.0 |
| `org.flywaydb:flyway-core:12.4.0` | Apache-2.0 |
| `org.flywaydb:flyway-database-postgresql:12.4.0` | Apache-2.0 |
| `org.glassfish.jaxb:jaxb-core:4.0.9` | EDL-1.0 |
| `org.glassfish.jaxb:jaxb-runtime:4.0.9` | EDL-1.0 |
| `org.glassfish.jaxb:txw2:4.0.9` | EDL-1.0 |
| `org.hdrhistogram:HdrHistogram:2.2.2` | BSD-2-Clause / CC0-1.0 |
| `org.hibernate.models:hibernate-models:1.1.2` | Apache License Version 2.0 |
| `org.hibernate.orm:hibernate-core:7.4.13.Final` | Apache-2.0 |
| `org.hibernate.validator:hibernate-validator:9.1.5.Final` | Apache-2.0 |
| `org.jboss.logging:jboss-logging:3.6.3.Final` | Apache-2.0 |
| `org.jspecify:jspecify:1.0.1` | Apache-2.0 |
| `org.postgresql:postgresql:42.7.14` | BSD-2-Clause |
| `org.slf4j:jul-to-slf4j:2.0.20` | MIT |
| `org.slf4j:slf4j-api:2.0.20` | MIT |
| `org.springframework.boot:spring-boot-actuator-autoconfigure:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-actuator:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-autoconfigure:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-data-commons:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-data-jpa:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-flyway:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-health:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-hibernate:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-http-converter:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-jackson:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-jdbc:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-jpa:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-micrometer-metrics:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-micrometer-observation:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-persistence:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-security-oauth2-resource-server:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-security:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-servlet:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-sql:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-actuator:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-data-jpa:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-flyway:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-jackson:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-jdbc:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-logging:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-micrometer-metrics:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-oauth2-resource-server:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-security:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-tomcat-runtime:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-tomcat:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-validation:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-webmvc:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-tomcat:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-transaction:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-validation:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-web-server:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot-webmvc:4.1.2` | Apache-2.0 |
| `org.springframework.boot:spring-boot:4.1.2` | Apache-2.0 |
| `org.springframework.data:spring-data-commons:4.1.2` | Apache-2.0 |
| `org.springframework.data:spring-data-jpa:4.1.2` | Apache-2.0 |
| `org.springframework.security:spring-security-config:7.1.2` | Apache-2.0 |
| `org.springframework.security:spring-security-core:7.1.2` | Apache-2.0 |
| `org.springframework.security:spring-security-crypto:7.1.2` | Apache-2.0 |
| `org.springframework.security:spring-security-oauth2-core:7.1.2` | Apache-2.0 |
| `org.springframework.security:spring-security-oauth2-jose:7.1.2` | Apache-2.0 |
| `org.springframework.security:spring-security-oauth2-resource-server:7.1.2` | Apache-2.0 |
| `org.springframework.security:spring-security-web:7.1.2` | Apache-2.0 |
| `org.springframework:spring-aop:7.0.10` | Apache-2.0 |
| `org.springframework:spring-aspects:7.0.10` | Apache-2.0 |
| `org.springframework:spring-beans:7.0.10` | Apache-2.0 |
| `org.springframework:spring-context:7.0.10` | Apache-2.0 |
| `org.springframework:spring-core:7.0.10` | Apache-2.0 |
| `org.springframework:spring-expression:7.0.10` | Apache-2.0 / SpEL |
| `org.springframework:spring-jdbc:7.0.10` | Apache-2.0 |
| `org.springframework:spring-orm:7.0.10` | Apache-2.0 |
| `org.springframework:spring-tx:7.0.10` | Apache-2.0 |
| `org.springframework:spring-web:7.0.10` | Apache-2.0 |
| `org.springframework:spring-webmvc:7.0.10` | Apache-2.0 |
| `org.yaml:snakeyaml:2.6` | Apache-2.0 |
| `tools.jackson.core:jackson-core:3.1.7` | Apache-2.0 |
| `tools.jackson.core:jackson-databind:3.1.7` | Apache-2.0 |

## Frontend (npm, production) — 11 packages

| Package | License |
|---|---|
| `@remix-run/route-pattern@0.22.1` | MIT |
| `@tanstack/query-core@5.105.0` | MIT |
| `@tanstack/react-query@5.105.0` | MIT |
| `@types/react-dom@19.3.0` | MIT |
| `@types/react@19.3.0` | MIT |
| `cookie-es@3.1.1` | MIT |
| `csstype@3.2.3` | MIT |
| `react-dom@19.3.1` | MIT |
| `react-router@8.5.1` | MIT |
| `react@19.3.1` | MIT |
| `scheduler@0.28.0` | MIT |

## AI service (pip, runtime) — 34 packages

| Package | License |
|---|---|
| `annotated-doc 0.0.5` | MIT |
| `annotated-types 0.8.0` | MIT |
| `anthropic 2.3.0` | Apache-2.0 |
| `anyio 4.15.1` | MIT |
| `click 8.5.0` | BSD-3-Clause |
| `docstring_parser 0.18.0` | MIT |
| `fastapi 0.162.0` | MIT |
| `h11 0.16.0` | MIT |
| `httpcore2 2.16.0` | BSD-3-Clause |
| `httptools 0.9.0` | MIT |
| `httpx2 2.16.0` | BSD-3-Clause |
| `idna 3.20` | BSD-3-Clause |
| `jiter 0.18.0` | MIT |
| `opentelemetry-api 1.45.2` | Apache-2.0 |
| `packaging 26.3` | Apache-2.0 OR BSD-2-Clause |
| `pillow 13.0.0` | MIT-CMU |
| `pydantic 2.14.2` | MIT |
| `pydantic-settings 2.16.2` | MIT |
| `pydantic_core 2.50.2` | MIT |
| `pypdf 6.21.4` | BSD-3-Clause |
| `pypdfium2 5.14.0` | BSD-3-Clause AND Apache-2.0 |
| `pytesseract 0.3.13` | Apache-2.0 |
| `python-dotenv 1.2.4` | BSD-3-Clause |
| `python-multipart 0.0.33` | Apache-2.0 |
| `PyYAML 6.0.3` | MIT |
| `sniffio 1.3.1` | Apache-2.0 OR MIT |
| `starlette 1.10.0` | BSD-3-Clause |
| `truststore 0.10.5` | MIT |
| `typing-inspection 0.4.4` | MIT |
| `typing_extensions 4.16.0` | PSF-2.0 |
| `uvicorn 0.54.0` | BSD-3-Clause |
| `uvloop 0.23.0` | Apache-2.0 OR MIT |
| `watchfiles 1.3.0` | MIT |
| `websockets 17.4` | BSD-3-Clause |
