package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A developer must be able to bring the stack up with no secrets.
 *
 * <p>Every {@code ${VAR}} a non-prod profile resolves needs either a default or a value supplied by
 * docker-compose; otherwise Spring fails placeholder resolution and the service does not start.
 * Removing the {@code test_}-prefixed gateway defaults from {@code payment-service.yml} without
 * scoping the removal to the prod profile left seven such placeholders, and the payment service
 * could not start under {@code dev} — the profile compose actually defaults to.
 *
 * <p>Under prod the opposite must hold: a credential with a fallback is a credential that can boot
 * on a placeholder, which is what {@code ProdSecretsGuardTest} and the {@code @PostConstruct} guards
 * exist to prevent.
 */
public class DeploymentConfigResolvesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)(:[^}]*)?}");

    private static Path deployment() {
        for (Path candidate : List.of(Path.of("Deployment"), Path.of("../Deployment"))) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot locate Deployment/ from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Environment variables docker-compose supplies to every service. */
    private static java.util.Set<String> composeSuppliedVars() {
        String compose = read(deployment().resolve("docker-compose.yml"));
        java.util.Set<String> names = new java.util.HashSet<>();
        Matcher m = Pattern.compile("^\\s*-\\s*([A-Z][A-Z0-9_]*)=", Pattern.MULTILINE).matcher(compose);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static String stripComments(String yaml) {
        return yaml.replaceAll("(?m)^\\s*#[^\\n]*", "");
    }

    @Test
    void everyNonProdPlaceholderResolvesWithoutSecrets() {
        java.util.Set<String> supplied = composeSuppliedVars();
        Map<String, List<String>> unresolvable = new LinkedHashMap<>();

        for (Path yml : List.of("payment-service.yml", "ledger-service.yml", "customer-service.yml")
                .stream().map(n -> deployment().resolve(n)).filter(Files::exists).toList()) {
            // Comments are prose. A comment explaining a placeholder is not a placeholder --
            // this check first flagged its own explanatory note in payment-service.yml.
            String content = stripComments(read(yml));
            // Split multi-document YAML and skip any document activated only under prod.
            for (String doc : content.split("(?m)^---\\s*$")) {
                if (doc.contains("on-profile: prod") || doc.contains("on-profile: \"prod\"")) {
                    continue;
                }
                List<String> missing = new ArrayList<>();
                Matcher m = PLACEHOLDER.matcher(doc);
                while (m.find()) {
                    String var = m.group(1);
                    boolean hasDefault = m.group(2) != null;
                    if (!hasDefault && !supplied.contains(var)) {
                        missing.add(var);
                    }
                }
                if (!missing.isEmpty()) {
                    unresolvable.computeIfAbsent(yml.getFileName().toString(), k -> new ArrayList<>()).addAll(missing);
                }
            }
        }

        if (!unresolvable.isEmpty()) {
            fail("These placeholders have no default and are not supplied by docker-compose, so the "
                    + "service cannot start outside prod:\n  " + unresolvable);
        }
    }

    /** Under prod the gateway credentials must have no fallback at all. */
    @Test
    void prodGatewayCredentialsHaveNoDefaults() {
        String content = stripComments(read(deployment().resolve("payment-service.yml")));
        String prodDoc = null;
        for (String doc : content.split("(?m)^---\\s*$")) {
            if (doc.contains("on-profile: prod")) {
                prodDoc = doc;
            }
        }
        assertTrue(prodDoc != null, "payment-service.yml has no prod profile document");

        List<String> withDefaults = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(prodDoc);
        while (m.find()) {
            String var = m.group(1);
            boolean isCredential = var.contains("KEY") || var.contains("SECRET") || var.contains("CLIENT_ID");
            if (isCredential && m.group(2) != null) {
                withDefaults.add(var);
            }
        }
        assertTrue(withDefaults.isEmpty(),
                "these prod credentials have a fallback, so the service would boot on a placeholder: " + withDefaults);
    }

    /** The parser must actually be reading the file. */
    @Test
    void theDeploymentConfigIsActuallyRead() {
        String content = read(deployment().resolve("payment-service.yml"));
        assertTrue(content.contains("razorpay"), "payment-service.yml does not mention razorpay");
        assertTrue(new Yaml().loadAll(content).iterator().hasNext(), "payment-service.yml is not parseable YAML");
    }
}
