package org.versatiles.shortbread;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.validator.BaseSchemaValidator;
import com.onthegomap.planetiler.validator.SchemaSpecification;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the example-based specification in {@code shortbread.spec.yml} against the {@link Shortbread} profile.
 * <p>
 * Each example describes an input feature and the vector-tile features it is expected to produce.
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
