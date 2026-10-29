package com.bobincraft.armament;

import com.bobincraft.armament.api.*;
import com.bobincraft.armament.definition.*;
import com.bobincraft.armament.serialization.*;
import com.bobincraft.armament.validation.DefinitionValidationException;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

final class ApiContractTest {
    private static final UUID OWNER = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID INSTANCE = UUID.fromString("abcdef12-1234-1234-1234-123456789abc");
    private static final WeaponId ID = WeaponId.parse("test:fixture");
    private static final WeaponId ACTION = WeaponId.parse("test:inspect");

    @Test void identifiersAreExplicitCanonicalValues() {
        assertEquals(new WeaponId("test", "models/fixture.glb"), WeaponId.parse("test:models/fixture.glb"));
        assertEquals("test:fixture", ID.toString());
        assertEquals(ID, WeaponId.parse(ID.toString()));
    }

    @TestFactory Stream<DynamicTest> unsafeIdentifiersAreRejected() {
        return Stream.of("fixture", ":fixture", "test:", "test:a:b", "Test:a", "test:../a", "test:a/../b",
                "test:a//b", "test:/a", "test:a/", "test:a\\b", "test:a%2fb", "test:a b", "test:a\n",
                ".:fixture", "..:fixture", "test..x:a", "https://host/a.glb", "test:" + "a".repeat(251)).map(value -> DynamicTest.dynamicTest(value,
                () -> assertThrows(DefinitionValidationException.class, () -> WeaponId.parse(value))));
    }

    @Test void nullsHaveDomainDiagnostics() {
        assertThrows(DefinitionValidationException.class, () -> WeaponId.parse(null));
        assertThrows(DefinitionValidationException.class, () -> new WeaponDefinition(1, ID, null));
        assertThrows(DefinitionValidationException.class, () -> new WeaponDefinition(1, null, Set.of()));
        assertThrows(DefinitionValidationException.class, () -> new WeaponDefinition(1, ID, new HashSet<>(Arrays.asList(ACTION, null))));
    }

    @Test void definitionIsImmutableAndHasNoRuntimeRegistration() {
        Set<WeaponId> input = new HashSet<>(Set.of(ACTION));
        WeaponDefinition definition = new WeaponDefinition(1, ID, input);
        input.clear();
        assertEquals(Set.of(ACTION), definition.actions());
        assertThrows(UnsupportedOperationException.class, () -> definition.actions().clear());
        assertThrows(DefinitionValidationException.class, () -> new WeaponDefinition(2, ID, Set.of()));
    }

    @Test void idleAndActiveSnapshotsRoundTripWithoutLosingLongPrecision() {
        for (Optional<WeaponId> action : List.of(Optional.<WeaponId>empty(), Optional.of(ACTION))) {
            WeaponStateSnapshot value = snapshot(Long.MAX_VALUE, action, 9, action.isPresent() ? Long.MAX_VALUE - 1 : 0);
            assertEquals(value, WeaponJson.readSnapshot(WeaponJson.writeSnapshot(value)));
        }
        assertEquals(WeaponUserView.Hand.OFF_HAND, WeaponJson.readSnapshot(WeaponJson.writeSnapshot(
                new WeaponStateSnapshot(OWNER, INSTANCE, ID, WeaponUserView.Hand.OFF_HAND, 0, 0, Optional.empty(), 0, 0))).hand());
    }

    @Test void snapshotsRejectImpossibleTimeAndActionCombinations() {
        assertThrows(DefinitionValidationException.class, () -> snapshot(-1, Optional.empty(), 0, 0));
        assertThrows(DefinitionValidationException.class, () -> snapshot(5, Optional.of(ACTION), 1, 6));
        assertThrows(DefinitionValidationException.class, () -> snapshot(5, Optional.of(ACTION), 0, 1));
        assertThrows(DefinitionValidationException.class, () -> snapshot(5, Optional.empty(), 1, 1));
        assertThrows(DefinitionValidationException.class, () -> snapshot(5, Optional.empty(), -1, 0));
        assertThrows(DefinitionValidationException.class, () -> snapshot(5, null, 0, 0));
        assertThrows(DefinitionValidationException.class, () -> new WeaponStateSnapshot(OWNER, INSTANCE, ID,
                WeaponUserView.Hand.MAIN_HAND, 1, -1, Optional.empty(), 0, 0));
    }

