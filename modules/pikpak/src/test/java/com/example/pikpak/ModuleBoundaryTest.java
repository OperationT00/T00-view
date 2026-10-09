package com.example.pikpak;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ModuleBoundaryTest {
    @Test void mainCodeHasOnlyJdkAndOwnModuleImports() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (line.stripLeading().startsWith("import ")) {
                        String declaration = line.strip().replace("import static ", "").replace("import ", "");
                        assertTrue(declaration.startsWith("java.") || declaration.startsWith("com.example.pikpak."),
                            () -> "Forbidden dependency: " + file + " " + line);
                    }
                }
            }
        }
    }
}
