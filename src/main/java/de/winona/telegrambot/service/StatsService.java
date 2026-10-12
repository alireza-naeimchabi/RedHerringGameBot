package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.PlayerStats;
import de.winona.telegrambot.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Keeps the results of closed cases per player and stores them in a small properties file,
 * so statistics and the leaderboard survive a restart of the bot.
 */
public class StatsService {

    public static final Path DEFAULT_FILE = Path.of("data", "player-stats.properties");

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    private final Map<Long, PlayerStats> statsByUser = new HashMap<>();
    private final Path file;

    public StatsService() {
        this(DEFAULT_FILE);
    }

    public StatsService(Path file) {
        this.file = file;
        load();
    }

    /**
     * Counts a finished case for every real player. Practice cases are ignored.
     *
     * @param investigatorsWon true if the crime was solved, false if the murderer escaped
     * @param solverId         user id of the player who solved the crime, or null
     */
    public void recordGame(Game game, boolean investigatorsWon, Long solverId) {
        if (game.isPractice()) {
            return;
        }
        game.getRoles().forEach((player, role) -> {
            boolean won = (role == Role.MURDERER) != investigatorsWon;
            boolean solved = solverId != null && solverId == player.userId();
            statsFor(player).addGame(player.name(), role, won, solved);
        });
        save();
    }

    public Optional<PlayerStats> statsOf(long userId) {
        return Optional.ofNullable(statsByUser.get(userId));
    }

    /**
     * Players ordered by wins, then by win rate, then by fewer games.
     */
    public List<PlayerStats> leaderboard(int limit) {
        return ranking().stream().limit(limit).toList();
    }

    /**
     * Position on the leaderboard, starting at 1. Empty if the player has no closed case.
     */
    public Optional<Integer> rankOf(long userId) {
        List<PlayerStats> ranking = ranking();
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getUserId() == userId) {
                return Optional.of(i + 1);
            }
        }
        return Optional.empty();
    }

    private List<PlayerStats> ranking() {
        return statsByUser.values().stream()
                .filter(stats -> stats.getGames() > 0)
                .sorted(Comparator.comparingInt(PlayerStats::getWins).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerStats::winRatePercent).reversed())
                        .thenComparingInt(PlayerStats::getGames)
                        .thenComparing(PlayerStats::getName))
                .toList();
    }

    private PlayerStats statsFor(Player player) {
        return statsByUser.computeIfAbsent(player.userId(), id -> new PlayerStats(id, player.name()));
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            log.warn("Could not read statistics from {}. Starting with empty statistics.", file, e);
            return;
        }

        Set<Long> userIds = new HashSet<>();
        for (String key : properties.stringPropertyNames()) {
            try {
                userIds.add(Long.parseLong(key.substring(0, key.indexOf('.'))));
            } catch (RuntimeException e) {
                log.warn("Ignoring unreadable statistics entry '{}'.", key);
            }
        }
        for (long id : userIds) {
            statsByUser.put(id, new PlayerStats(id,
                    properties.getProperty(id + ".name", "Detective"),
                    number(properties, id + ".games"),
                    number(properties, id + ".wins"),
                    number(properties, id + ".scientist"),
                    number(properties, id + ".murderer"),
                    number(properties, id + ".investigator"),
                    number(properties, id + ".solved"),
                    number(properties, id + ".escapes")));
        }
    }

    private int number(Properties properties, String key) {
        try {
            return Math.max(0, Integer.parseInt(properties.getProperty(key, "0").trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void save() {
        Properties properties = new Properties();
        statsByUser.forEach((id, stats) -> {
            properties.setProperty(id + ".name", stats.getName());
            properties.setProperty(id + ".games", String.valueOf(stats.getGames()));
            properties.setProperty(id + ".wins", String.valueOf(stats.getWins()));
            properties.setProperty(id + ".scientist", String.valueOf(stats.getAsForensicScientist()));
            properties.setProperty(id + ".murderer", String.valueOf(stats.getAsMurderer()));
            properties.setProperty(id + ".investigator", String.valueOf(stats.getAsInvestigator()));
            properties.setProperty(id + ".solved", String.valueOf(stats.getCasesSolved()));
            properties.setProperty(id + ".escapes", String.valueOf(stats.getEscapes()));
        });
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "Player statistics - written by the bot, do not edit while it runs");
            }
        } catch (IOException e) {
            log.warn("Could not save statistics to {}. They stay in memory until the bot stops.", file, e);
        }
    }
}
