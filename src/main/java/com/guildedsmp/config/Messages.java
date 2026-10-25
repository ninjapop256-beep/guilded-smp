package com.guildedsmp.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * MiniMessage-formatted messages from the {@code messages:} section of config.yml.
 * Player-supplied text (names, reasons) is always inserted with {@link #ph} which
 * escapes MiniMessage tags, so players cannot inject formatting or click events.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Swapped as a whole on reload, so async chat handlers always see a complete map. */
    private volatile Map<String, String> raw = Map.of();
    private volatile Component prefix = Component.empty();

    public Messages(ConfigurationSection root) {
        reload(root);
    }

    public void reload(ConfigurationSection root) {
        Map<String, String> next = new HashMap<>();
        ConfigurationSection section = root.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(true)) {
                if (section.isString(key)) {
                    next.put(key, section.getString(key));
                }
            }
        }
        raw = Map.copyOf(next);
        prefix = MM.deserialize(next.getOrDefault("prefix", ""));
    }

    public static TagResolver ph(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    public Component get(String key, TagResolver... resolvers) {
        String template = raw.get(key);
        if (template == null) {
            return Component.text("[GuildedSMP] missing message: " + key);
        }
        return MM.deserialize(template, resolvers);
    }

    public boolean has(String key) {
        String template = raw.get(key);
        return template != null && !template.isEmpty();
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        if (!has(key)) {
            return;
        }
        to.sendMessage(prefix.append(get(key, resolvers)));
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        return prefix.append(get(key, resolvers));
    }

    public static Component parse(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }
}
