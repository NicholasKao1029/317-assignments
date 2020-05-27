#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <netdb.h>
#include <pthread.h>

#include <sys/types.h>
#include <sys/socket.h>
#include "dir.h"
#include "usage.h"
#include <ifaddrs.h>
#define _GNU_SOURCE

char initPath[1024];
int pasvMode;
int loggedIn;
int clientd;
int pasvd;
int quit;

// send response to clientd socket
void* sendResponse(char response[]) {
    char buffer[1024];
    ssize_t length = snprintf(buffer, 1024, "%s\r\n", response);

    if (send(clientd, buffer, length, 0) != length) {
         perror("Failed to send to the socket");
    }
}

// send response to pasvd socket
void* sendResponsePASV(char response[]) {
    char buffer[1024];
    ssize_t length = snprintf(buffer, 1024, "%s\r\n", response);

    if (send(pasvd, buffer, length, 0) != length) {
         perror("Failed to send to the socket");
    }
}


// Returns empty pointer or the IPV4 address in numeric form
/*char* getIP(){
    struct ifaddrs *ifap, *ifa;
    int family, s;
    // 1025 is NI max host 
    char host[1025];

    if (getifaddrs(&ifap) == -1) {
        return &host;
    }
    
    //need to only get IPV4 addresses
    //AF_INET
    ifa = ifap;
    while(ifa != NULL){
        family = ifa->ifa_addr->sa_family;
        
        //check address is IPV4 and not localhost
        //1 flag in getnameinfo set for NI_NUMERICHOST
        if(family == AF_INET){
            s = getnameinfo(ifa->ifa_addr, 
                            sizeof(struct sockaddr_in),
                            host, 1025, NULL, 0 , 1);
            if(s == 0){
                if(host != "127.0.0.1"){
                    freeifaddrs(ifap);
                    return &host;
                }
            }
        }

        ifa = ifa->ifa_next;
    }

    freeifaddrs(ifap);
    return &host;
}*/

// Create pasvd socket
int createPasvConnection(){

    return 0;

   /* if (pasvMode == 1 && pasvd != 0) {
        close(pasvd);
    }

    int portNum = 0;

    int pasvsocketd = socket(PF_INET, SOCK_STREAM, 0);
    if(pasvsocketd < 0 ){
      perror("Failed to create the socket.");
    }

    int value = 1;

    if (setsockopt(pasvsocketd, SOL_SOCKET, SO_REUSEADDR, &value, sizeof(int)) != 0)
    {
        perror("Failed to set the socket option");
        
    }

    struct sockaddr_in address;
    
    bzero(&address, sizeof(struct sockaddr_in));
    
    address.sin_family = AF_INET;
    
    address.sin_port = portNum;
    
    address.sin_addr.s_addr = INADDR_ANY;
    
    if (bind(pasvsocketd, (const struct sockaddr*) &address, sizeof(struct sockaddr_in)) != 0)
    {
        perror("Failed to bind the socket");
    }
    
    // Set the socket to listen for connections
    if (listen(pasvsocketd, 4) != 0)
    {
        perror("Failed to listen for connections");
        
    }

    while (true)
    {
        // Accept the connection
        struct sockaddr_in pasvAddress;
        socklen_t pasvAddressLength = sizeof(struct sockaddr_in);
        
        // get ip
        // use get ifaddrs() to iterate over all interfaces and find address
        // hint: filter out non-IPv4 addressess and the loopback address (localhost) during iteration
        // ALTERNATIVE: Can we use main socket IP?  https://piazza.com/class/k4szj0ldhzy433?cid=425_f5       

        //creates linked list of structures describing network interfaces

        struct ifaddrs *ifap, *ifa;
        int family, s, n;

        if (getifaddrs(&ifap) == -1) {
            perror("failed to retrieve interfaces");
        }


        // get port
        getsockname(pasvsocketd, (struct sockaddr*) &pasvAddress, &pasvAddressLength);
        int pasvPortNum = ntohs(pasvAddress.sin_port); 

        // format ip/port for response
        // public IP address (h1,h2,h3,h4,p1,p2) where h1.h2.h3.h4 is ip address and p1 * 256 + p2 is the data port.
        sendResponse("227 Entering Passive Mode (h1,h2,h3,h4,p1,p2).");
      
        printf("Waiting for incomming connections...\n");
        
        //pasvd set
        pasvd = accept(pasvsocketd, (struct sockaddr*) &pasvAddress, &pasvAddressLength);
        
        if (pasvd < 0)
        {
            perror("Failed to accept the client connection");
            
            continue;
        }
        
        printf("Accepted the client connection from %s:%d.\n", inet_ntoa(pasvAddress.sin_addr), ntohs(pasvAddress.sin_port));
        return 1;
        
        // Not necessary, but usable for pasv connection - piazza
        // pthread_t thread;
        
        if (pthread_create(&thread, NULL, PASV_CONNECTION_FUNCTION, &pasvd) != 0)
        {
            perror("Failed to create the thread");
            
            continue;
        }
        
        // The main thread just waits until the interaction is done
        pthread_join(thread, NULL);
        
    }
    return 0;*/
}

