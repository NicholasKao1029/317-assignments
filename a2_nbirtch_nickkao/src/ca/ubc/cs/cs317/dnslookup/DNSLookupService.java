package ca.ubc.cs.cs317.dnslookup;

import java.io.Console;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.*;

public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL = 10;

    private static InetAddress rootServer;
    private static boolean verboseTracing = false;
    private static DatagramSocket socket;

    private static DNSCache cache = DNSCache.getInstance();

    private static Random random = new Random();

    /**
     * Main function, called when program is first invoked.
     *
     * @param args list of arguments specified in the command line.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Invalid call. Usage:");
            System.err.println("\tjava -jar DNSLookupService.jar rootServer");
            System.err.println("where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
            System.exit(1);
        }

        try {
            rootServer = InetAddress.getByName(args[0]);
            System.out.println("Root DNS server is: " + rootServer.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Invalid root server (" + e.getMessage() + ").");
            System.exit(1);
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000);
        } catch (SocketException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        Scanner in = new Scanner(System.in);
        Console console = System.console();
        do {
            // Use console if one is available, or standard input if not.
            String commandLine;
            if (console != null) {
                System.out.print("DNSLOOKUP> ");
                commandLine = console.readLine();
            } else
                try {
                    commandLine = in.nextLine();
                } catch (NoSuchElementException ex) {
                    break;
                }
            // If reached end-of-file, leave
            if (commandLine == null) break;

            // Ignore leading/trailing spaces and anything beyond a comment character
            commandLine = commandLine.trim().split("#", 2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty()) continue;

            String[] commandArgs = commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") ||
                    commandArgs[0].equalsIgnoreCase("exit"))
                break;
            else if (commandArgs[0].equalsIgnoreCase("server")) {
                // SERVER: Change root nameserver
                if (commandArgs.length == 2) {
                    try {
                        rootServer = InetAddress.getByName(commandArgs[1]);
                        System.out.println("Root DNS server is now: " + rootServer.getHostAddress());
                    } catch (UnknownHostException e) {
                        System.out.println("Invalid root server (" + e.getMessage() + ").");
                        continue;
                    }
                } else {
                    System.out.println("Invalid call. Format:\n\tserver IP");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("trace")) {
                // TRACE: Turn trace setting on or off
                if (commandArgs.length == 2) {
                    if (commandArgs[1].equalsIgnoreCase("on"))
                        verboseTracing = true;
                    else if (commandArgs[1].equalsIgnoreCase("off"))
                        verboseTracing = false;
                    else {
                        System.err.println("Invalid call. Format:\n\ttrace on|off");
                        continue;
                    }
                    System.out.println("Verbose tracing is now: " + (verboseTracing ? "ON" : "OFF"));
                } else {
                    System.err.println("Invalid call. Format:\n\ttrace on|off");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("lookup") ||
                    commandArgs[0].equalsIgnoreCase("l")) {
                // LOOKUP: Find and print all results associated to a name.
                RecordType type;
                if (commandArgs.length == 2)
                    type = RecordType.A;
                else if (commandArgs.length == 3)
                    try {
                        type = RecordType.valueOf(commandArgs[2].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid query type. Must be one of:\n\tA, AAAA, NS, MX, CNAME");
                        continue;
                    }
                else {
                    System.err.println("Invalid call. Format:\n\tlookup hostName [type]");
                    continue;
                }
                findAndPrintResults(commandArgs[1], type);
            } else if (commandArgs[0].equalsIgnoreCase("dump")) {
                // DUMP: Print all results still cached
                cache.forEachNode(DNSLookupService::printResults);
            } else {
                System.err.println("Invalid command. Valid commands are:");
                System.err.println("\tlookup fqdn [type]");
                System.err.println("\ttrace on|off");
                System.err.println("\tserver IP");
                System.err.println("\tdump");
                System.err.println("\tquit");
                continue;
            }

        } while (true);

        socket.close();
        System.out.println("Goodbye!");
    }

    /**
     * Finds all results for a host name and type and prints them on the standard output.
     *
     * @param hostName Fully qualified domain name of the host being searched.
     * @param type     Record type for search.
     */
    private static void findAndPrintResults(String hostName, RecordType type) {

        DNSNode node = new DNSNode(hostName, type);
        printResults(node, getResults(node, 0));
    }

    /**
     * Finds all the result for a specific node.
     *
     * @param node             Host and record type to be used for search.
     * @param indirectionLevel Control to limit the number of recursive calls due to CNAME redirection.
     *                         The initial call should be made with 0 (zero), while recursive calls for
     *                         regarding CNAME results should increment this value by 1. Once this value
     *                         reaches MAX_INDIRECTION_LEVEL, the function prints an error message and
     *                         returns an empty set.
     * @return A set of resource records corresponding to the specific query requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel) {

        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }
        

        //check the cache to see if inside
        Set<ResourceRecord> ret = null;
        ret = cache.getCachedResults(node);
        //cache returns something not empty already got it.
        if(!ret.isEmpty()){
            return ret;
        }

        //check the cache to see if cname node is inside
        DNSNode cNode = new DNSNode(node.getHostName(), RecordType.CNAME);
        ret = cache.getCachedResults(cNode);
        if(!ret.isEmpty()){
            // handle recursive cname results
            ResourceRecord cRet = (ResourceRecord)ret.toArray()[0];
            DNSNode iNode = new DNSNode(cRet.getTextResult(), node.getType());
            return getResults(iNode, indirectionLevel + 1);
        }

        //not in cache, retrieve results from server and try again.
        retrieveResultsFromServer(node, rootServer);
        ret = cache.getCachedResults(node);

        //cache returns something
        if(!ret.isEmpty()){
            return ret;
        }

        //cache returns cname node if inside
        ret = cache.getCachedResults(cNode);
        if(!ret.isEmpty()){
            // handle recursive cname results
            ResourceRecord cRet = (ResourceRecord)ret.toArray()[0];
            DNSNode iNode = new DNSNode(cRet.getTextResult(), node.getType());
            return getResults(iNode, indirectionLevel + 1);
        }

        return Collections.emptySet();

    }

    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in iterative mode,
     * and the query is repeated with a new server if the provided one is non-authoritative.
     * Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     */
    private static void retrieveResultsFromServer(DNSNode node, InetAddress server) {

        //Create query packet with Header/Question
        QuestionCreate q = new QuestionCreate(node, server);
        DatagramPacket sendPacket = q.createQ(DEFAULT_DNS_PORT);

        //Create response packet
        byte[] resBuf= new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(resBuf, 1024);
        Decoder d = null;
        int timeoutMax = 2;
        boolean validResponse = false;
     
        //Send query packet
        querying:
        while (timeoutMax > 0) {
            try {
                socket.send(sendPacket);

                if (verboseTracing) {
                    // 1. 
                    System.out.println();
                    System.out.println();
                    // 2. 
                    System.out.println("Query ID" + "     " + q.getID() + " " + node.getHostName() + "  " + node.getType() + " " + "-->" + " " + server.getHostAddress());
                }
          
                //recieve response 
                //System.out.println("Receiving...");
                while (true) {
                    socket.receive(responsePacket);
                    // decode response
                    d = new Decoder(responsePacket);
                    
                    /*
                    System.out.println("Query ID " +  q.getID());
                    System.out.println("Response ID " + d.getID()); 
                    System.out.println("QDCount response " + d.getnumQs());
                    System.out.println("NumAns response " + d.getnumAns());
                    System.out.println("NumNS response " + d.getnumNSer());
                    System.out.println("numAR response " + d.getnumAR());

                    System.out.println("length of Answer Array " + d.getAnswers().size());
                    System.out.println("length of NS Array " + d.getNameServers().size());
                    System.out.println("length of AR Array " + d.getAdditionals().size());
                    */

                    // ensure correct response
                    if (d.getID() != q.getID()) {
                        System.out.println("Reset Receive");
                        resBuf= new byte[1024];
                        responsePacket = new DatagramPacket(resBuf, 1024);
                    } else {
                        validResponse = true;
                        break querying;
                    }
                }
            } catch(SocketTimeoutException e) {
                e.printStackTrace();
                timeoutMax--;
            } catch(Exception e) {
               
                return;
            }
        }

        if (!validResponse) {
            return;
        }

        byte RCode = d.getRCCode();
        if (RCode != 0 || d.getTC()) {
            // some error occured.
            return;
        }
        
        //get resource record ArrayLists
        ArrayList<ResourceRecord> Answers = d.getAnswers();
        ArrayList<ResourceRecord> NameServers = d.getNameServers();
        ArrayList<ResourceRecord> Additionals = d.getAdditionals();

	if (RCode == 0 && d.getAuthoritative() == "true" && Answers.size() == 0) {
            return;
        }

        //Need to print trace of all queries made and responses received before printing results
        if (verboseTracing) {
            // 3. 
            System.out.println("Response ID:" + " " + d.getID() + " " + "Authoritative" + " " + "=" + " " + d.getAuthoritative());
            // 4. 
            System.out.println("  " + "Answers" + " " + "(" + Answers.size() + ")");
            // 5. 
           for (int i = 0; i < Answers.size(); i++) {
                ResourceRecord Answer = Answers.get(i);
                verbosePrintResourceRecord(Answer, Answer.getType().getCode());
            }
            // 6. 
            System.out.println("  " + "Nameservers" + " " + "(" + NameServers.size() + ")");
            
            for (int i = 0; i < NameServers.size(); i++) {
                ResourceRecord NameServer = NameServers.get(i);
                verbosePrintResourceRecord(NameServer, NameServer.getType().getCode());
            }

            System.out.println("  " + "Additional Information" + " " + "(" + Additionals.size() + ")");


            for (int i = 0; i < Additionals.size(); i++) {
                ResourceRecord Additional = Additionals.get(i);
                verbosePrintResourceRecord(Additional, Additional.getType().getCode());
            }
        }

        //At this point, we have decoded packet, finished tracing:

        //Cache all Answers/Additionals, do not cache NameServers
        for (int i = 0; i < Answers.size(); i++) {
            ResourceRecord Answer = Answers.get(i);
            //System.out.println("CACHING");
            cache.addResult(Answer);
        }

        for (int i = 0; i < Additionals.size(); i++) {
            ResourceRecord Additional = Additionals.get(i);
            //System.out.println("Cache Additional");
            cache.addResult(Additional);
        }

        // Answers exist, return records 
        if (d.getAuthoritative() == "true" && Answers.size() > 0) {
            return;
        }

        if (NameServers.size() == 0) {
            return;
        }

        try {
            ResourceRecord firstNameServer = NameServers.get(0);
            //System.out.println("FIRST NAME SERVER " + firstNameServer.getHostName());
            if (Additionals.size() > 0) {
                //look for current server in additionals
                for (int i = 0; i < Additionals.size(); i++) {
                    //System.out.printf("Additionals.get(i).getHostName() = %s \n", Additionals.get(i).getHostName());
                    if (Additionals.get(i).getHostName() == node.getHostName() && Additionals.get(i).getType() == node.getType()) { 
                        //System.out.println("FOUND CURRENT SERVER");
                        return;
                    }
                }
            }
            InetAddress ipNS = null;
            //look for ip address of name server in additionals
            for (int i = 0; i < Additionals.size(); i++) {
                if (Additionals.get(i).getHostName() == firstNameServer.getTextResult() && Additionals.get(i).getType() == RecordType.A) {
                    ipNS = Additionals.get(i).getInetResult();
                 }
            }
            
            if (ipNS == null) {
                //resolve name server as IPV4 RecordType.A via query
                //System.out.println("RESOLVE NAME SERVER AS IPV4 VIA QUERY:  " + firstNameServer.getTextResult());
                DNSNode nsNode = new DNSNode(firstNameServer.getTextResult(), RecordType.A);
                Set<ResourceRecord> resolved = getResults(nsNode, 0);
                // https://stackoverflow.com/questions/20511140/how-to-get-first-item-from-a-java-util-set
                for (ResourceRecord firstRecord: resolved) {
                    retrieveResultsFromServer(node, firstRecord.getInetResult());
                    break;
                }
            } else {
                // found address of name server in additionals
                //System.out.println("FOUND ADDRESS OF NAME SERVER IN ADDITIONALS");
                retrieveResultsFromServer(node, ipNS);
            }
        } catch (Exception e) {
                return;
        }

        return;
    }

    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(),
                    record.getTextResult());
    }

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node, Set<ResourceRecord> results) {
        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), -1, "0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), record.getTTL(), record.getTextResult());
        }
    }

}