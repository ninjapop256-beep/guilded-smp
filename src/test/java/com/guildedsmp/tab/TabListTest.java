package com.guildedsmp.tab;

import com.guildedsmp.PluginTestBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabListTest extends PluginTestBase {

    /** MockBukkit's PlayerMock ignores Adventure's header/footer call; record it instead. */
    static final class TabPlayer extends PlayerMock {
        Component header;
        Component footer;
        int sends;

        TabPlayer(org.mockbukkit.mockbukkit.ServerMock server, String name) {
            super(server, name);
        }

        @Override
        public void sendPlayerListHeaderAndFooter(Component header, Component footer) {
            this.header = header;
            this.footer = footer;
            sends++;
        }
    }

    private TabPlayer tabPlayer(String name) {
        TabPlayer p = new TabPlayer(server, name);
        server.addPlayer(p);
        return p;
    }

    /** Every text leaf with the colour it will actually render in (inherited if not set). */
    private static void leaves(Component c, TextColor inherited, List<Object[]> out) {
        TextColor color = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            out.add(new Object[]{t.content(), color});
        }
        for (Component child : c.children()) {
            leaves(child, color, out);
        }
    }

    @Test
    void brandWordsHaveExactlyTheRequiredColours() {
        for (boolean shadow : new boolean[]{true, false}) {
            Component brand = TabListManager.brand(shadow, 0xFFA0A0A0);
            assertNull(brand.color(), "no colour on the parent, so nothing is inherited");
            List<Object[]> parts = new ArrayList<>();
            leaves(brand, null, parts);
            assertEquals("GUILDED", parts.get(0)[0]);
            assertEquals(0x00FFFF, ((TextColor) parts.get(0)[1]).value());
            assertEquals(" ", parts.get(1)[0]);
            assertEquals("SMP", parts.get(2)[0]);
            assertEquals(0x000000, ((TextColor) parts.get(2)[1]).value());
            assertEquals(3, parts.size());
            assertEquals("GUILDED SMP", plain(brand));
        }
    }

    @Test
    void playersGetHeaderAndLiveFooter() {
        TabPlayer a = tabPlayer("Alpha");
        TabPlayer b = tabPlayer("Beta");
        server.getScheduler().performTicks(41);
        assertTrue(plain(a.header).contains("GUILDED SMP"));
        String footer = plain(a.footer);
        assertTrue(footer.contains("Ping: " + a.getPing() + " ms"), footer);
        assertTrue(footer.contains("Players Online: 2/" + server.getMaxPlayers()), footer);

        b.disconnect();
        server.getScheduler().performTicks(41);
        assertTrue(plain(a.footer).contains("Players Online: 1/"), "updates after a player leaves");

        int sends = a.sends;
        server.getScheduler().performTicks(400);
        assertEquals(sends, a.sends, "nothing changed, so nothing is re-sent");
    }

    @Test
    void footerHandlesUnknownPing() {
        String footer = plain(plugin.tabList().footer(-1, 3, 20));
        assertTrue(footer.contains("Ping: ? ms"));
        assertTrue(footer.contains("3/20"));
    }
}