// Login Interaction
void* userCommand (char* param) {
    if (param == NULL) { 
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if (loggedIn == 1) {
        sendResponse("530 Already logged in.");
    } else if (strcmp(param, "cs317") == 0) {
        // handle successful user login
        loggedIn = 1;
        sendResponse("230 User logged in, proceed.");
    } else {
        sendResponse("530 Not logged in.");
    }
}

// Clean up sockets, CSftp state
void* quitCommand () {
    sendResponse("221 Service closing control connection.");
    close(clientd);
    close(pasvd);
    clientd = 0;
    pasvd = 0;
    loggedIn = 0;
    pasvMode = 0;
    quit = 0;
}

// Set working directory
void* cwdCommand (char* param) {
    if (param == NULL) {
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if (strncmp(param, "./", 2) == 0 || strncmp(param, "~/", 2) == 0 || strncmp(param, "..", 2) == 0 || (strstr(param, "../") != NULL)) {
        sendResponse("550 Requested action not taken.");
    } else if (chdir(param) == 0) {
        // handle successful cwd
         sendResponse("250 Requested file action okay, completed.");
    } else {
         sendResponse("550 Requested action not taken.");
    }
}

// Set working directory to parent directory
void* cdupCommand (char* param) {
    char cwdPath[1024];
    getcwd(cwdPath, 1024);
    
    if (param != NULL) {
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if (strcmp(cwdPath, initPath) == 0) {
        sendResponse("550 Requested action not taken.");
    } else if (chdir("..") == 0) {
        // handle successful cwd
        sendResponse("200 Command okay.");
    } else {
        sendResponse("550 Requested action not taken.");
    }  
}

// Set type
void* typeCommand (char* param) {
    if (param == NULL) {
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if ((strcasecmp(param, "I") == 0) || (strcasecmp(param,"A") == 0)) {
        // handle type Image and ASCII
        sendResponse("200 Command okay.");
    } else {
        sendResponse("504 Command not implemented for that parameter.");
    }   
}

// Set mode
void* modeCommand (char* param) {
    if (param == NULL) {
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if (strcasecmp(param, "S") == 0) { 
        //handle mode Stream
        sendResponse("200 Command okay.");
    } else {
        sendResponse("504 Command not implemented for that parameter.");
    }   
}

// Set structure type
void* struCommand (char* param) { 
    if (param == NULL) {
        sendResponse("501 Syntax error in parameters or arguments.");  
    } else if (strcasecmp(param, "F") == 0) { 
        //handle stru File
        sendResponse("200 Command okay.");
    } else {
        sendResponse("504 Command not implemented for that parameter.");
    }
}

// Retrieve copy of file through pasv connection
void* retrCommand (char* param) {
    if (param == NULL) { 
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if (pasvMode == 0) {
        sendResponse("425 Can't open data connection.");
    } else if (pasvMode == 1) {
        sendResponse("125 Data connection already open; transfer starting.");        
        // transfer copy of file specified in param
        sendResponse("226 Closing data connection. Requested file action successful.");

        close(pasvd);
        pasvd = 0;
        pasvMode = 0;
    } 
}

// Attempt to initiate pasv connection
void* pasvCommand (char* param) {
    if (param != NULL) { 
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if (createPasvConnection() == 1) {
        // handle successful pasv connection
        pasvMode = 1;
    } else {
        sendResponse("501 Syntax error in parameters or arguments.");
    }
}

// Produce directory listing over pasv connection
void* nlstCommand (char* param) {
    if(param != NULL) {
        sendResponse("501 Syntax error in parameters or arguments.");
    } else if (pasvMode == 0) {
        sendResponse("425 Can't open data connection.");
    } else if (pasvMode == 1) {
        char cwdPath[1024];
        getcwd(cwdPath, 1024);

        sendResponse("125 Data connection already open; transfer starting.");
        if (listFiles(pasvd, cwdPath) < 0) {
            sendResponse("450 Requested file action not taken.");
        } else {
            sendResponse("226 Closing data connection. Requested file action successful.");
        }

        close(pasvd);
        pasvd = 0;
        pasvMode = 0;
    }
}

// Parse command/param, handle accepted commands
void* handleCommand (char input[]) {
    char* command = strtok(input, " \r\n");
    char* param = strtok(NULL, " \r\n");

    if (strcasecmp(command, "USER") == 0) {
        userCommand(param);
    } else if (strcasecmp(command, "QUIT") == 0) {
        quit = 1;
    } else if (loggedIn == 0) {
        sendResponse("530 Not logged in.");
    } else if (strcasecmp(command, "CWD") == 0) {
        cwdCommand(param);
    } else if (strcasecmp(command, "CDUP") == 0) {
        cdupCommand(param);
    } else if (strcasecmp(command, "TYPE") == 0) {
        typeCommand(param);
    } else if (strcasecmp(command, "MODE") == 0) {
        modeCommand(param);
    } else if (strcasecmp(command, "STRU") == 0) {
        struCommand(param);
    } else if (strcasecmp(command, "RETR") == 0) {  
        retrCommand(param);
    } else if (strcasecmp(command, "PASV") == 0) {
        pasvCommand(param);
    } else if (strcasecmp(command, "NLST") == 0) {       
        nlstCommand(param);
    } else { 
        sendResponse("500 Syntax error, command unrecognized.");
    }
}

// Initialize CSftp state, Client/Server interaction
void* interact(void* args) {
    clientd = *(int*) args;    
    char buffer[1024];
    bzero(buffer, 1024);
    ssize_t length;

    sendResponse("220 Service ready for new user.");
   
    getcwd(initPath, 1024);
  
    pasvMode = 0;
    loggedIn = 0;
    quit = 0;

 
    while (true)
    {
        if (quit == 1) {
            break;
        }

        bzero(buffer, 1024);
        
        length = recv(clientd, buffer, 1024, 0);
        
        if (length < 0) {
            perror("Failed to read from the socket");
            break;
        }
        
        if (length == 0) {
             // Skip
        }

        if (length > 0) {
            handleCommand(buffer);
        }
    }
    
    quitCommand();
    return NULL;
}

// Initialize clientd socket, client/server interaction
int main(int argc, char *argv[]) {

    int i;
    
    if (argc != 2) {
      usage(argv[0]);
      return -1;
    }

    char* p = argv[1];
    int portNum = atoi(p);

    int socketd = socket(PF_INET, SOCK_STREAM, 0);
    if(socketd < 0 ){
      perror("Failed to create the socket.");
      exit(-1);
    }

    int value = 1;

    if (setsockopt(socketd, SOL_SOCKET, SO_REUSEADDR, &value, sizeof(int)) != 0)
    {
        perror("Failed to set the socket option");
        
        exit(-1);
    }

    struct sockaddr_in address;
    
    bzero(&address, sizeof(struct sockaddr_in));
    
    address.sin_family = AF_INET;
    
    address.sin_port = htons(portNum);
    
    address.sin_addr.s_addr = INADDR_ANY;
    
    if (bind(socketd, (const struct sockaddr*) &address, sizeof(struct sockaddr_in)) != 0)
    {
        perror("Failed to bind the socket");
        exit(-1);
    }
    
    // Set the socket to listen for connections
    if (listen(socketd, 4) != 0)
    {
        perror("Failed to listen for connections");
        
        exit(-1);
    }

    while (true)
    {
        // Accept the connection
        struct sockaddr_in clientAddress;
        
        socklen_t clientAddressLength = sizeof(struct sockaddr_in);
        
        printf("Waiting for incomming connections...\n");
        
        int clientd = accept(socketd, (struct sockaddr*) &clientAddress, &clientAddressLength);
        
        if (clientd < 0) {
            perror("Failed to accept the client connection");
            continue;
        }
        
        printf("Accepted the client connection from %s:%d.\n", inet_ntoa(clientAddress.sin_addr), ntohs(clientAddress.sin_port));
        
        // Create a separate thread to interact with the client
        pthread_t thread;
        
        if (pthread_create(&thread, NULL, interact, &clientd) != 0)
        {
            perror("Failed to create the thread");
            
            continue;
        }
        
        // The main thread just waits until the interaction is done
        pthread_join(thread, NULL);
        
        printf("Interaction thread has finished.\n");
    }

    return 0;
}

