package com.planetgallium.kitpvp.game;

import com.planetgallium.database.*;
import com.planetgallium.database.Record;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Infobase {

    private final Game plugin;
    private final Resources resources;

    private final Database database;
    private final Table statsTable;

    private final static int UUID_MAX_CHARACTERS = 36;
    private final static int USERNAME_MAX_CHARACTERS = 16;

    public Infobase(Game plugin) {
        this.plugin = plugin;
        this.resources = plugin.getResources();
        this.database = setupDatabase(resources.getConfig());

        // Stats
        this.statsTable = database.createTable("stats", new Record(
                new Field("uuid", DataType.FIXED_STRING, Infobase.UUID_MAX_CHARACTERS),
                new Field("username", DataType.STRING, Infobase.USERNAME_MAX_CHARACTERS),
                new Field("kills", DataType.INTEGER),
                new Field("deaths", DataType.INTEGER),
                new Field("experience", DataType.INTEGER),
                new Field("level", DataType.INTEGER),
                new Field("killstreak", DataType.INTEGER),
                new Field("assists", DataType.INTEGER)));

        // Kit Cooldowns
        for (String kitName : resources.getPluginDirectoryFiles("kits", false)) {
            if (!kitName.startsWith(".")) { // ignore hidden files
                addKitCooldownTable(kitName);
            }
        }
    }

    private Database setupDatabase(Resource config) {
        Toolkit.printToConsole("&7[&b&lKIT-PVP&7] Establishing database connection...");

        // Set database to MySQL, or in any other case, SQLite
        if (config.fetchString("Storage.Type").equalsIgnoreCase("mysql")) {
            String host = config.fetchString("Storage.MySQL.Host");
            int port = config.getInt("Storage.MySQL.Port");
            String databaseName = config.fetchString("Storage.MySQL.Database");
            String username = config.fetchString("Storage.MySQL.Username");
            String password = config.fetchString("Storage.MySQL.Password");

            return new Database(host, port, databaseName, username, password);
        } else {
            return new Database("plugins/KitPvP/storage.db");
        }
    }

    public boolean isPlayerRegistered(String username) {
        String uuid = usernameToUUID(username);
        if (uuid == null) return false;

        return tableContainsUUID("stats", uuid);
    }

    public void registerPlayerStats(Player p) {
        if (!verifyTableExists("stats")) {
            return;
        }

        Field uuidField = uuidField("uuid", p.getUniqueId().toString());
        Record playerRecord = statsTable.getRecord(uuidField);

        if (playerRecord == null) {
            Record statsRecord = new Record(
                    uuidField,
                    new Field("username", DataType.STRING, p.getName(), Infobase.USERNAME_MAX_CHARACTERS),
                    new Field("kills", DataType.INTEGER, 0),
                    new Field("deaths", DataType.INTEGER, 0),
                    new Field("killstreak", DataType.INTEGER, 0),
                    new Field("experience", DataType.INTEGER, 0),
                    new Field("assists", DataType.INTEGER, 0),
                    new Field("level", DataType.INTEGER,
                            resources.getLevels().getInt("Levels.Options.Minimum-Level")));
            statsTable.insertRecord(statsRecord);
        } else {
            // check if stored username needs changing if a player changed their username
            String storedUsername = (String) playerRecord.getFieldValue("username");
            String currentUsername = p.getName();

            if (!storedUsername.equals(currentUsername)) {
                Field updatedUsernameField = new Field("username", DataType.STRING, p.getName(),
                        Infobase.USERNAME_MAX_CHARACTERS);
                statsTable.updateRecord(uuidField, updatedUsernameField);
            }
        }

        // put stats into stats cache
        playerRecord = statsTable.getRecord(uuidField("uuid", p.getUniqueId().toString()));
        CacheManager.getStatsCache().put(p.getName(), recordToPlayerData(playerRecord));
    }

    public void exportStats() {
        Resource statsResource = new Resource(plugin, "stats.yml");
        statsResource.load();

        ConfigurationSection statsSection = statsResource.getConfigurationSection("Stats.Players");

        for (String uuid : statsSection.getKeys(false)) {
            ConfigurationSection playerSection = statsSection.getConfigurationSection(uuid);

            Field uuidField = uuidField("uuid", uuid);
            Field usernameField = new Field("username", DataType.STRING,
                    playerSection.getString("Username"), Infobase.USERNAME_MAX_CHARACTERS);
            Field killsField = new Field("kills", DataType.INTEGER, playerSection.getInt("Kills"));
            Field deathsField = new Field("deaths", DataType.INTEGER, playerSection.getInt("Deaths"));
            Field streakField = new Field("killstreak", DataType.INTEGER, playerSection.getInt("Killstreak"));
            Field experienceField =
                    new Field("experience", DataType.INTEGER, playerSection.getInt("Experience"));
            Field levelField = new Field("level", DataType.INTEGER, playerSection.getInt("Level"));
            Field assistsField = new Field("assists", DataType.INTEGER, playerSection.getInt("Assists"));

            Record playerStatsRecord = new Record(uuidField, usernameField, killsField, deathsField, experienceField,
                    levelField, assistsField, streakField);

            statsTable.updateOrInsertRecord(playerStatsRecord);
        }

        File renamedStatsFile = new File("old_stats.yml");
        if (!statsResource.getFile().renameTo(renamedStatsFile)) {
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cThere was a problem renaming stats.yml to old_stats.yml.");
        }
    }

    public String usernameToUUID(String username) {
        if (CacheManager.getUUIDCache().containsKey(username)) {
            return CacheManager.getUUIDCache().get(username);
        }

        if (verifyTableExists("stats")) {
            Table stats = database.getTable("stats");

            List<Record> matchingRecords = stats.searchRecords(usernameField(username));
            if (matchingRecords.size() == 1) {
                Record matchingRecord = matchingRecords.get(0);

                String uuid = (String) matchingRecord.getFieldValue("uuid");
                CacheManager.getUUIDCache().put(username, uuid);
                return uuid;
            }
        }

        return null;
    }

    private boolean tableContainsUUID(String tableName, String uuid) {
        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);
            return table.getRecord(uuidField("uuid", uuid)) != null;
        }
        return false;
    }

    public List<TopEntry> getTopNStats(String identifier, int n) {
        if (verifyTableExists("stats")) {
            Table table = database.getTable("stats");

            return table.getTopN(new Field(identifier, DataType.INTEGER),
                    new Field("username", DataType.FIXED_STRING, Infobase.USERNAME_MAX_CHARACTERS), n);
        }
        return new ArrayList<>();
    }

    public void addKitCooldownTable(String kitName) {
        database.createTable(kitName + "_cooldowns", new Record(
                        new Field("uuid", DataType.FIXED_STRING, Infobase.UUID_MAX_CHARACTERS),
                        new Field("last_used", DataType.INTEGER)));
    }

    public void deleteKitCooldownTable(String kitName) {
        String kitCooldownTableName = kitName + "_cooldowns";
        if (verifyTableExists(kitCooldownTableName)) {
            database.deleteTable(kitCooldownTableName);
        }
        cleanupUnusedKitCooldownTables();
    }

    public void cleanupUnusedKitCooldownTables() {
        for (String tableName : database.getTables().keySet()) {
            if (tableName.contains("_cooldowns")) {
                String kitName = tableName.split("_cooldowns")[0];
                if (!plugin.getArena().getKits().isKit(kitName)) {
                    database.deleteTable(tableName);
                }
            }
        }
    }

    public void setData(String tableName, String identifier, Object data, DataType type, String username) {
        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);

            Field uuidField = uuidField("username", username);
            Record record = table.getRecord(uuidField);
            Field fieldToUpdate = new Field(identifier, type, data);

            if (record != null) {
                table.updateRecord(uuidField, fieldToUpdate);
            } else {
                System.out.printf("[Database] Failed to set data; database does not contain player %s\n", username);
            }
        }
    }

    public void setStatsData(String username, PlayerData playerData) {
        if (verifyTableExists("stats")) {
            Table statsTable = database.getTable("stats");
            Field uuidField = uuidField("username", username);

            List<Field> fieldsToUpdate = new ArrayList<>();
            for (String statIdentifier : playerData.getData().keySet()) {
                Field statField = new Field(statIdentifier, DataType.INTEGER, playerData.getData(statIdentifier));
                fieldsToUpdate.add(statField);
            }

            // by doing this, it updates a whole stat row with one single SQL statement
            statsTable.updateRecord(uuidField, fieldsToUpdate.toArray(new Field[0]));
        }

        // Cooldowns
        if (playerData.getKitCooldowns().size() > 0) {
            for (Map.Entry<String, Long> entry : playerData.getKitCooldowns().entrySet()) {
                String kitNameWithCooldown = entry.getKey();
                long timeKitLastUsed = entry.getValue();

                String kitCooldownTableName = "{kit_name}_cooldowns"
                                            .replace("{kit_name}", kitNameWithCooldown);
                if (verifyTableExists(kitCooldownTableName)) {
                    Table kitCooldownTable = database.getTable(kitCooldownTableName);
                    Record cooldownRecord = new Record(uuidField("username", username),
                            new Field("last_used", DataType.INTEGER, timeKitLastUsed));

                    kitCooldownTable.updateOrInsertRecord(cooldownRecord);
                }
            }
        }
    }

    public Object getData(String tableName, String identifier, String username) {
        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);
            Record record = table.getRecord(uuidField("username", username));

            if (record != null) {
                return record.getFieldValue(identifier);
            } /*else { // currently commented out because not being in a kit cooldown table prints this
                System.out.printf("[Database] Failed to get data; database does not contain player %s\n", username);
            } */
        }
        return null;
    }

    public PlayerData recordToPlayerData(Record playerRecord) {
        PlayerData playerData = new PlayerData(-1, -1, -1, -1, -1, -1);
        for (String statIdentifier : playerData.getData().keySet()) {
            Object o = playerRecord.getFieldValue(statIdentifier);
            if (o == null) continue;
            playerData.setData(statIdentifier, (int) o);
        }
        return playerData;
    }

    public PlayerData getStatsData(String username) {
        PlayerData playerData = new PlayerData(-1, -1, -1, -1, -1, -1);
        if (verifyTableExists("stats")) {
            Table statsTable = database.getTable("stats");

            Record playerRecord = statsTable.getRecord(uuidField("username", username));
            return recordToPlayerData(playerRecord);
        }
        return playerData;
    }

    private boolean verifyTableExists(String tableName) {
        if (database.getTable(tableName) != null) {
            return true;
        }
        System.out.printf("[Database] Failed to perform action; table %s does not exist\n", tableName);
        return false;
    }

    private Field uuidField(String flag, String value) {
        if (flag.equalsIgnoreCase("username")) {
            return new Field("uuid", DataType.FIXED_STRING, usernameToUUID(value),
                    Infobase.UUID_MAX_CHARACTERS);
        } else if (flag.equalsIgnoreCase("uuid")) {
            return new Field("uuid", DataType.FIXED_STRING, value,
                    Infobase.UUID_MAX_CHARACTERS);
        }
        return null;
    }

    private Field usernameField(String value) {
        return new Field("username", DataType.STRING, value, Infobase.USERNAME_MAX_CHARACTERS);
    }

}
