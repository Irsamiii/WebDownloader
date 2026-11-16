package org.example;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseManager {
    // Update DB_URL, DB_USER, and DB_PASSWORD to match your local database settings
    private static final String DB_URL = "jdbc:mysql://localhost:3306/web_downloader_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void initializeDatabase() {
        String createWebsiteTable = "CREATE TABLE IF NOT EXISTS website (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "website_name VARCHAR(255) NOT NULL, " +
                "download_start_date_time DATETIME NOT NULL, " +
                "download_end_date_time DATETIME NOT NULL, " +
                "total_elapsed_time BIGINT NOT NULL, " +
                "total_downloaded_kilobytes DOUBLE NOT NULL);";

        String createLinkTable = "CREATE TABLE IF NOT EXISTS `link` (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "link_name TEXT NOT NULL, " +
                "website_id INT NOT NULL, " +
                "total_elapsed_time BIGINT NOT NULL, " +
                "total_downloaded_kilobytes DOUBLE NOT NULL, " +
                "FOREIGN KEY (website_id) REFERENCES website(id) ON DELETE CASCADE);";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createWebsiteTable);
            stmt.execute(createLinkTable);
            System.out.println("[Database] Tables verified and ready.");
        } catch (SQLException e) {
            System.err.println("[Database Error] Failed to initialize tables: " + e.getMessage());
        }
    }

    public static long saveWebsiteReport(String websiteName, LocalDateTime start, LocalDateTime end, long elapsedTime, double totalKb) {
        String query = "INSERT INTO website (website_name, download_start_date_time, download_end_date_time, total_elapsed_time, total_downloaded_kilobytes) VALUES (?, ?, ?, ?, ?)";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, websiteName);
            pstmt.setString(2, start.format(formatter));
            pstmt.setString(3, end.format(formatter));
            pstmt.setLong(4, elapsedTime);
            pstmt.setDouble(5, totalKb);

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[Database Error] Failed to save website report: " + e.getMessage());
        }
        return -1;
    }

    public static void saveLinkReport(long websiteId, String linkName, long elapsedTime, double downloadedKb) {
        String query = "INSERT INTO `link` (link_name, website_id, total_elapsed_time, total_downloaded_kilobytes) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, linkName);
            pstmt.setLong(2, websiteId);
            pstmt.setLong(3, elapsedTime);
            pstmt.setDouble(4, downloadedKb);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[Database Error] Failed to save link report: " + e.getMessage());
        }
    }

    public static void queryWebsiteByName(String websiteName) {
        String query = "SELECT w.*, l.link_name, l.total_elapsed_time AS link_time, l.total_downloaded_kilobytes AS link_kb " +
                "FROM website w LEFT JOIN `link` l ON w.id = l.website_id WHERE w.website_name LIKE ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, "%" + websiteName + "%");
            ResultSet rs = pstmt.executeQuery();

            boolean headerPrinted = false;
            while (rs.next()) {
                if (!headerPrinted) {
                    System.out.println("\n--- DATABASE RECORD FOR: " + rs.getString("website_name") + " ---");
                    System.out.println("Start Time: " + rs.getString("download_start_date_time"));
                    System.out.println("End Time:   " + rs.getString("download_end_date_time"));
                    System.out.println("Total Time: " + rs.getLong("total_elapsed_time") + " ms");
                    System.out.println("Total Size: " + String.format("%.2f", rs.getDouble("total_downloaded_kilobytes")) + " KB");
                    System.out.println("\nExtracted Links Records:");
                    headerPrinted = true;
                }
                String link = rs.getString("link_name");
                if (link != null) {
                    System.out.println("  -> Link: " + link + " | Time: " + rs.getLong("link_time") + " ms | Size: " + String.format("%.2f", rs.getDouble("link_kb")) + " KB");
                }
            }
            if (!headerPrinted) {
                System.out.println("No records found in database for website: " + websiteName);
            }
        } catch (SQLException e) {
            System.err.println("[Database Error] Error querying database: " + e.getMessage());
        }
    }
}