package com.endava.cats.report.error.extract;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@QuarkusTest
class StackFrameParserTest {

    static Stream<Arguments> traces() {
        return Stream.of(
                Arguments.of("JVM skips the runtime", """
                        java.lang.NullPointerException: boom
                        \tat java.base/java.util.Objects.requireNonNull(Objects.java:233)
                        \tat com.acme.OrderService.create(OrderService.java:88)""", "com.acme.OrderService.create@OrderService.java:88"),
                Arguments.of("JVM prefers the root cause and application frames", """
                        javax.servlet.ServletException: wrapped
                        \torg.glassfish.jersey.servlet.WebComponent.service(WebComponent.java:427)
                        \tcom.acme.CorsFilter.doFilter(CorsFilter.java:29)
                        root cause
                        com.acme.NotFound: missing
                        \torg.springframework.aop.Proxy.invoke(Proxy.java:1)
                        \tcom.acme.Dao.find(Dao.java:41)""", "com.acme.Dao.find@Dao.java:41"),
                Arguments.of("JVM falls back to a library frame", "\tat org.springframework.web.Servlet.run(Servlet.java:7)",
                        "org.springframework.web.Servlet.run@Servlet.java:7"),
                Arguments.of("Node skips internals and node_modules", """
                        TypeError: x
                            at JSON.parse (<anonymous>)
                            at Layer.handle [as handle_request] (/app/node_modules/express/lib/router/layer.js:95:5)
                            at async ownerId (/app/src/x.js:10:5)""", "ownerId@/app/src/x.js:10"),
                Arguments.of("Node anonymous frame", "Error\n    at /app/x.js:3:9", "/app/x.js:3"),
                Arguments.of(".NET skips System frames", """
                        System.FormatException: bad
                           at System.Number.ThrowFormatException[TChar](ReadOnlySpan`1 value)
                           at Shop.Orders.ParseCount(String raw) in /src/Program.cs:line 26""", "Shop.Orders.ParseCount@/src/Program.cs:26"),
                Arguments.of("Python innermost frame is last", """
                        Traceback (most recent call last):
                          File "/usr/local/lib/python3.12/site-packages/starlette/routing.py", line 76, in app
                          File "/app/app.py", line 30, in handler
                          File "/app/app.py", line 22, in parse_count
                          File "/usr/local/lib/python3.12/json/decoder.py", line 9, in decode
                        ValueError: bad""", "parse_count@/app/app.py:22"),
                Arguments.of("Python skips compiled extension frames", """
                        Traceback (most recent call last):
                          File "/app/app/db/repositories/articles.py", line 120, in create_article
                          File "asyncpg/protocol/protocol.pyx", line 201, in bind_execute
                        asyncpg.exceptions.DataError: bad""", "create_article@/app/app/db/repositories/articles.py:120"),
                Arguments.of("Go starts after the panic", """
                        panic: boom

                        goroutine 7 [running]:
                        main.recoverer()
                        \t/app/main.go:33 +0x38
                        panic({0x1, 0x2})
                        \t/usr/local/go/src/runtime/panic.go:859 +0x120
                        github.com/gin-gonic/gin.(*Context).Next(...)
                        \t/go/pkg/mod/github.com/gin-gonic/gin@v1.10.0/context.go:185
                        main.(*Server).ownerID(...)
                        \t/app/main.go:23""", "main.(*Server).ownerID@/app/main.go:23"),
                Arguments.of("Ruby skips gems", """
                        actionpack (7.2.4) lib/abstract_controller/base.rb:215:in `process_action'
                        app/controllers/users_controller.rb:27:in `owner_id'""", "owner_id@app/controllers/users_controller.rb:27"),
                Arguments.of("PHP skips vendor", """
                        #0 /var/www/vendor/laravel/framework/src/Router.php(12): handle()
                        #1 /var/www/app/Http/Controllers/UserController.php(42): find()""", "find@/var/www/app/Http/Controllers/UserController.php:42"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("traces")
    void shouldFindTheCrashSite(String name, String trace, String expected) {
        Assertions.assertThat(StackFrameParser.crashSite(trace)).isEqualTo(expected);
    }

    @Test
    void shouldReturnNothingWithoutFramesOrWithRuntimeFramesOnly() {
        Assertions.assertThat(StackFrameParser.parse("Error at line 5 of the request")).isEmpty();
        Assertions.assertThat(StackFrameParser.parse("Order 42 failed at 2026-11-13T10:00:00Z")).isEmpty();
        Assertions.assertThat(StackFrameParser.crashSite("\tat java.lang.Thread.run(Thread.java:1583)")).isEmpty();
    }

    @Test
    void shouldTakeThePythonExceptionLineAfterTheTraceback() {
        String text = """
                Traceback (most recent call last):
                  File "/app/app.py", line 22, in parse_count
                    return int(raw)
                ValueError: invalid literal""";

        Assertions.assertThat(StackFrameParser.parse(text)).hasValueSatisfying(trace ->
                Assertions.assertThat(trace.message(text)).isEqualTo("ValueError: invalid literal"));
    }
}
