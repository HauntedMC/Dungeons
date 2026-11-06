package nl.hauntedmc.dungeons.content.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.UUID;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class RewardClaimContractTest {
    @Test
    void expiringAnOldClaimCannotEraseANewerClaimAcrossWorldCopiesOrAffectAnotherPlayer() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var another = mock(Player.class);
        when(another.getUniqueId()).thenReturn(UUID.randomUUID());
        var firstRun = mock(RewardFunction.class);
        var secondRun = mock(RewardFunction.class);
        when(firstRun.getLocation()).thenReturn(new Location(mock(World.class), 1, 2, 3));
        when(secondRun.getLocation()).thenReturn(new Location(mock(World.class), 1, 2, 3));
        var claims = new PlayerLootData(player);
        claims.addLootCooldown(firstRun, new Date(1000));
        LootCooldown expired = claims.getLootCooldown(firstRun);
        claims.addLootCooldown(secondRun, new Date(9000000000000L));
        LootCooldown latest = claims.getLootCooldown(secondRun);
        claims.removeLootCooldown(expired);
        assertSame(latest, claims.getLootCooldown(firstRun));
        assertTrue(claims.hasLootOnCooldown(secondRun));
        assertFalse(new PlayerLootData(another).hasLootOnCooldown(secondRun));
        assertTrue(new PlayerLootData(claims.serialize()).hasLootOnCooldown(secondRun));
    }
}
