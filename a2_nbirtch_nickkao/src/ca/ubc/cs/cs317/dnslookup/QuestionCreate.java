package ca.ubc.cs.cs317.dnslookup;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.*;

/**
 *                                  1  1  1  1  1  1
      0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                                               |
    /                     QNAME                     /
    /                                               /
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                     QTYPE                     |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                     QCLASS                    |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */

 //creates a datagramPacket based on node and adr given in constructor
public class QuestionCreate{

    private static Random random = new Random();

    private DNSNode node;
    private short ID; 
    private InetAddress adr;



    public QuestionCreate(DNSNode node, InetAddress adr){
        this.node = node;
        this.ID = createID();
        this.adr = adr;
    }

    public DatagramPacket createQ(int port){
        
        byte[] inputBuf = new byte[1024];

        byte[] headerBuf = new byte[12];
        
        
        headerBuf[0] = (byte)(this.ID >>> 8);
        headerBuf[1] = (byte)(this.ID & 0xff);
        
        //blue first half second row header
        headerBuf[2] = 0;
        //yellow second half second row header
        headerBuf[3] = 0;
        //dark green QDCount
        headerBuf[4] = 0;
        headerBuf[5] = 1;
        //light green ANCOUNT
        headerBuf[6] = 0;
        headerBuf[7] = 0;
        //mid green NSCOUNT
        headerBuf[8] = 0;
        headerBuf[9] = 0;
        //turquoise ARCOUNT
        headerBuf[10] = 0;
        headerBuf[11] = 0;

        Header header = new Header(headerBuf);
    
        //next will start at index 12 

        //QNAME
        //length will be the length of the hostname in node + 2 for the terminating byte and starting byte
        byte[] Qname = QnameCreate();

        //combine header and Q name into the input array
        // from stack overflow
        //https://stackoverflow.com/questions/5513152/easy-way-to-concatenate-two-byte-arrays    

        System.arraycopy(headerBuf, 0, inputBuf, 0, headerBuf.length);
        System.arraycopy(Qname, 0, inputBuf, headerBuf.length, Qname.length);

        int len = headerBuf.length + Qname.length;

        //Qtype comes from node
        inputBuf[len++] = 0;
        inputBuf[len++] = (byte)node.getType().getCode();
       
        //QClass is internet
        inputBuf[len++] = 0;
        inputBuf[len++] = 1;


        byte[] input = new byte[len];
        //need to have exact length of 
        for(int i = 0; i < len; i++){
            input[i] = inputBuf[i];
        }
        // 12 from header + length of hostname + 1; 
        
        DatagramPacket ret = new DatagramPacket(input, len, this.adr,port);

        return ret;
    }

    //create qname from the string given in node
    private byte[] QnameCreate(){

        String[] name = this.node.getHostName().split("\\.");
        int length = name.length; //length of hostname
        byte[] ret = new byte[this.node.getHostName().length() + 2]; 
        //each word seperated by . is in string array
        //first byte of every string is the length of the string 
        //each character in the string is represented as a byte
        //entire qname is terminated by a 0 byte

        int i = 0;
        for(int k = 0; k < length; k++){
            byte[] b = name[k].getBytes();
            ret[i++] = (byte) name[k].length();
            for(int j = 0; j < b.length; j++){
                ret[i++] = b[j];
            }
        }
        ret[i] = 0;
        return ret;
    }

    public DNSNode getNode(){
        return this.node;
    }

    public short getID(){
        return this.ID;
    }

    public InetAddress getAdr(){
        return this.adr;
    }

    //use random to create id
    private short createID(){
        short ret = (short) random.nextInt(1 << 15);
        return ret;
    }
}
