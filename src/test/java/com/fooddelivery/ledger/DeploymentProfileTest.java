package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The deployed stack must not run a money service with the debug profile.
 *
 * <p>{@code docker-compose.yml} appended {@code ,debug} unconditionally to the payment service. In
 * dev that registered only the mock gateway strategies, whose {@code createOrder} schedules a
 * success webhook two seconds later -- <b>every order marked paid without any money being taken</b>.
 * With {@code SPRING_PROFILES_ACTIVE=prod} the value expanded to {@code prod,debug}, registering a
 * real and a mock strategy for the same gateway, and the service did not start.
 *
 * <p>This test previously asserted that the active profile inside a Spring test was not null, which
 * says nothing about the deployment. It now reads the compose file.
 */
public class DeploymentProfileTest {

    private static final List<String> MONEY_SERVICES = List.of(
            "payment-gateway", "ledger-service", "wallet-service", "customer-service");

    private static Path compose() {
        // The test runs from the module directory; the deployment lives at the workspace root.
        for (Path candidate : List.of(
                Path.of("Deployment/docker-compose.yml"),
                Path.of("../Deployment/docker-compose.yml"))) {
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot locate Deployment/docker-compose.yml from " + Path.of("").toAbsolutePath());
    }

    private static String read() {
        try {
            return Files.readString(compose(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void noMoneyServiceAppendsDebugToItsProfileList() {
        String yaml = read();
        List<String> offenders = new ArrayList<>();

        // Walk the file tracking which service block each SPRING_PROFILES_ACTIVE line belongs to.
        String currentService = null;
        for (String line : yaml.split("\n")) {
            Matcher service = Pattern.compile("^ {2}([a-z0-9-]+):\\s*$").matcher(line);
            if (service.find()) {
                currentService = service.group(1);
            }
            if (line.contains("SPRING_PROFILES_ACTIVE") && !line.trim().startsWith("#")) {
                String value = line.substring(line.indexOf("SPRING_PROFILES_ACTIVE") + "SPRING_PROFILES_ACTIVE".length());
                if (value.contains("debug")) {
                    offenders.add((currentService == null ? "?" : currentService) + " -> " + line.trim());
                }
            }
        }

        if (!offenders.isEmpty()) {
            fail("A service is deployed with the debug profile, which registers the mock gateway "
                    + "strategies and marks every order paid without taking money:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    @Test
    void everyMoneyServiceTakesItsProfileFromTheEnvironment() {
        String yaml = read();
        List<String> missing = new ArrayList<>();
        for (String service : MONEY_SERVICES) {
            int at = yaml.indexOf("\n  " + service + ":");
            if (at < 0) continue;
            String block = yaml.substring(at, Math.min(yaml.length(), at + 6000));
            int next = block.indexOf("\n  ", 1);
            // Trim at the next top-level service so a neighbour's setting cannot satisfy this one.
            for (int i = 1; i < block.length() - 3; i++) {
                if (block.charAt(i) == '\n' && block.charAt(i + 1) == ' ' && block.charAt(i + 2) == ' '
                        && block.charAt(i + 3) != ' ' && block.charAt(i + 3) != '#') {
                    next = i;
                    break;
                }
            }
            String own = block.substring(0, next > 0 ? next : block.length());
            if (!own.contains("SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE")) {
                missing.add(service);
            }
        }
        assertTrue(missing.isEmpty(),
                "these money services do not take SPRING_PROFILES_ACTIVE from the environment: " + missing);
    }

    @Test
    void composeCarriesTheServicesThisTestClaimsToCheck() {
        // A locator that silently reads the wrong file would make both checks vacuously green.
        String yaml = read();
        assertFalse(yaml.isBlank());
        assertTrue(yaml.contains("payment-gateway"), "compose file does not mention payment-gateway");
        assertTrue(yaml.contains("SPRING_PROFILES_ACTIVE"), "compose file sets no profiles at all");
    }
}
