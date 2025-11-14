package uj.wmii.pwj.gvt;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Gvt {

    private final ExitHandler exitHandler;

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
    }

    private static String gvt_dir = ".gvt";
    private static String VersionDir = "versions";
    private static String LatestFile = "latest";
    private static String ActiveFile = "active";
    private static String messageFile = ".message";

    private Path gvtPath = Paths.get(gvt_dir);
    private Path versionsPath = gvtPath.resolve(VersionDir);
    private Path latestPath = gvtPath.resolve(LatestFile);
    private Path activePath = gvtPath.resolve(ActiveFile);

    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }

    public void mainInternal(String... args) {
        if (args.length == 0) {
            exitHandler.exit(1, "Please specify command.");
            return;
        }
        if (args[0].equals("init")) {
            try {
                executeInit(args);
            } catch (IOException e) {
                systemError(e);
            }
            return;
        }
        if (!Files.isDirectory(gvtPath)) {
            exitHandler.exit(-2, "Current directory is not initialized. Please use init command to initialize.");
            return;
        }
        try {
            switch (args[0]) {
                case "add":
                    executeAdd(args);
                    break;
                case "detach":
                    executeDetach(args);
                    break;
                case "commit":
                    executeCommit(args);
                    break;
                case "checkout":
                    executeCheckout(args);
                    break;
                case "history":
                    executeHistory(args);
                    break;
                case "version":
                    executeVersion(args);
                    break;
                default:
                    exitHandler.exit(1, "Unknown command " + args[0] + ".");
            }
        } catch (IOException e) {
            systemError(e);
        }
    }

    private void executeAdd(String[] args) throws IOException {
        if (args.length < 2) {
            exitHandler.exit(20, "Please specify file to add.");
            return;
        }
        Path fileToAdd = Paths.get(args[1]);
        Path lastVersionPath = versionsPath.resolve(String.valueOf(getLatestVersionNum()));
        Path targetGvt = lastVersionPath.resolve(args[1]);
        if (Files.exists(targetGvt)) {
            exitHandler.exit(0, "File already added. File: " + args[1]);
            return;
        }
        if (!Files.isRegularFile(fileToAdd)) {
            exitHandler.exit(21, "File not found. File: " + args[1]);
            return;
        }

        String defaultMessage = "File added successfully. File: " + args[1];
        String message;
        if (args.length == 4 && args[2].equals("-m")) {
            message = args[3];
        } else {
            message = defaultMessage;
        }

        try {
            int prevLatest = getLatestVersionNum();
            int newVersionNumber = createNewVersion(prevLatest, message);
            Path newVersionPath = versionsPath.resolve(String.valueOf(newVersionNumber));
            Path newFileDest = newVersionPath.resolve(args[1]);
            copyFile(fileToAdd, newFileDest);
            exitHandler.exit(0, "File added successfully. File: " + args[1]);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            exitHandler.exit(22, "File cannot be added. See ERR for details. File: " + args[1]);
        }
    }

    private void executeCommit(String[] args) throws IOException {
        if (args.length < 2) {
            exitHandler.exit(50, "Please specify file to commit.");
            return;
        }
        if (!Files.isRegularFile(Paths.get(args[1]))) {
            exitHandler.exit(51, "File not found. File: " + args[1]);
            return;
        }
        int latestVersionNum = getLatestVersionNum();
        Path latestVersionPath = versionsPath.resolve(String.valueOf(latestVersionNum));
        Path fileInGvt = latestVersionPath.resolve(args[1]);

        if (!Files.exists(fileInGvt)) {
            exitHandler.exit(0, "File is not added to gvt. File: " + args[1]);
            return;
        }
        String defaultMessage = "File committed successfully. File: " + args[1];
        String message;
        if (args.length == 4 && args[2].equals("-m")) {
            message = args[3];
        } else {
            message = defaultMessage;
        }

        try {
            int newVersionNum = createNewVersion(latestVersionNum, message);
            Path newVersionPath = versionsPath.resolve(String.valueOf(newVersionNum));
            Path newFileDest = newVersionPath.resolve(args[1]);
            copyFile(Paths.get(args[1]), newFileDest);
            exitHandler.exit(0, "File committed successfully. File: " + args[1]);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            exitHandler.exit(52, "File cannot be committed, see ERR for details. File: " + args[1]);
        }
    }

    private void executeDetach(String[] args) throws IOException {
        if (args.length < 2) {
            exitHandler.exit(30, "Please specify file to detach.");
            return;
        }
        int latestVersionNum = getLatestVersionNum();
        Path latestVersionPath = versionsPath.resolve(String.valueOf(latestVersionNum));
        Path fileToDetach = latestVersionPath.resolve(args[1]);
        if (!Files.exists(fileToDetach)) {
            exitHandler.exit(0, "File is not added to gvt. File: " + args[1]);
            return;
        }

        String defaultMessage = "File detached successfully. File: " + args[1];
        String message;
        if (args.length == 4 && args[2].equals("-m")) {
            message = args[3];
        } else {
            message = defaultMessage;
        }

        try {
            int newVersionNum = createNewVersion(latestVersionNum, message);
            Path newVersionPath = versionsPath.resolve(String.valueOf(newVersionNum));
            Path fileInNewVersion = newVersionPath.resolve(args[1]);
            Files.deleteIfExists(fileInNewVersion);
            exitHandler.exit(0, "File detached successfully. File: " + args[1]);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            exitHandler.exit(31, "File cannot be detached, see ERR for details. File: " + args[1]);
        }
    }

    private void executeCheckout(String[] args) throws IOException {
        if (args.length < 2) {
            exitHandler.exit(60, "Invalid version number: ");
            return;
        }
        int versionNum;
        try {
            versionNum = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            exitHandler.exit(60, "Invalid version number: " + args[1]);
            return;
        }

        Path versionPath = versionsPath.resolve(String.valueOf(versionNum));
        if (!Files.isDirectory(versionPath)) {
            exitHandler.exit(60, "Invalid version number: " + args[1]);
            return;
        }

        Set<String> targetFiles = getFiles(versionNum);
        Set<String> currentFiles = getFiles(Integer.parseInt(Files.readString(activePath).trim()));
        for (String fileName : targetFiles) {
            Path source = versionPath.resolve(fileName);
            Path dest = Paths.get(fileName);
            copyFile(source, dest);
        }
        for (String fileName : currentFiles) {
            if (!targetFiles.contains(fileName)) {
                Files.deleteIfExists(Paths.get(fileName));
            }
        }
        Files.writeString(activePath, String.valueOf(versionNum));

        exitHandler.exit(0, "Checkout successful for version: " + versionNum);
    }

    private void executeHistory(String[] args) throws IOException {
        try {
            int latestVersionNum = getLatestVersionNum();
            int first = 0;
            if (args.length == 3 && args[1].equals("-last")) {
                int n;
                try {
                    n = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    n = latestVersionNum + 1;
                }
                first = Math.max(0, latestVersionNum - n + 1);
            }
            StringBuilder output = new StringBuilder();
            boolean firstLineAppended = false;
            for (int i = latestVersionNum; i >= first; i--) {
                Path msgPath = versionsPath.resolve(String.valueOf(i)).resolve(messageFile);
                String message = Files.readString(msgPath);
                String firstLine = message.split("\\R", 2)[0];

                if (firstLineAppended) {
                    output.append("\n");
                }

                output.append(i).append(": ").append(firstLine);
                firstLineAppended = true;
            }
            if (firstLineAppended) {
                output.append("\n");
            }
            exitHandler.exit(0, output.toString());
        } catch (IOException e) {
            systemError(e);
        }
    }

    private void executeVersion(String[] args) throws IOException {
        try {
            int versionNum;

            if (args.length == 1) {
                versionNum = Integer.parseInt(Files.readString(activePath).trim());
            } else {
                String versionStr = args[1];
                try {
                    versionNum = Integer.parseInt(versionStr);
                } catch (NumberFormatException e) {
                    exitHandler.exit(60, "Invalid version number: " + versionStr);
                    return;
                }
                Path versionPathToCheck = versionsPath.resolve(String.valueOf(versionNum));
                if (!Files.isDirectory(versionPathToCheck)) {
                    exitHandler.exit(60, "Invalid version number: " + versionStr);
                    return;
                }
            }
            Path msgPath = versionsPath.resolve(String.valueOf(versionNum)).resolve(messageFile);
            String message = Files.readString(msgPath);

            exitHandler.exit(0, "Version: " + versionNum + "\n" + message);
        } catch (IOException e) {
            systemError(e);
        }
    }

    private void executeInit(String[] args) throws IOException {
        if (Files.isDirectory(gvtPath)) {
            exitHandler.exit(10, "Current directory is already initialized.");
            return;
        }
        Files.createDirectory(gvtPath);
        Files.createDirectory(versionsPath);
        Path vzeroPath = versionsPath.resolve("0");
        Files.createDirectory(vzeroPath);

        Files.writeString(vzeroPath.resolve(messageFile), "GVT initialized.");
        Files.writeString(latestPath, "0");
        Files.writeString(activePath, "0");
        exitHandler.exit(0, "Current directory initialized successfully.");
    }

    private int getLatestVersionNum() throws IOException {
        String content = Files.readString(latestPath).trim();
        return Integer.parseInt(content);
    }

    private int createNewVersion(int prevVersion, String message) throws IOException {

        int newVersionNum = prevVersion + 1;
        Path baseVersionPath = versionsPath.resolve(String.valueOf(prevVersion));
        Path newVersionPath = versionsPath.resolve(String.valueOf(newVersionNum));

        Files.createDirectory(newVersionPath);
        Files.writeString(newVersionPath.resolve(messageFile), message);

        File oldFolder = baseVersionPath.toFile();
        File[] listaPlikow = oldFolder.listFiles();

        if (listaPlikow != null) {
            for (File plik : listaPlikow) {
                String fileName = plik.getName();
                if (!fileName.equals(messageFile)) {
                    Path destinationFile = newVersionPath.resolve(fileName);
                    Files.copy(plik.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        Files.writeString(latestPath, String.valueOf(newVersionNum));
        Files.writeString(activePath, String.valueOf(newVersionNum));

        return newVersionNum;
    }

    private Set<String> getFiles(int versionNum) throws IOException {

        Set<String> trackedFiles = new HashSet<>();
        Path versionPath = versionsPath.resolve(String.valueOf(versionNum));
        File katalogWersji = versionPath.toFile();
        File[] listaPlikow = katalogWersji.listFiles();
        if (listaPlikow != null) {
            for (File plik : listaPlikow) {
                String fileName = plik.getName();
                if (!fileName.equals(messageFile)) {
                    trackedFiles.add(fileName);
                }
            }
        }
        return trackedFiles;
    }

    private void systemError(IOException e) {
        e.printStackTrace(System.err);
        exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
    }

    private void copyFile(Path source, Path dest) throws IOException {
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
