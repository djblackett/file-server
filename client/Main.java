package client;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String saveLocation = "F:\\Downloads\\Login\\File Server\\File Server\\task\\src\\client\\data\\";
    public static final int GET = 1;
    public static final int PUT = 2;
    public static final int DELETE = 3;
    private static final int PORT = 3000;
    private static final String address = "127.0.0.1";
    private byte[] fileContent;

    public static void main(String[] args) throws IOException {
        //todo add exception handling
        Main client = new Main();
        Socket socket = new Socket(InetAddress.getByName(address), PORT);

        // setup input and output streams for client-server communication
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Client started!");

            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            int untitledFileIncrement = input.readInt();

            // take user input to determine which kind of http request to execute
            String request = createRequest(reader, client, untitledFileIncrement);

            if (request != null) {
                // Send request
                output.writeUTF(request);
                System.out.println("The request was sent.");


                if (request.equals("exit")) {
                    System.exit(0);
                }
                // Server response:
                String received = input.readUTF();

                // Process server response
                if (request.startsWith("PUT")) {
                    sendPutContent(received, output, client);
                } else if (request.startsWith("GET")) {
                    receiveGetContents(received, input, reader);
                } else if (request.startsWith("DELETE")) {
                    printDeleteResults(received);
                }

                // Close streams
                closeAllStreams(socket, input, output);
            }
        }
    }

    private static String createRequest(BufferedReader reader, Main client, int untitledFileCount) throws IOException {

        System.out.println("Enter action (1 - get the file, 2 - create a file, 3 - delete the file): ");

        // User chooses which action to take and provides filename
        String methodString = reader.readLine();
        if (methodString.equals("exit")) {
            return methodString;
        }

        int method = Integer.parseInt(methodString);
        if (method == GET) {
            return createGetRequest(reader);
        }
        System.out.println("Enter name of the file: ");
        String fileName = reader.readLine();

        // Generate request according to user choice
        switch (method) {
            case GET:

            case PUT:
                return createPutRequest(reader, fileName, client, untitledFileCount);
            case DELETE:
                return createDeleteRequest(reader, fileName);
        }
        return null;
    }

    static String createGetRequest(BufferedReader reader) throws IOException {
        System.out.println("Do you want to get the file by name or by id (1 - name, 2 - id): ");
        String replyString = reader.readLine();
//        if (replyString.replaceAll("\\d", "").length() > 0) {
//            System.out.println("The response says that this file is not found!");
//            System.exit(1);
//        }
        int reply = 0;
        if (replyString.replaceAll("\\d", "").length() == 0) {
            reply = Integer.parseInt(replyString);
        }
        if (reply == 1) {
            System.out.println("Enter filename");
            String fileName = reader.readLine();
            return "GET BY_NAME " + fileName;
        } else if (reply == 2) {
            System.out.println("Enter id: ");
            int id = Integer.parseInt(reader.readLine());

            //todo check map key for id# - make getter in server class
            return "GET BY_ID " + id;
        } else {
            if (replyString.replaceAll("\\d", "").length() > 0) {
                return "GET BY_NAME " + replyString;
            } else {
                return "GET BY_ID " + replyString;
            }
        }
    }

    static String createPutRequest(BufferedReader reader, String fileName, Main client, int untitledFileIncrement) throws IOException {
        //todo
                    /*
                    If they want to save a file to the server, the program should ask the user which file from the
                     ../client/data needs to be saved. After that, the user should specify the name of the file to
                      be saved on the server. This name should not contain spaces or tabs. If the user doesn't want
                       to specify a name, they should just press enter without typing anything. The server should generate
                       a unique name for this file and send back the id. The file should be saved in the
                        .../server/data/ folder.
                    */

        Path clientFilepath = Paths.get(saveLocation + fileName);

        System.out.println("Enter name of file to be saved on server: ");
        String serverFileName = reader.readLine();
        if (serverFileName == null || serverFileName.equals("")) {
            String fileType = fileName.substring(fileName.indexOf("."));
            serverFileName = "new-file" + untitledFileIncrement++ + fileType;
            //untitledFileIncrement++;
            //todo find a way to check the server fileList so it doesn't conflict
            // When the server starts it could read the new_file#.txt files, sort them, find the largest number, send it to the client
            // the client will increment it and continue -- cannot be static in client class - needs to be tied to object
            // make a method(s) in each class to pass the info back and forth to coordinate DataOutputRead/Write
        }
        client.fileContent = Files.readAllBytes(clientFilepath);
        return "PUT " + serverFileName;
    }

    static String createDeleteRequest(BufferedReader reader, String fileName) throws IOException {
        System.out.println("Do you want to delete the file by name or by id (1 - name, 2 - id): ");
        String deleteReply = reader.readLine();
        boolean hasLetters = deleteReply.replaceAll("\\d", "").length() > 0;
        if (!hasLetters) {
            int deleteReplyInt = Integer.parseInt(deleteReply);
            if (deleteReplyInt == 1) {
                return "DELETE BY_NAME " + fileName;
            } else if (deleteReplyInt == 2) {
                System.out.println("Enter id: ");
                int id = reader.read();
                return "DELETE BY_ID " + id;
            }
        }
        //todo what do I print here?
        return "DELETE BY_NAME " + deleteReply;
    }

    static void sendPutContent(String received, DataOutputStream output, Main client) throws IOException {
        if (received.startsWith("200")) {
            int length = client.fileContent.length;
            output.writeInt(length);
            output.write(client.fileContent, 0, length);
            //todo change start to id numbers
            String id = received.split(" ")[1];
            System.out.println("Response says that file is saved! ID = " + id + ", where " + id + " is an id of the file!");
        } else {
            System.out.println("The response says that creating the file was forbidden!");
        }
    }

    static void printDeleteResults(String received) {
            if (received.startsWith("200")) {
                System.out.println("The response says that this file was deleted successfully!");
            } else {
                System.out.println("The response says that this file is not found!");
            }
    }

    static void receiveGetContents(String received, DataInputStream input, BufferedReader reader) throws IOException {
        if (received.startsWith("200")) {
            int length = input.readInt();
            byte[] fileData = new byte[length];
            input.readFully(fileData, 0, length);
            System.out.println("The file was downloaded! Specify a name for it: ");
            String saveFileName = reader.readLine();
            String savedFilePathString = saveLocation + saveFileName;
            Path savedFilePath = Paths.get(savedFilePathString);

            if (!Files.exists(savedFilePath)) {
                Files.createFile(savedFilePath);
            }
                OutputStream writer = Files.newOutputStream(savedFilePath);
                writer.write(fileData);
                writer.flush();
                writer.close();
                System.out.println("File saved on the hard drive!");

        } else {
            System.out.println("The response says that this file is not found!");
        }
    }
    static  void closeAllStreams(Socket socket, DataInputStream input, DataOutputStream output) throws IOException {
        socket.close();
        input.close();
        output.close();
    }
}



//todo When client tries to get a file by ID that doesn't exist you should print:
//"The response says that this file is not found!"