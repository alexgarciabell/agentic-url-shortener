package com.example.agentic.agent;

import com.example.agentic.domain.FileOperation;

import java.util.List;

final class DemoProjectTemplate {
    private DemoProjectTemplate() {
    }

    static List<FileOperation> operations() {
        return List.of(
                write("pom.xml", pom()), write("README.md", readme()),
                write("src/main/java/com/example/shortener/UrlShortenerApplication.java", app()),
                write("src/main/java/com/example/shortener/url/ShortUrl.java", entity()),
                write("src/main/java/com/example/shortener/url/ShortUrlRepository.java", repository()),
                write("src/main/java/com/example/shortener/url/UrlService.java", service()),
                write("src/main/java/com/example/shortener/url/UrlController.java", controller()),
                write("src/main/java/com/example/shortener/url/ApiExceptionHandler.java", errors()),
                write("src/main/resources/application.yml", config()),
                write("src/test/resources/application.yml", testConfig()),
                write("src/test/java/com/example/shortener/url/UrlServiceTest.java", unitTest()),
                write("src/test/java/com/example/shortener/url/UrlControllerTest.java", integrationTest())
        );
    }

    private static FileOperation write(String p, String c) {
        return new FileOperation(p, c, FileOperation.Operation.WRITE);
    }

    private static String pom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                 <modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.5</version><relativePath/></parent>
                 <groupId>com.example</groupId><artifactId>generated-url-shortener</artifactId><version>1.0.0-SNAPSHOT</version><properties><java.version>21</java.version></properties>
                 <dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency><dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency></dependencies>
                 <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
                </project>
                """;
    }

    private static String readme() {
        return "# Generated URL Shortener\n\nRun: `mvn clean verify` then `mvn spring-boot:run`.\n";
    }

    private static String app() {
        return """
                package com.example.shortener; import org.springframework.boot.*; import org.springframework.boot.autoconfigure.*; @SpringBootApplication public class UrlShortenerApplication { public static void main(String[] args){SpringApplication.run(UrlShortenerApplication.class,args);} }""";
    }

    private static String entity() {
        return """
                package com.example.shortener.url; import jakarta.persistence.*; import java.time.*; @Entity public class ShortUrl { @Id private String code; @Column(nullable=false,length=2048) private String targetUrl; private Instant createdAt; private Instant expiresAt; private long clickCount; private Instant lastAccessedAt; protected ShortUrl(){} public ShortUrl(String c,String t,Instant e){code=c;targetUrl=t;expiresAt=e;createdAt=Instant.now();} public String getCode(){return code;} public String getTargetUrl(){return targetUrl;} public Instant getCreatedAt(){return createdAt;} public Instant getExpiresAt(){return expiresAt;} public long getClickCount(){return clickCount;} public Instant getLastAccessedAt(){return lastAccessedAt;} public boolean expired(Instant now){return expiresAt!=null&&!expiresAt.isAfter(now);} public void recordClick(Instant now){clickCount++;lastAccessedAt=now;} }""";
    }

    private static String repository() {
        return """
                package com.example.shortener.url; import org.springframework.data.jpa.repository.JpaRepository; public interface ShortUrlRepository extends JpaRepository<ShortUrl,String>{}""";
    }

    private static String service() {
        return """
                package com.example.shortener.url; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.net.*; import java.time.*; import java.util.*; @Service public class UrlService { private final ShortUrlRepository repo; public UrlService(ShortUrlRepository r){repo=r;} @Transactional public ShortUrl create(String target,String alias,Instant expires){validate(target); String code=alias==null||alias.isBlank()?UUID.randomUUID().toString().replace("-","").substring(0,8):alias; if(repo.existsById(code)) throw new IllegalStateException("Alias already exists"); return repo.save(new ShortUrl(code,target,expires));} @Transactional public ShortUrl resolve(String code){ShortUrl u=find(code); if(u.expired(Instant.now())) throw new ExpiredUrlException(); u.recordClick(Instant.now()); return u;} @Transactional(readOnly=true) public ShortUrl analytics(String code){return find(code);} @Transactional public void delete(String code){if(!repo.existsById(code)) throw new NoSuchElementException(); repo.deleteById(code);} private ShortUrl find(String c){return repo.findById(c).orElseThrow(NoSuchElementException::new);} private void validate(String value){try{URI u=URI.create(value); if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null) throw new IllegalArgumentException("Only valid HTTP/HTTPS URLs are allowed");}catch(RuntimeException e){throw new IllegalArgumentException("Only valid HTTP/HTTPS URLs are allowed");}} public static class ExpiredUrlException extends RuntimeException{} }""";
    }

    private static String controller() {
        return """
                package com.example.shortener.url; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.net.*; import java.time.*; @RestController public class UrlController { private final UrlService service; public UrlController(UrlService s){service=s;} public record CreateRequest(@NotBlank String targetUrl,String customAlias,Instant expiresAt){} public record UrlResponse(String shortCode,String shortUrl,String targetUrl,Instant createdAt,Instant expiresAt){} public record AnalyticsResponse(String shortCode,String targetUrl,long clickCount,Instant createdAt,Instant lastAccessedAt,Instant expiresAt){} @PostMapping("/api/urls") ResponseEntity<UrlResponse> create(@Valid @RequestBody CreateRequest r){var u=service.create(r.targetUrl(),r.customAlias(),r.expiresAt()); return ResponseEntity.status(201).body(new UrlResponse(u.getCode(),"/"+u.getCode(),u.getTargetUrl(),u.getCreatedAt(),u.getExpiresAt()));} @GetMapping("/{code}") ResponseEntity<Void> redirect(@PathVariable String code){return ResponseEntity.status(302).location(URI.create(service.resolve(code).getTargetUrl())).build();} @GetMapping("/api/urls/{code}/analytics") AnalyticsResponse analytics(@PathVariable String code){var u=service.analytics(code); return new AnalyticsResponse(u.getCode(),u.getTargetUrl(),u.getClickCount(),u.getCreatedAt(),u.getLastAccessedAt(),u.getExpiresAt());} @DeleteMapping("/api/urls/{code}") ResponseEntity<Void> delete(@PathVariable String code){service.delete(code);return ResponseEntity.noContent().build();} }""";
    }

    private static String errors() {
        return """
                package com.example.shortener.url; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*; @RestControllerAdvice public class ApiExceptionHandler { @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Void> missing(){return ResponseEntity.notFound().build();} @ExceptionHandler(UrlService.ExpiredUrlException.class) ResponseEntity<Void> expired(){return ResponseEntity.status(HttpStatus.GONE).build();} @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));} @ExceptionHandler(IllegalStateException.class) ResponseEntity<Map<String,String>> conflict(IllegalStateException e){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",e.getMessage()));} }""";
    }

    private static String config() {
        return """
                spring:
                  datasource:
                    url: jdbc:h2:file:./data/url-shortener
                  jpa:
                    hibernate:
                      ddl-auto: update
                management:
                  endpoints:
                    web:
                      exposure:
                        include: health
                """;
    }

    private static String testConfig() {
        return """
                spring:\n  datasource:\n    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1\n  jpa:\n    hibernate:\n      ddl-auto: create-drop\n""";
    }

    private static String unitTest() {
        return """
                package com.example.shortener.url; import org.junit.jupiter.api.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*; class UrlServiceTest { ShortUrlRepository repo=mock(ShortUrlRepository.class); UrlService service=new UrlService(repo); @Test void rejectsUnsafeScheme(){assertThrows(IllegalArgumentException.class,()->service.create("javascript:alert(1)",null,null));} @Test void deletesMissingAsNotFound(){when(repo.existsById("x")).thenReturn(false);assertThrows(NoSuchElementException.class,()->service.delete("x"));} }""";
    }

    private static String integrationTest() {
        return """
                package com.example.shortener.url;
                
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.http.MediaType;
                import org.springframework.test.web.servlet.MockMvc;
                
                import java.util.Map;
                
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
                
                @SpringBootTest
                @AutoConfigureMockMvc
                class UrlControllerTest {
                
                    @Autowired
                    MockMvc mvc;
                
                    @Autowired
                    ObjectMapper objectMapper;
                
                    @Test
                    void createRedirectAnalyticsDelete() throws Exception {
                        String requestBody = objectMapper.writeValueAsString(Map.of(
                            "targetUrl", "https://example.com",
                            "customAlias", "docs"
                        ));
                
                        mvc.perform(post("/api/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                            .andExpect(status().isCreated());
                
                        mvc.perform(get("/docs"))
                            .andExpect(status().isFound())
                            .andExpect(header().string("Location", "https://example.com"));
                
                        mvc.perform(get("/api/urls/docs/analytics"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.clickCount").value(1));
                
                        mvc.perform(delete("/api/urls/docs"))
                            .andExpect(status().isNoContent());
                
                        mvc.perform(get("/docs"))
                            .andExpect(status().isNotFound());
                    }
                }
                """;
    }

}
