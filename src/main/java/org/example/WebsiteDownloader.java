package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WebsiteDownloader {

    public static class DownloadResult {
        public long elapsedTime;
        public double downloadedKb;

        public DownloadResult(long elapsedTime, double downloadedKb) {
            this.elapsedTime = elapsedTime;
            this.downloadedKb = downloadedKb;
        }
    }

    public static class LinkReportData {
        public String linkName;
        public long elapsedTime;
        public double downloadedKb;

        public LinkReportData(String linkName, long elapsedTime, double downloadedKb) {
            this.linkName = linkName;
            this.elapsedTime = elapsedTime;
            this.downloadedKb = downloadedKb;
        }
    }

    public void startDownloadProcess(String urlString) {
        LocalDateTime startTime = LocalDateTime.now();
        long overallStartTime = System.currentTimeMillis();

        try {
            URI uri = new URI(urlString);
            URL url = uri.toURL();
            String host = url.getHost();
            if (host == null || host.isEmpty()) {
                System.out.println("Invalid URL: Could not extract domain name.");
                return;
            }

            // Create local directory named after domain
            Path downloadDir = Paths.get(host);
            if (!Files.exists(downloadDir)) {
                Files.createDirectories(downloadDir);
            }
            System.out.println("[INFO] Created directory: " + downloadDir.toAbsolutePath());

            // Download Homepage
            System.out.println("[INFO] Downloading homepage from: " + urlString);
            long homeStart = System.currentTimeMillis();
            File homeFile = new File(downloadDir.toFile(), "index.html");
            DownloadResult homeResult = downloadFileAndMeasure(url, homeFile);
            long homeElapsed = System.currentTimeMillis() - homeStart;
            System.out.println("[PROGRESS] Homepage downloaded. Size: " + String.format("%.2f", homeResult.downloadedKb) + " KB in " + homeElapsed + " ms.");

            // Parse homepage and extract links
            Document doc = Jsoup.parse(homeFile, "UTF-8", urlString);
            Elements links = doc.select("a[href], link[href], script[src], img[src]");

            List<LinkReportData> linkReports = new ArrayList<>();
            double totalWebsiteKb = homeResult.downloadedKb;
            int linkCount = 0;

            System.out.println("\n[INFO] Extracted External & Resource Links:");
            for (Element element : links) {
                String resourceUrlStr = element.hasAttr("href") ? element.absUrl("href") : element.absUrl("src");
                if (resourceUrlStr.isEmpty() || resourceUrlStr.startsWith("javascript") || resourceUrlStr.startsWith("#")) {
                    continue;
                }

                linkCount++;
                System.out.println("  [" + linkCount + "] " + resourceUrlStr);

                try {
                    URI resUri = new URI(resourceUrlStr);
                    URL resUrl = resUri.toURL();

                    // Maintain remote server directory structure locally
                    String pathPart = resUri.getPath();
                    if (pathPart == null || pathPart.equals("/") || pathPart.isEmpty()) {
                        pathPart = "/index.html";
                    }

                    File targetFile = new File(downloadDir.toFile(), pathPart);
                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    long resStart = System.currentTimeMillis();
                    DownloadResult resResult = downloadFileAndMeasure(resUrl, targetFile);
                    long resElapsed = System.currentTimeMillis() - resStart;
                    totalWebsiteKb += resResult.downloadedKb;

                    System.out.println("    -> Progress: Downloaded " + String.format("%.2f", resResult.downloadedKb) + " KB in " + resElapsed + " ms");

                    linkReports.add(new LinkReportData(resourceUrlStr, resElapsed, resResult.downloadedKb));

                } catch (Exception e) {
                    System.out.println("    -> Failed to download resource: " + resourceUrlStr + " (" + e.getMessage() + ")");
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            long overallElapsed = System.currentTimeMillis() - overallStartTime;

            // Persist report to database
            long websiteId = DatabaseManager.saveWebsiteReport(host, startTime, endTime, overallElapsed, totalWebsiteKb);
            if (websiteId != -1) {
                for (LinkReportData lr : linkReports) {
                    DatabaseManager.saveLinkReport(websiteId, lr.linkName, lr.elapsedTime, lr.downloadedKb);
                }
            }

            // Print Final Completion Report
            printCompletionReport(host, startTime, endTime, overallElapsed, totalWebsiteKb, linkReports);

        } catch (Exception e) {
            System.err.println("Error during download process: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private DownloadResult downloadFileAndMeasure(URL url, File outputFile) throws Exception {
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = 0;
            long start = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            long elapsed = System.currentTimeMillis() - start;
            double kb = totalBytes / 1024.0;
            return new DownloadResult(elapsed, kb);
        }
    }

    private void printCompletionReport(String websiteName, LocalDateTime start, LocalDateTime end, long totalTime, double totalKb, List<LinkReportData> links) {
        System.out.println("\n==================================================");
        System.out.println("           WEBSITE DOWNLOAD COMPLETION REPORT     ");
        System.out.println("==================================================");
        System.out.println("Website Name   : " + websiteName);
        System.out.println("Start Date/Time: " + start);
        System.out.println("End Date/Time  : " + end);
        System.out.println("Total Elapsed  : " + totalTime + " ms");
        System.out.println("Total Download : " + String.format("%.2f", totalKb) + " KB");
        System.out.println("--------------------------------------------------");
        System.out.println("Individual Link Breakdown:");
        for (int i = 0; i < links.size(); i++) {
            LinkReportData l = links.get(i);
            System.out.println(String.format(" %d. URL: %s", (i + 1), l.linkName));
            System.out.println(String.format("    Time Taken: %d ms | Size: %.2f KB", l.elapsedTime, l.downloadedKb));
        }
        System.out.println("==================================================");
        System.out.println("[SUCCESS] Report successfully persisted to Relational DBMS.");
    }
}