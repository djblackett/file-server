package server;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Main implements Serializable {
    public static volatile int newFileCount = 0;
    public static List<Path> files;
    public static String mapFile = "F:\\Downloads\\Login\\File Server\\File Server\\task\\src\\server\\data\\map.txt";
    public static Path mapPath = Paths.get(mapFile);
    public static final String DATA = "F:\\Downloads\\Login\\File Server\\File Server\\task\\src\\server\\data\\";
    public static Path serverStorage = Paths.get(DATA);
    public static volatile ConcurrentHashMap<Integer, String> map = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final int PORT = 3000;
    private static final String address = "127.0.0.1";

    public static void main(String[] args) throws IOException {


        ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getByName(address));
        System.out.println("Server started!");

        loadMap();

        while (true) {

            Socket socket = server.accept();
            files = Files.list(serverStorage).collect(Collectors.toList());
            newFileCount = untitledFileCount(files);
            System.out.println(newFileCount);
            executor.execute(new ServerThread(server, socket));

        }
    }

    private static void serve(Socket socket, ServerSocket server) throws IOException {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        // Read http request from client - parse which method from incoming string
        output.writeInt(newFileCount); // tells client how to number unnamed files when PUT request is made
        String inputString = input.readUTF();
        System.out.println("Received: " + inputString);
        String[] request = inputString.split(" ");
        String method = request[0];

        //exit method for testing purposes - remove for production
        //saves map to file
        if (method.equals("exit")) {
            shutDown(socket, server, input, output);
        }

        // Determine filename and its Path for file on server
        String filename = determineFilename(method, request, output);


        Path filePath = serverStorage.resolve(Paths.get(filename));

        // send correct response to client and do corresponding actions
        switch (method) {
            case "GET":
                getResponse(files, output, filePath);
                break;
            case "PUT":
                putResponse(files, output, input, filePath, filename);
                files = updateFiles();
                break;
            case "DELETE":
                deleteResponse(files, output, filePath, filename);
                files = updateFiles();
        }

        // close streams
        closeAllStreams(socket, input, output);
    }


    static class ServerThread implements Runnable {
        ServerSocket server;
        Socket socket;

        public ServerThread(ServerSocket server, Socket socket) {
            this.server = server;
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                serve(socket, server);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static int getNextId() {
        if (map.isEmpty()) {
            return 1;
        }
        int max = Collections.max(map.keySet());
        return max + 1;
    }

    static void putResponse(List<Path> files, DataOutputStream output, DataInputStream input, Path filePath, String filename) throws IOException {
        if (files.contains(filePath)) {
            output.writeUTF("403");
        } else {
            //todo create file and write data
            int newId = getNextId();
            map.put(newId, filename);
            output.writeUTF("200 " + newId);

            int byteArrayLength = input.readInt();
            byte[] content = new byte[byteArrayLength];
            input.readFully(content);
            Files.createFile(filePath);
            Files.write(filePath, content);
        }
    }

    static void deleteResponse(List<Path> files, DataOutputStream output, Path filePath, String filename) throws IOException {
        if (files.contains(filePath)) {
            Files.delete(filePath);
            for (Map.Entry<Integer, String> entry : map.entrySet()) {
                if (entry.getValue().equals(filename)) {
                    map.remove(entry.getKey());
                    break;
                }
            }
            output.writeUTF("200");
        } else {
            output.writeUTF("404");
        }
    }

    static void getResponse(List<Path> files, DataOutputStream output, Path filePath) throws IOException {
        if (files.contains(filePath)) {
            output.writeUTF("200");
            byte[] fileData = Files.readAllBytes(filePath);
            int length = fileData.length;
            output.writeInt(length);
            output.write(fileData);

        } else {
            output.writeUTF("404");
        }
    }

    static void shutDown(Socket socket, ServerSocket server, DataInputStream input, DataOutputStream output) throws IOException {
        executor.shutdown();
        if (!Files.exists(mapPath)) {
            Files.createFile(mapPath);
        }

        BufferedWriter buff = Files.newBufferedWriter(mapPath);
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            buff.write(entry.getKey() + " " + entry.getValue() + "\n");
        }
        buff.close();
        socket.close();
        input.close();
        output.close();
        server.close();
        System.exit(0);
    }

    static void loadMap() throws IOException {
        if (Files.exists(mapPath)) {
            List<String> dataList = Files.readAllLines(mapPath);
            for (String s : dataList) {
                String[] split = s.split(" ");
                map.put(Integer.parseInt(split[0]), split[1]);
            }
        }
    }

    static String determineFilename(String method, String[] request, DataOutputStream output) throws IOException {
        String filename = "";
        if (method.equals("PUT")) {
            filename = request[1];
        } else {

            String mode = request[1];

            if (request[2].replaceAll("\\d", "").length() == 0) {
                mode = "BY_ID";
            }

            if (mode.equals("BY_ID")) {
                int id = Integer.parseInt(request[2]);
                if (!map.containsKey(id)) {
                    output.writeUTF("404");
                    return null;
                }
                filename = map.get(id);
            } else if (mode.equals("BY_NAME")) {

                filename = request[2];
            }
        }

        return filename;
    }


    static void closeAllStreams(Socket socket, DataInputStream input, DataOutputStream output) throws IOException {
        socket.close();
        input.close();
        output.close();
    }

    static int untitledFileCount(List<Path> files) {
        Optional<String> highestUntitledString = files
                .parallelStream()
                .filter((x) -> x.toString().contains("new-file"))
                .map((x) -> x.toString().substring(0, x.toString().length() - 4))
                .collect(Collectors.toList())
                .stream()
                .max(Comparator.comparingInt(Main::extractInt));
        return highestUntitledString.map(s -> Integer.parseInt(s.replaceAll("\\D", ""))).orElse(0);
    }

    static int extractInt(String s) {
        String num = s.replaceAll("\\D", "");
        // return 0 if no digits found
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }

    static List<Path> updateFiles() throws IOException {
        return Files.list(serverStorage).collect(Collectors.toList());
    }



}