package com.guildedsmp;

import com.guildedsmp.combat.CombatListener;
import com.guildedsmp.combat.CombatManager;
import com.guildedsmp.command.GuildedCommand;
import com.guildedsmp.config.ConfigMigrator;
import com.guildedsmp.config.Messages;
import com.guildedsmp.config.Settings;
import com.guildedsmp.drops.IngredientDrops;
import com.guildedsmp.home.HomeCommands;
import com.guildedsmp.home.HomeManager;
import com.guildedsmp.identity.IdentityRegistry;
import com.guildedsmp.items.CustomItems;
import com.guildedsmp.items.ItemProtectionListener;
import com.guildedsmp.legendary.LegendaryCombatListener;
import com.guildedsmp.legendary.PassiveEffects;
import com.guildedsmp.rules.OwnerCraftRules;
import com.guildedsmp.moderation.ChatListener;
import com.guildedsmp.moderation.ModerationCommands;
import com.guildedsmp.moderation.MuteManager;
import com.guildedsmp.opprotect.OperatorProtection;
import com.guildedsmp.pack.ResourcePackManager;
import com.guildedsmp.pack.SoundService;
import com.guildedsmp.recipes.LegendaryRecipes;
import com.guildedsmp.recipes.RecipeListener;
import com.guildedsmp.recipes.RecipeMenu;
import com.guildedsmp.spawn.BoundaryOutline;
import com.guildedsmp.spawn.SpawnCommand;
import com.guildedsmp.spawn.SpawnJoinListener;
import com.guildedsmp.spawn.SpawnManager;
import com.guildedsmp.spawn.SpawnProtectCommand;
import com.guildedsmp.spawn.SpawnProtectionListener;
import com.guildedsmp.storage.IoExecutor;
import com.guildedsmp.storage.YamlStore;
import com.guildedsmp.tab.TabListManager;
import com.guildedsmp.teleport.TeleportService;
import com.guildedsmp.teleport.TpaCommands;
import com.guildedsmp.teleport.TpaManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/** Guilded SMP core plugin entry point. */
public class GuildedSMP extends JavaPlugin {

    private volatile Settings settings;
    private Messages messages;
    private Keys keys;
    private IoExecutor io;
    private CustomItems items;
    private IdentityRegistry identities;
    private OperatorProtection operatorProtection;
    private CombatManager combat;
    private TeleportService teleports;
    private TpaManager tpa;
    private HomeManager homes;
    private SpawnManager spawn;
    private BoundaryOutline outline;
    private SpawnProtectionListener spawnProtection;
    private LegendaryRecipes recipes;
    private RecipeMenu recipeMenu;
    private PassiveEffects passiveEffects;
    private LegendaryCombatListener legendaryCombat;
    private IngredientDrops drops;
    private MuteManager mutes;
    private TabListManager tabList;
    private ResourcePackManager resourcePack;
    private SoundService sounds;
    private OwnerCraftRules ownerRules;

