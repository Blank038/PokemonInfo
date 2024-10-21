package com.aiyostudio.pokemoninfo.internal.view;

import com.aiyostudio.pokemoninfo.internal.core.PokemonInfo;
import com.aiyostudio.pokemoninfo.api.event.PlayerConvertPokemonEvent;
import com.aiyostudio.pokemoninfo.internal.cache.PokemonCache;
import com.aiyostudio.pokemoninfo.internal.config.Configuration;
import com.aiyostudio.pokemoninfo.internal.dao.AbstractPersistenceDataImpl;
import com.aiyostudio.pokemoninfo.internal.i18n.I18n;
import com.aiyostudio.pokemoninfo.internal.manager.CacheManager;
import com.aiyostudio.pokemoninfo.internal.util.TextUtil;
import com.aystudio.core.bukkit.util.common.CommonUtil;
import com.aystudio.core.bukkit.util.inventory.GuiModel;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.utils.MinecraftVersion;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Blank038
 */
public class PokemonConvertView {
    private static final TConsumer<FileConfiguration, InventoryClickEvent, Integer> CONSUMER = (data, e, pokemonSlot) -> {
        e.setCancelled(true);
        if (e.getClickedInventory() == e.getInventory()) {
            ItemStack itemStack = e.getCurrentItem();
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return;
            }
            NBTItem nbtItem = new NBTItem(e.getCurrentItem());
            if (nbtItem.hasTag("PokemonConvertAction")) {
                Player clicker = (Player) e.getWhoClicked();
                if ("confirm".equals(nbtItem.getString("PokemonConvertAction"))) {
                    if (PokemonInfo.getModule().getPartyPokemonCount(clicker.getUniqueId()) == 1) {
                        clicker.sendMessage(I18n.getStrAndHeader("last-pokemon"));
                        return;
                    }
                    if (!PokemonInfo.getModule().isNullOrEgg(clicker.getUniqueId(), pokemonSlot)) {
                        FileConfiguration configuration = Configuration.getConvertModuleConfig();
                        Object pokemonObj = PokemonInfo.getModule().getPokemon(clicker.getUniqueId(), pokemonSlot);
                        String species = PokemonInfo.getModule().getSpecies(pokemonObj);

                        if (configuration.getStringList("black-list").contains(species)) {
                            clicker.sendMessage(I18n.getStrAndHeader("black-list"));
                            return;
                        }
                        if (PokemonInfo.getModule().getIVStoreValue(pokemonObj) > configuration.getInt("settings.maximum-ivs")) {
                            clicker.sendMessage(I18n.getStrAndHeader("maximum-ivs"));
                            return;
                        }
                        if (!configuration.getBoolean("settings.color") && PokemonInfo.getModule().getPokemonCustomName(pokemonObj).contains("§")) {
                            clicker.sendMessage(I18n.getStrAndHeader("color"));
                            return;
                        }
                        if (PokemonInfo.getModule().hasFlags(pokemonObj, configuration.getStringList("settings.flags").toArray(new String[0]))
                                || PokemonInfo.getModule().isCancelled(pokemonObj)) {
                            clicker.sendMessage(I18n.getStrAndHeader("denied"));
                            return;
                        }
                        PlayerConvertPokemonEvent event = new PlayerConvertPokemonEvent(clicker, pokemonObj);
                        Bukkit.getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            if (event.isNotify()) {
                                clicker.sendMessage(I18n.getStrAndHeader("denied"));
                            }
                            return;
                        }
                        PokemonInfo.getModule().retrieveAll(clicker.getUniqueId());
                        PokemonInfo.getModule().setPartyPokemon(clicker.getUniqueId(), pokemonSlot, null);
                        String uuid = UUID.randomUUID().toString();

                        NBTItem spriteItem = new NBTItem(PokemonConvertView.getPokemonItem(pokemonObj , false));
                        spriteItem.setString(CacheManager.getDataKey(), uuid);

                        PokemonCache pokemonCache = new PokemonCache(uuid, pokemonObj);
                        AbstractPersistenceDataImpl.getInstance().addPokemonCache(pokemonCache);

                        clicker.getInventory().addItem(spriteItem.getItem());
                        clicker.sendMessage(I18n.getStrAndHeader("convert"));
                    }
                }
                PartyView.open(clicker);
            }
        }
    };
    @Setter
    private static FileConfiguration data;


    public static void init() {
        String sourceFile = MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_13_R1) ? "view/convert.yml" : "view/legacy/convert.yml";
        PokemonInfo.getInstance().saveResource(sourceFile, "view/convert.yml", false, (file) -> {
            data = YamlConfiguration.loadConfiguration(file);
        });
    }

    public static void open(Player player, int pokemonSlot) {
        GuiModel model = new GuiModel(data.getString("title"), data.getInt("size"));
        model.registerListener(PokemonInfo.getInstance());
        model.setCloseRemove(true);

        Object displayPokemon = PokemonInfo.getModule().getPokemon(player.getUniqueId(), pokemonSlot);
        model.setItem(data.getInt("pokemon-slot"), PokemonConvertView.getPokemonItem(displayPokemon, true));

        if (data.getKeys(false).contains("items")) {
            for (String key : data.getConfigurationSection("items").getKeys(false)) {
                ConfigurationSection section = data.getConfigurationSection("items." + key);
                ItemStack itemStack = new ItemStack(Material.valueOf(section.getString("type")), section.getInt("amount"));
                ItemMeta meta = itemStack.getItemMeta();
                if (MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_13_R1)) {
                    ((Damageable) meta).setDamage((short) section.getInt("data"));
                    if (section.contains("custom-data")) {
                        meta.setCustomModelData(section.getInt("custom-data"));
                    }
                } else {
                    itemStack.setDurability((short) section.getInt("data"));
                }
                meta.setDisplayName(TextUtil.formatHexColor(section.getString("name")));
                List<String> lore = section.getStringList("lore");
                lore.replaceAll(TextUtil::formatHexColor);
                meta.setLore(lore);
                itemStack.setItemMeta(meta);

                if (section.contains("action")) {
                    NBTItem nbtItem = new NBTItem(itemStack);
                    nbtItem.setString("PokemonConvertAction", section.getString("action"));
                    itemStack = nbtItem.getItem();
                }

                for (int i : CommonUtil.formatSlots(section.getString("slot"))) {
                    model.setItem(i, itemStack);
                }
            }
        }
        model.execute((e) -> CONSUMER.run(data, e, pokemonSlot));
        model.openInventory(player);
    }

    public static ItemStack getPokemonItem(Object pokemon, boolean display) {
        ConfigurationSection section = data.getConfigurationSection("pokemon-item");
        ItemStack itemStack = PokemonInfo.getModule().getPokemonSpriteItem(pokemon);
        ItemMeta meta = itemStack.getItemMeta();
        if (MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_13_R1)) {
            ((Damageable) meta).setDamage((short) section.getInt("data"));
            if (section.contains("custom-data")) {
                meta.setCustomModelData(section.getInt("custom-data"));
            }
        } else {
            itemStack.setDurability((short) section.getInt("data"));
        }
        meta.setDisplayName(TextUtil.formatHexColor(section.getString("name"))
                .replace("%pokemon_name%", PokemonInfo.getModule().getPokemonTranslationName(pokemon))
                .replace("%shiny%", I18n.getOption("shiny." + (PokemonInfo.getModule().isShiny(pokemon) ? "t" : "f"))));
        List<String> lore = PokemonInfo.getModule().formatStats(pokemon, new ArrayList<>(section.getStringList(display ? "display-lore" : "sprite-lore")));
        lore.replaceAll(TextUtil::formatHexColor);
        meta.setLore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    @FunctionalInterface
    public interface TConsumer<T, U, A> {

        void run(T t, U u , A a);
    }
}
