
import java.util.*;
import java.lang.*;
import java.net.*;
import java.lang.System;
import java.io.*;



//
// This is an implementation of a simplified version of a command 
// line ftp client. The program always takes two arguments


// port number is optional and should default to port 21 if not supplied 


public class CSftp
{
	static final int MAX_LEN = 255;
	static final int ARG_CNT = 2;
	static int DEFAULT_PORT_NUM = 21;
	static String[] validCommands = {"user", "pw", "quit", "get", "features", "cd", "dir"};
	
    public static void main(String [] args)
    {

	byte cmdString[] = new byte[MAX_LEN];
	
	// Get command line arguments and connected to FTP
	// If the arguments are invalid or there aren't enough of them
	// then exit.
	if (args.length == 0 || args.length > 2) {
		System.out.print("Usage: cmd ServerAddress ServerPort\n");
		return;
	}

	String ip = args[0];
	int portNum = DEFAULT_PORT_NUM; 
	if(args.length == 2){
		portNum = Integer.parseInt(args[1]);
	}

	Socket socket = new Socket();
	InetSocketAddress endpoint = new InetSocketAddress(ip, portNum);
	int timeout = 20 * 1000;

	try{
		socket.connect(endpoint ,timeout);
		
	} catch (IOException exception) {
		System.err.println("0xFFFC Control connection to " + ip + " on port " + portNum + " failed to open.");
		System.exit(1);
	} 
	
	try {
		// Call/Response flow between Client and Server
		PrintWriter clientOutput = new PrintWriter(socket.getOutputStream(), true);
		BufferedReader serverOutput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		
		while(true) {
			serverOutput(serverOutput, socket);
			System.out.print("csftp> ");
			clientOutput(clientOutput, socket, serverOutput);
		}
    		
	} catch (IOException exception) {
		System.err.println("998 Input error while reading commands, terminating.");
	}
    }   

	// Handle server output validation and outputs to terminal
	static String serverOutput(BufferedReader serverOutput, Socket socket) {
		String outputString = "";
		try {
			while ((outputString = serverOutput.readLine()) != null) {
				System.out.println("<-- " + outputString);
				if(outputString.matches("\\d{3}\\ .*")) {
					break;
				}
				if (!serverOutput.ready()) {
                    break;
                }
			}
		} catch (IOException exception) {
			System.err.println("0xFFFD Control connection I/O error, closing control connection.");
			try {
				 socket.close();
			} catch (IOException exception2) {
				System.err.println("0xFFFF Processing error. Socket failed to close. ");
			}
			System.exit(1);
		}
		return outputString;
	}

	// Handles server output validation for passive mode socket, outputs to terminal.
	static String serverOutputPassive(Socket passiveSocket) {
		String outputString = "";
		try {
			BufferedReader serverOutput = new BufferedReader(new InputStreamReader(passiveSocket.getInputStream()));
			while ((outputString = serverOutput.readLine()) != null) {
				System.out.println("<-- " + outputString);
				if (!serverOutput.ready()) {
                    break;
                }
			}
		} catch (IOException exception) {
			System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
			try {
				passiveSocket.close();
			} catch (IOException exception2) {
				System.err.println("0xFFFF Processing error. Socket failed to close. ");
			}
			System.exit(1);
		}

		return outputString;
	}
	
	// Handles client input validation and outputs to terminal/client 
	static void clientOutput(PrintWriter clientOutput, Socket socket, BufferedReader serverOutput) {		
		String outputString = "";		
		try{
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			outputString = stdIn.readLine().trim();
			//loops till cmd valid
			while(!validCmd(outputString)){
				if(doesCmdExist(outputString.split(" ")[0])){
					System.out.println("0x002 Incorrect number of arguments");
				}else if((outputString.length() != 0 && !validCmd(outputString) && !outputString.startsWith("#")) || outputString == null){
					System.out.println("0x001 Invalid command.");
				}
				System.out.print("csftp> ");
				outputString = stdIn.readLine().trim();
			}

		} catch(IOException e) {
			System.err.println("0xFFFE Input error while reading commands, terminating.");
			System.exit(1);
		}
		
		//prints to terminal and writes to client, handle quit/dir/get
		if (outputString != null && validCmd(outputString)) {
			String commandOutput = parseCmd(outputString);
			System.out.println("--> " + commandOutput);
			clientOutput.print(commandOutput + "\r\n");
			clientOutput.flush();
            if (outputString.contains("quit")) {
                try {
                    clientOutput.close();
                    serverOutput.close();
                    socket.close();
                } catch (IOException exception) {
    	            System.err.println("0xFFFF Processing error. Socket failed to close. ");
                    }
                System.exit(0);
			}     
			if (outputString.contains("dir")){
				String dirServerOutput = serverOutput(serverOutput, socket);
				Socket passiveSocket = passiveMode(dirServerOutput);
				if(passiveSocket != null){
					dirCmd(socket, passiveSocket, clientOutput, serverOutput);
				}
			}else if(outputString.contains("get")){
				String getServerOutput = serverOutput(serverOutput, socket);
				Socket passiveSocket = passiveMode(getServerOutput);
				if(passiveSocket != null){
					String file = paramSplit(outputString)[1];
					getCmd(socket, passiveSocket, clientOutput, serverOutput, file);
				}
			}
		}
	}
	
