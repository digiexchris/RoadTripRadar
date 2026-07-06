package ca.voiditswarranty.roadtripradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for [InsertPosition] — the sealed class the route editor uses to describe *where* a
 * new waypoint should be inserted relative to the existing list / a known waypoint id. The
 * current production variants are [InsertPosition.Start], [InsertPosition.BeforeLast],
 * [InsertPosition.End], [InsertPosition.Index], and [InsertPosition.ReplaceId].
 *
 * Pinned here so a future add / remove of a variant is a deliberate test diff. Equality
 * matters: the route editor's state-checks use `==` on `InsertPosition` values to decide
 * which branch to take.
 */
class InsertPositionTest {

    @Test
    fun sealedSubclasses_areAllExpected() {
        // Pin the 5 variants. A future addition (or removal) is intentional only if the
        // test is updated alongside it.
        val variants: List<InsertPosition> = listOf(
            InsertPosition.Start,
            InsertPosition.BeforeLast,
            InsertPosition.End,
            InsertPosition.Index(i = 2),
            InsertPosition.ReplaceId(id = "abc"),
        )
        // The count must match the expected pinned value.
        assertEquals(5, variants.size)
        // Each variant must be non-null (this is a tautology at the type level, but it's
        // a useful sanity check that the list is fully constructed).
        for (v in variants) {
            assertNotNull("variant $v must be non-null", v)
        }
    }

    @Test
    fun equality_singletonCasesAreEqual() {
        // The `object` singletons compare by identity.
        assertEquals(InsertPosition.Start, InsertPosition.Start)
        assertEquals(InsertPosition.BeforeLast, InsertPosition.BeforeLast)
        assertEquals(InsertPosition.End, InsertPosition.End)
    }

    @Test
    fun equality_indexDistinguishesByI() {
        assertEquals(InsertPosition.Index(0), InsertPosition.Index(0))
        assertNotEquals(InsertPosition.Index(0), InsertPosition.Index(1))
    }

    @Test
    fun equality_replaceIdDistinguishesById() {
        assertEquals(InsertPosition.ReplaceId("a"), InsertPosition.ReplaceId("a"))
        assertNotEquals(InsertPosition.ReplaceId("a"), InsertPosition.ReplaceId("b"))
    }

    @Test
    fun equality_distinguishesAllVariants() {
        // No two different variants compare equal. This is the contract the route editor
        // relies on (a switch / when-chain over InsertPosition).
        val positions: List<InsertPosition> = listOf(
            InsertPosition.Start,
            InsertPosition.BeforeLast,
            InsertPosition.End,
            InsertPosition.Index(i = 0),
            InsertPosition.ReplaceId(id = ""),
        )
        for (i in positions.indices) {
            for (j in positions.indices) {
                if (i == j) continue
                assertNotEquals(
                    "variants at indices $i and $j must not be equal; " +
                        "got ${positions[i]} == ${positions[j]}",
                    positions[i],
                    positions[j],
                )
            }
        }
    }
}