    @Override
    public void onEnable() {
        io = new IoExecutor(getLogger());
        ConfigMigrator.migrate(this);
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages = new Messages(getConfig());
        keys = new Keys(this);
        items = new CustomItems(keys);

        identities = new IdentityRegistry(store("identities.yml"), getLogger());
        operatorProtection = new OperatorProtection(this);
        combat = new CombatManager(this);
        teleports = new TeleportService(this);
        tpa = new TpaManager(this);
        homes = new HomeManager(this, store("homes.yml"));
        spawn = new SpawnManager(this, store("spawn.yml"));
        outline = new BoundaryOutline(this);
        spawnProtection = new SpawnProtectionListener(this);
        recipes = new LegendaryRecipes(this);
        recipeMenu = new RecipeMenu(this);
        passiveEffects = new PassiveEffects(this);
        legendaryCombat = new LegendaryCombatListener(this);
        drops = new IngredientDrops(this);
        mutes = new MuteManager(this, store("mutes.yml"));
        tabList = new TabListManager(this);
        resourcePack = new ResourcePackManager(this);
        sounds = new SoundService(this);
        ownerRules = new OwnerCraftRules(this, store("craft-limits.yml"));

        // Combat start cancels every pending teleport and teleport request of that player.
        combat.onCombatStart(p -> {
            teleports.cancel(p, "teleport.cancelled-combat");
            tpa.cancelAllFor(p, "tpa.cancelled-combat");
        });
        spawn.onChange(outline::rebuild);

        PluginManager pm = getServer().getPluginManager();
        List<Listener> listeners = List.of(operatorProtection, new CombatListener(this), teleports, tpa, spawn,
                outline, spawnProtection, new SpawnJoinListener(this), new ItemProtectionListener(this),
                new RecipeListener(this), recipeMenu, passiveEffects, legendaryCombat, drops, new ChatListener(this),
                tabList, resourcePack, ownerRules);
        for (Listener l : listeners) {
            pm.registerEvents(l, this);
        }

        TpaCommands tpaCommands = new TpaCommands(this);
        HomeCommands homeCommands = new HomeCommands(this);
        ModerationCommands moderation = new ModerationCommands(this);
        SpawnCommand spawnCommand = new SpawnCommand(this);
        SpawnProtectCommand spawnProtect = new SpawnProtectCommand(this);
        GuildedCommand guilded = new GuildedCommand(this);
        for (String name : List.of("tpa", "tpaccept", "tpdeny", "tpacancel")) {
            bind(name, tpaCommands, tpaCommands);
        }
        for (String name : List.of("home", "sethome", "delhome", "homes")) {
            bind(name, homeCommands, homeCommands);
        }
        for (String name : List.of("mute", "unmute", "tempban", "unban")) {
            bind(name, moderation, moderation);
        }
        bind("spawn", spawnCommand, spawnCommand);
        bind("spawnprotect", spawnProtect, spawnProtect);
        bind("guilded", guilded, guilded);

        startServices(false);
        getLogger().info("Guilded SMP " + getPluginMeta().getVersion() + " enabled.");
    }

    private void bind(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(completer);
    }

    private YamlStore store(String fileName) {
        return new YamlStore(new File(new File(getDataFolder(), "data"), fileName), io, getLogger());
    }

    private void startServices(boolean resendRecipes) {
        operatorProtection.start();
        spawnProtection.start();
        passiveEffects.start();
        recipes.register(resendRecipes);
        outline.purgeStale();
        outline.rebuild();
        tabList.start();
        resourcePack.checkConflicts();
    }

    private void stopServices() {
        if (tabList != null) {
            tabList.stop();
        }
        if (outline != null) {
            outline.clear();
        }
        if (passiveEffects != null) {
            passiveEffects.stop();
        }
        if (spawnProtection != null) {
            spawnProtection.stop();
        }
        if (operatorProtection != null) {
            operatorProtection.stop();
        }
    }

    /** /guilded reload: re-reads config.yml and restarts timers. Data files are not touched. */
    public List<String> reloadAll() {
        ConfigMigrator.migrate(this);
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages.reload(getConfig());
        stopServices();
        startServices(true);
        return settings.warnings();
    }

    @Override
    public void onDisable() {
        stopServices();
        if (combat != null) {
            combat.clearAll();
        }
        if (teleports != null) {
            teleports.shutdown();
        }
        if (tpa != null) {
            tpa.shutdown();
        }
        if (recipes != null) {
            recipes.unregister(false);
        }
        if (io != null) {
            io.shutdown();
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Keys keys() {
        return keys;
    }

    public CustomItems items() {
        return items;
    }

    public IdentityRegistry identities() {
        return identities;
    }

    public OperatorProtection operatorProtection() {
        return operatorProtection;
    }

    public CombatManager combat() {
        return combat;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public TpaManager tpa() {
        return tpa;
    }

    public HomeManager homes() {
        return homes;
    }

    public SpawnManager spawn() {
        return spawn;
    }

    public BoundaryOutline outline() {
        return outline;
    }

    public LegendaryRecipes recipes() {
        return recipes;
    }

    public RecipeMenu recipeMenu() {
        return recipeMenu;
    }

    public LegendaryCombatListener legendaryCombat() {
        return legendaryCombat;
    }

    public IngredientDrops drops() {
        return drops;
    }

    public PassiveEffects passiveEffects() {
        return passiveEffects;
    }

    public MuteManager mutes() {
        return mutes;
    }

    public TabListManager tabList() {
        return tabList;
    }

    public ResourcePackManager resourcePack() {
        return resourcePack;
    }

    public SoundService sounds() {
        return sounds;
    }

    public OwnerCraftRules ownerRules() {
        return ownerRules;
    }
}
