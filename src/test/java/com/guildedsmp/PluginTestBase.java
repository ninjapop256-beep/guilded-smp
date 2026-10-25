package com.guildedsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

/** Boots a MockBukkit server with the real plugin for each test. */
public abstract class PluginTestBase {

    /** ServerMock plus the bits MockBukkit leaves unimplemented that the plugin relies on. */
    @SuppressWarnings("unchecked") // ServerMock's inherited raw getBanList(Type) signature
    public static class TestServer extends ServerMock {
        public boolean stopping;

        @Override
        public boolean isStopping() {
            return stopping;
        }

        /** MockBukkit only implements the older BanList.Type lookup; route the modern one to it. */
        @Override
        @SuppressWarnings({"unchecked", "deprecation", "rawtypes"})
        public <B extends org.bukkit.BanList<E>, E> B getBanList(io.papermc.paper.ban.BanListType<B> type) {
            return (B) getBanList(type == io.papermc.paper.ban.BanListType.PROFILE
                    ? org.bukkit.BanList.Type.PROFILE : org.bukkit.BanList.Type.IP);
        }
    }

    protected TestServer server;
    protected WorldMock world;
    protected GuildedSMP plugin;

    @BeforeEach
    void bootServer() {
        server = MockBukkit.mock(new TestServer());
        server.setOnlineMode(true);
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(GuildedSMP.class);
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    /** A survival player standing on stone at (x, 64, z). */
    protected PlayerMock player(String name, double x, double z) {
        PlayerMock p = server.addPlayer(name);
        p.setGameMode(GameMode.SURVIVAL);
        floor((int) Math.floor(x), (int) Math.floor(z));
        p.setLocation(new Location(world, x, 64, z));
        drain(p);
        return p;
    }

    protected PlayerMock player(String name) {
        return player(name, 1000.5, 1000.5);
    }

    protected void floor(int x, int z) {
        world.loadChunk(x >> 4, z >> 4);
        world.getBlockAt(x, 63, z).setType(Material.STONE);
        world.getBlockAt(x, 64, z).setType(Material.AIR);
        world.getBlockAt(x, 65, z).setType(Material.AIR);
    }

    protected static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    protected static List<String> drain(PlayerMock p) {
        List<String> out = new ArrayList<>();
        Component c;
        while ((c = p.nextComponentMessage()) != null) {
            out.add(plain(c));
        }
        return out;
    }

    protected static boolean said(List<String> messages, String fragment) {
        return messages.stream().anyMatch(m -> m.contains(fragment));
    }

    /** Fires a melee hit through the real event pipeline. */
    @SuppressWarnings("removal") // the only public constructor that takes a DamageSource
    protected EntityDamageByEntityEvent melee(Entity attacker, Entity victim, double damage) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(attacker).withDirectEntity(attacker).build();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, damage);
        server.getPluginManager().callEvent(event);
        return event;
    }
}