	// Handles multiple step dir command interaction
	static void dirCmd(Socket socket, Socket passive, PrintWriter clientOutput, BufferedReader serverOutput){
		System.out.println("--> " + "LIST");
		clientOutput.print("LIST" + "\r\n");
		clientOutput.flush();

		String response = serverOutput(serverOutput, socket);

		// print directory
		serverOutputPassive(passive);
        // close passive socket after data transfer
        try {
                passive.close();
        } catch (IOException exception) {
                System.err.println("0xFFFF Processing error. Socket failed to close. ");
        }
	}
	
	// Handles multiple step get command interaction
	static void getCmd(Socket socket, Socket passive, PrintWriter clientOutput, BufferedReader serverOutput, String file){
        System.out.println("--> " + "TYPE I");
        clientOutput.print("TYPE I" + "\r\n");
        clientOutput.flush();
        serverOutput(serverOutput, socket);

    	System.out.println("--> " + "RETR " + file);
        clientOutput.print("RETR " + file + "\r\n");
        clientOutput.flush();

        String response = serverOutput(serverOutput, socket);
               
        // read data and save to file
        serverOutputPassive(passive);

		// close passive socket after data transfer
		try {   
            passive.close();
        } catch (IOException exception) {
            System.err.println("0xFFFF Processing error. Socket failed to close. ");
    	}
	}

	//takes command returns the appropriate FTP command
	static String parseCmd(String given){
		String[] inputs = paramSplit(given);
		String answer = "";
		String cmd = inputs[0];
		String param = "";
		if(inputs.length == 2){
			param = inputs[1];
		}
		
		switch(cmd){
			case "user":
				answer = "USER";
				break;
			case "pw":
				answer = "PASS";
				break;
			case "quit":
				answer = "QUIT";
				break;
			case "features":
				answer = "FEAT";
				break;
			case "cd":
				answer = "CWD";
				break;
			default:
				answer = "PASV";
		}
		if (answer != "PASV") { answer = answer + " " + param; } 
		return answer;
	}

	// Creates a passive socket for data transfer
	static Socket passiveMode(String serverOutput) {
		// https://stackoverflow.com/questions/14498331/what-should-be-the-ftp-response-to-pasv-command
		int sIndex = serverOutput.indexOf("(") + 1;
		int eIndex = serverOutput.indexOf(")");
		String sub = serverOutput.substring(sIndex, eIndex);
		String[] num = sub.split(",");
		String ip = num[0] + "." + num[1] + "." + num[2] + "." + num[3];
		int portNum = (Integer.parseInt(num[4]) * 256) + Integer.parseInt(num[5]);

        Socket socket = new Socket();
    	InetSocketAddress endpoint = new InetSocketAddress(ip, portNum);
   		int timeout = 10 * 1000;

		try {
			socket.connect(endpoint, timeout);
		} catch (IOException e) {
                	System.err.println("0x3A2 Data transfer connection to " + ip + " on port " + portNum + " failed to open.");
			try { 
				socket.close();
			} catch (IOException exception) {
				System.err.println("0xFFFF Processing error. Socket failed to close. ");
			}
			socket = null;
         	}
		return socket;
	}

	//returns true if the given command is in the validCommands array 
	static boolean doesCmdExist(String given){
		return Arrays.asList(validCommands).contains(given);
	}

	//returns true if command is valid
	//assumes first word is without leading spaces
	//trim given string before inputing 
	static boolean validCmd(String given){
		String[] inputs = paramSplit(given);
		String cmd = inputs[0];
		
		if(!doesCmdExist(cmd)){
			return false;
		}

		switch(cmd){
			//only one parameter
			case "quit":
			case "features":
			case "dir":
				if(inputs.length != 1){
					return false;
				}
				break;
			//two params
			default:
				if(inputs.length != 2){
					return false;
				}
				break;
		}
		return true;
	}

	// processes a given String into an array of parameters
	static String[] paramSplit(String given){
		String[] inputs = given.split(" ");
		return inputs;
	}
}