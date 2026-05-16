package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.RewardItem;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.util.MaterialUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RewardManager {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ConfigManager configManager;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ScratchcardType> types = new LinkedHashMap<>();

    public RewardManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void reload() {
        types.clear();
        ConfigurationSection root = configManager.rewards().getConfigurationSection("scratchcards");
        if (root == null) {
            return;
        }

        for (String typeId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(typeId);
            if (section == null) {
                continue;
            }
            Material material = MaterialUtil.parse(section.getString("material"), Material.PAPER);
            Material shopMaterial = MaterialUtil.parse(section.getString("shop_material"), material);
            List<Reward> rewards = loadRewards(section.getConfigurationSection("rewards"));
            if (rewards.isEmpty()) {
                rewards.add(new Reward("nothing", 1.0D, "common", "&7Leider kein Gewinn", Material.BARRIER,
                        0.0D, List.of(), List.of(), false));
            }
            types.put(typeId.toLowerCase(), new ScratchcardType(
                    typeId.toLowerCase(),
                    section.getString("display_name", typeId),
                    material,
                    shopMaterial,
                    section.getDouble("price", 0.0D),
                    section.getBoolean("buyable", true),
                    parseDateMillis(section.getString("available_from", "")),
                    parseDateMillis(section.getString("expires_at", "")),
                    List.copyOf(rewards)
            ));
        }
    }

    private List<Reward> loadRewards(ConfigurationSection section) {
        List<Reward> rewards = new ArrayList<>();
        if (section == null) {
            return rewards;
        }
        for (String rewardId : section.getKeys(false)) {
            ConfigurationSection rewardSection = section.getConfigurationSection(rewardId);
            if (rewardSection == null) {
                continue;
            }
            rewards.add(new Reward(
                    rewardId.toLowerCase(),
                    Math.max(0.0D, rewardSection.getDouble("chance", 0.0D)),
                    rewardSection.getString("rarity", rewardSection.getString("tier", "common")).toLowerCase(),
                    rewardSection.getString("display_name", rewardId),
                    MaterialUtil.parse(rewardSection.getString("symbol_material"), Material.CHEST),
                    rewardSection.getDouble("money", 0.0D),
                    loadItems(rewardSection.getMapList("items")),
                    rewardSection.getStringList("commands"),
                    rewardSection.getBoolean("broadcast", false)
            ));
        }
        return rewards;
    }

    private long parseDateMillis(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException exception) {
            return 0L;
        }
    }

    private List<RewardItem> loadItems(List<Map<?, ?>> maps) {
        List<RewardItem> items = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            Material material = MaterialUtil.parse(String.valueOf(map.get("material")), Material.STONE);
            int amount = parseInt(map.get("amount"), 1);
            String name = map.containsKey("name") ? String.valueOf(map.get("name")) : "";
            List<String> lore = new ArrayList<>();
            Object loreValue = map.get("lore");
            if (loreValue instanceof List<?> loreList) {
                for (Object line : loreList) {
                    lore.add(String.valueOf(line));
                }
            }
            items.add(new RewardItem(material, amount, name, lore));
        }
        return items;
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public Optional<ScratchcardType> type(String id) {
        return Optional.ofNullable(types.get(id == null ? "" : id.toLowerCase()));
    }

    public List<ScratchcardType> types() {
        return List.copyOf(types.values());
    }

    public double totalChance(ScratchcardType type) {
        return type.rewards().stream().mapToDouble(Reward::chance).sum();
    }

    public double effectiveChancePercent(ScratchcardType type, Reward reward) {
        double total = totalChance(type);
        if (total <= 0.0D) {
            return 0.0D;
        }
        return (reward.chance() / total) * 100.0D;
    }

    public double winChancePercent(ScratchcardType type) {
        double total = totalChance(type);
        if (total <= 0.0D) {
            return 0.0D;
        }
        double wins = type.rewards().stream()
                .filter(Reward::isWin)
                .mapToDouble(Reward::chance)
                .sum();
        return (wins / total) * 100.0D;
    }

    public Reward chooseReward(ScratchcardType type) {
        return chooseReward(type, 1.0D);
    }

    public Reward chooseReward(ScratchcardType type, double winChanceMultiplier) {
        double multiplier = Math.max(0.0D, winChanceMultiplier);
        double total = type.rewards().stream().mapToDouble(reward -> effectiveRollWeight(reward, multiplier)).sum();
        if (total <= 0.0D) {
            return type.rewards().get(random.nextInt(type.rewards().size()));
        }
        double roll = random.nextDouble(total);
        double current = 0.0D;
        for (Reward reward : type.rewards()) {
            current += effectiveRollWeight(reward, multiplier);
            if (roll <= current) {
                return reward;
            }
        }
        return type.rewards().getLast();
    }

    private double effectiveRollWeight(Reward reward, double winChanceMultiplier) {
        if (reward.isWin()) {
            return reward.chance() * winChanceMultiplier;
        }
        return reward.chance();
    }

    public List<String> createSymbols(ScratchcardType type, Reward selectedReward, int slots, int winningMatches) {
        List<String> symbols = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();

        if (selectedReward.isWin()) {
            for (int i = 0; i < winningMatches && symbols.size() < slots; i++) {
                symbols.add(selectedReward.id());
                counts.merge(selectedReward.id(), 1, Integer::sum);
            }
        }

        List<Reward> decoys = new ArrayList<>(type.rewards().stream()
                .filter(reward -> !reward.id().equalsIgnoreCase(selectedReward.id()))
                .toList());
        if (decoys.isEmpty()) {
            decoys.add(selectedReward);
        }

        int guard = 0;
        while (symbols.size() < slots && guard++ < 500) {
            Reward reward = decoys.get(random.nextInt(decoys.size()));
            int current = counts.getOrDefault(reward.id(), 0);
            if (current >= Math.max(1, winningMatches - 1) && decoys.size() > 1) {
                continue;
            }
            symbols.add(reward.id());
            counts.merge(reward.id(), 1, Integer::sum);
        }

        while (symbols.size() < slots) {
            symbols.add(selectedReward.id());
        }

        Collections.shuffle(symbols, random);
        return symbols;
    }
}
