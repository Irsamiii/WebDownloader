package org.example;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        WebsiteDownloader downloader = new WebsiteDownloader();

        DatabaseManager.initializeDatabase();

        System.out.println("\n=== ALGORITHM INC. - CONSOLE WEB DOWNLOADER ===");
        while (true) {
            System.out.println("\nSelect an option:");
            System.out.println("1. Download a Website");
            System.out.println("2. Query Website Report from Database");
            System.out.println("3. Exit");
            System.out.print("Enter choice (1-3): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    System.out.print("Enter website URL (e.g., https://example.com): ");
                    String url = scanner.nextLine().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        System.out.println("Invalid URL format. Must start with http:// or https://");
                        break;
                    }
                    downloader.startDownloadProcess(url);
                    break;
                case "2":
                    System.out.print("Enter website name or domain to search: ");
                    String queryName = scanner.nextLine().trim();
                    DatabaseManager.queryWebsiteByName(queryName);
                    break;
                case "3":
                    System.out.println("Exiting app1lication. Goodbye!");
                    scanner.close();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid choice. Please enter 1, 2, or 3.");
            }
        }
    }
}