    @Test void actionEventsRoundTripAndRequireSequenceAndIdentity() {
        WeaponActionEvent event = event(ACTION, Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(event, WeaponJson.readActionEvent(WeaponJson.writeActionEvent(event)));
        assertThrows(DefinitionValidationException.class, () -> event(ACTION, 0, 0));
        assertThrows(DefinitionValidationException.class, () -> event(ACTION, 1, -1));
        assertThrows(DefinitionValidationException.class, () -> event(null, 1, 0));
    }

    @Test void semanticChecksRejectUnknownActionsAndWrongDefinition() {
        WeaponDefinition definition = new WeaponDefinition(1, ID, Set.of(ACTION));
        assertDoesNotThrow(() -> WeaponDefinitionValidator.validate(definition, event(ACTION, 1, 0)));
        assertDoesNotThrow(() -> WeaponDefinitionValidator.validate(definition, snapshot(1, Optional.empty(), 0, 0)));
        assertThrows(DefinitionValidationException.class, () -> WeaponDefinitionValidator.validate(definition, event(WeaponId.parse("test:unknown"), 1, 0)));
        assertThrows(DefinitionValidationException.class, () -> WeaponDefinitionValidator.validate(
                new WeaponDefinition(1, WeaponId.parse("test:other"), Set.of(ACTION)), event(ACTION, 1, 0)));
        assertThrows(DefinitionValidationException.class, () -> WeaponDefinitionValidator.validate(
                new WeaponDefinition(1, ID, Set.of()), snapshot(1, Optional.of(ACTION), 1, 0)));
    }

    @Test void userViewCanDescribeANonPlayerWithoutMinecraft() {
        WeaponStateSnapshot value = snapshot(0, Optional.empty(), 0, 0);
        WeaponUserView npc = new WeaponUserView() {
            public UUID userId() { return OWNER; }
            public Optional<WeaponStateSnapshot> weaponState(Hand hand) {
                return hand == Hand.MAIN_HAND ? Optional.of(value) : Optional.empty();
            }
        };
        assertEquals(npc.userId(), npc.weaponState(WeaponUserView.Hand.MAIN_HAND).orElseThrow().ownerId());
        assertTrue(npc.weaponState(WeaponUserView.Hand.OFF_HAND).isEmpty());
    }

    @Test void snapshotCodecRejectsCoercionOverflowUnknownAndNullFields() {
        String valid = WeaponJson.writeSnapshot(snapshot(1, Optional.empty(), 0, 0));
        for (String invalid : List.of(valid.replace("\"server_tick\":1", "\"server_tick\":\"1\""),
                valid.replace("\"server_tick\":1", "\"server_tick\":1.5"),
                valid.replace("\"server_tick\":1", "\"server_tick\":9223372036854775808"),
                valid.replace("\"main_hand\"", "\"MAIN_HAND\""),
                valid.replace("\"main_hand\"", "null"),
                valid.replace(OWNER.toString(), "1-1-1-1-1"),
                valid.replace("\"action_sequence\":0", "\"action_id\":null,\"action_sequence\":0"),
                valid.replace("\"revision\":0", "\"revision\":0,\"damage\":999"))) {
            assertThrows(DefinitionValidationException.class, () -> WeaponJson.readSnapshot(invalid), invalid);
        }
    }

    private static WeaponStateSnapshot snapshot(long tick, Optional<WeaponId> action, long sequence, long start) {
        return new WeaponStateSnapshot(OWNER, INSTANCE, ID, WeaponUserView.Hand.MAIN_HAND, tick, 0, action, sequence, start);
    }

    private static WeaponActionEvent event(WeaponId action, long sequence, long start) {
        return new WeaponActionEvent(OWNER, INSTANCE, ID, WeaponUserView.Hand.MAIN_HAND, action, sequence, start);
    }
}
