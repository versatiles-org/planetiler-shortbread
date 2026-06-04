package com.onthegomap.planetiler.shortbread;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.validator.BaseSchemaValidator;
import com.onthegomap.planetiler.validator.SchemaSpecification;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the example-based specification ported from {@code shortbread.spec.yml} (originally the test spec for the YAML
 * {@code custommap} implementation) against the Java {@link Shortbread} profile.
 * <p>
 * Each example describes an input feature and the vector-tile features it is expected to produce. Where the Java profile
 * intentionally diverges from the original YAML output (bug fixes, Tilemaker-faithful behaviour, omitting empty-string
 * NULL sentinels), the expectation in our copy of the spec has been adjusted with an inline {@code # DEVIATION} comment.
 */
class ShortbreadSpecTest {

  private static final Path SPEC =
    Path.of("src", "test", "resources", "shortbread.spec.yml");

  @TestFactory
  List<DynamicTest> specExamples() {
    var spec = SchemaSpecification.load(SPEC);
    var config = PlanetilerConfig.defaults();
    var profile = new Shortbread(config);
    var result = BaseSchemaValidator.validate(profile, spec, config);
    return result.results().stream()
      .map(example -> dynamicTest(example.example().name(), () -> {
        var issues = example.issues().get();
        if (!issues.isEmpty()) {
          throw new AssertionError(
            "Example \"" + example.example().name() + "\" failed:\n  " + String.join("\n  ", issues));
        }
      }))
      .toList();
  }
}
