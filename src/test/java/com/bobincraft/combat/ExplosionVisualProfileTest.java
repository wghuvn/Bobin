package com.bobincraft.combat;
import org.junit.jupiter.api.Test;
final class ExplosionVisualProfileTest {
    @Test void layeredContract() { Stage21Contract.main(new String[0]); }
    @Test void visualContract() throws Exception { ExplosionVisualContract.runAll(); }
}
