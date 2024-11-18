package nl.ramsolutions.sw.magik.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import nl.ramsolutions.sw.MagikToolsProperties;
import org.junit.jupiter.api.Test;

/** Tests for {@link CheckList}. */
class CheckListTest {

  @Test
  void testAllChecksHaveAJsonFile() throws IOException {
    for (Class<? extends MagikCheck> checkClass : CheckList.getChecks()) {
      final MagikCheckHolder holder =
          new MagikCheckHolder(
              checkClass, Collections.emptySet(), true, new MagikToolsProperties());
      final MagikCheckMetadata metadata = holder.getMetadata();
      assertThat(metadata).isNotNull();
    }
  }
}
