package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The default stocking area scan resolves sign text to placements through an
 * alias table; these tests pin down which placement each alias resolves to.
 */
final class PlacementAliasTest {

    private static ServerPlacement placement(final String fileName, final String displayName) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), fileName, UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
        if (displayName != null) {
            placement.setDisplayName(displayName);
        }
        return placement;
    }

    @Test
    void fileNamesResolveToCanonicalDisplayNames() {
        final ServerPlacement placement = placement("castle_v2", "Castle");

        final Map<String, String> aliases =
                MaterialService.buildPlacementAliases(Collections.singleton(placement));

        assertEquals("Castle", aliases.get("castle_v2"));
        assertEquals("Castle", aliases.get("Castle"));
    }

    @Test
    void fileNameAliasesResolveWhenNoDisplayNameExists() {
        final ServerPlacement placement = placement("farm", null);

        final Map<String, String> aliases =
                MaterialService.buildPlacementAliases(Collections.singleton(placement));

        // getName() falls back to the file name, so both aliases collapse to one entry.
        assertEquals(1, aliases.size());
        assertEquals("farm", aliases.get("farm"));
    }

    @Test
    void displayNameWinsOverAnotherPlacementFileName() {
        final ServerPlacement castle = placement("castle", "Castle");
        final ServerPlacement tower = placement("Castle", "Tower");

        final Map<String, String> aliases =
                MaterialService.buildPlacementAliases(Arrays.asList(castle, tower));

        assertEquals("Castle", aliases.get("castle"));
        assertEquals("Castle", aliases.get("Castle"));
        assertEquals("Tower", aliases.get("Tower"));
    }

    @Test
    void emptyCollectionYieldsNoAliases() {
        final Collection<ServerPlacement> placements = Collections.emptyList();

        assertTrue(MaterialService.buildPlacementAliases(placements).isEmpty());
    }
}
