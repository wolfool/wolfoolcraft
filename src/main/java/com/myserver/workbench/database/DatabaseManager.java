package com.myserver.workbench.database;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final Plugin plugin;
    private Connection connection;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connected to SQLite database.");
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to SQLite database: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Player data table
            statement.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid TEXT PRIMARY KEY," +
                    "proficiency INTEGER DEFAULT 0," +
                    "unlocked_slots INTEGER DEFAULT 3," +
                    "installed_workbenches INTEGER DEFAULT 0)");

            // Unlocked recipes table
            statement.execute("CREATE TABLE IF NOT EXISTS player_recipes (" +
                    "uuid TEXT," +
                    "recipe_id TEXT," +
                    "PRIMARY KEY(uuid, recipe_id))");

            // Active crafting queues
            statement.execute("CREATE TABLE IF NOT EXISTS player_queues (" +
                    "session_id TEXT PRIMARY KEY," +
                    "uuid TEXT," +
                    "result_item_base64 TEXT," +
                    "end_time_ms INTEGER," +
                    "is_collected INTEGER DEFAULT 0)");

            // Encyclopedia: discovered recipes per player
            statement.execute("CREATE TABLE IF NOT EXISTS encyclopedia (" +
                    "uuid TEXT," +
                    "recipe_id TEXT," +
                    "discovered_at INTEGER DEFAULT 0," +
                    "craft_count INTEGER DEFAULT 0," +
                    "PRIMARY KEY(uuid, recipe_id))");
        }
    }
    
    public Connection getConnection() {
        return connection;
    }

    // --- Helper Methods ---

    public int getUnlockedSlots(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT unlocked_slots FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("unlocked_slots");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
        return 3;
    }

    public void updateUnlockedSlots(UUID uuid, int slots) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_data (uuid, unlocked_slots) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET unlocked_slots = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slots);
            ps.setInt(3, slots);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
    }

    public int getInstalledCount(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT installed_workbenches FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("installed_workbenches");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
        return 0;
    }

    public void incrementInstalledCount(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_data (uuid, installed_workbenches) VALUES (?, 1) " +
                "ON CONFLICT(uuid) DO UPDATE SET installed_workbenches = installed_workbenches + 1")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
    }

    /** 제작대를 회수했을 때. 0 밑으로는 내려가지 않게 막는다. */
    public void decrementInstalledCount(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_data SET installed_workbenches = MAX(installed_workbenches - 1, 0) WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
    }

    public void saveSession(UUID playerUuid, com.myserver.workbench.crafting.CraftingSession session) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_queues (session_id, uuid, result_item_base64, end_time_ms, is_collected) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(session_id) DO UPDATE SET is_collected = ?")) {
            ps.setString(1, session.getId().toString());
            ps.setString(2, playerUuid.toString());
            ps.setString(3, com.myserver.workbench.utils.ItemSerializer.toBase64(session.getResultItem()));
            ps.setLong(4, session.getEndTime());
            ps.setInt(5, session.isCollected() ? 1 : 0);
            ps.setInt(6, session.isCollected() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
    }

    public java.util.List<com.myserver.workbench.crafting.CraftingSession> loadSessions(UUID playerUuid) {
        java.util.List<com.myserver.workbench.crafting.CraftingSession> sessions = new java.util.ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT session_id, result_item_base64, end_time_ms, is_collected FROM player_queues WHERE uuid = ? AND is_collected = 0")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID sessionId = UUID.fromString(rs.getString("session_id"));
                org.bukkit.inventory.ItemStack item = com.myserver.workbench.utils.ItemSerializer.fromBase64(rs.getString("result_item_base64"));
                long endTime = rs.getLong("end_time_ms");
                // Construct session and bypass standard start time
                com.myserver.workbench.crafting.CraftingSession session = new com.myserver.workbench.crafting.CraftingSession(sessionId, item, endTime);
                sessions.add(session);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
        return sessions;
    }

    // ===== Encyclopedia Methods =====

    public void discoverRecipe(UUID uuid, String recipeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO encyclopedia (uuid, recipe_id, discovered_at, craft_count) VALUES (?, ?, ?, 0)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, recipeId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
    }

    public boolean isDiscovered(UUID uuid, String recipeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM encyclopedia WHERE uuid = ? AND recipe_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, recipeId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
        return false;
    }

    public java.util.Set<String> getDiscoveredRecipes(UUID uuid) {
        java.util.Set<String> discovered = new java.util.HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT recipe_id FROM encyclopedia WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                discovered.add(rs.getString("recipe_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
        return discovered;
    }

    public void incrementCraftCount(UUID uuid, String recipeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE encyclopedia SET craft_count = craft_count + 1 WHERE uuid = ? AND recipe_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, recipeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
    }

    public int getCraftCount(UUID uuid, String recipeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT craft_count FROM encyclopedia WHERE uuid = ? AND recipe_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, recipeId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("craft_count");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 작업에 실패했습니다.", e);
        }
        return 0;
    }
}
