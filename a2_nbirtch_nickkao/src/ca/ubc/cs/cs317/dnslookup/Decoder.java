package ca.ubc.cs.cs317.dnslookup;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;


//decode a response DatagramPacket
//This class is purely a decoder giving information 
//The logic on what action to take will be determined by caller in DNSLookup 
public class Decoder{

    private Header header;
    private int length;
    private ByteBuffer bBuffer;
    // Are these necessary?
    private byte[] Qname;
    private String QnameString;
    private short Qtype;
    private short Qclass;
    private int numQs;
    private int numAns;
    private int numNSer;
    private int numAR;
    // ^^^
    private ArrayList<ResourceRecord> Answers;
    private ArrayList<ResourceRecord> NameServers;
    private ArrayList<ResourceRecord> Additionals;
    private boolean Authoritative;
    private short ID;
    private byte RCode;
    private boolean TC;
    private int offset;
    private int dNameOffset;

    public Decoder(DatagramPacket d){
        
        Answers = new ArrayList<ResourceRecord>();
        NameServers = new ArrayList<ResourceRecord>();
        Additionals = new ArrayList<ResourceRecord>();
        decodesPacket(d);
    }

    public ArrayList<ResourceRecord> getAnswers() {
        return Answers;
    }
    public int getnumNSer(){
        return numNSer;
    }
    public int getnumAR(){
        return numAR;
    }

    public int getnumAns(){
        return numAns;
    }
    public int getnumQs(){
        return this.numQs;
    }

    public ArrayList<ResourceRecord> getNameServers() {
        return NameServers;
    }

    public ArrayList<ResourceRecord> getAdditionals() {
        return Additionals;
    }

    public short getID() {
        return ID;
    }

    public byte getRCCode() {
        return RCode;
    }

    public boolean getTC() {
        return TC;
    }

    public String getAuthoritative() {
        return (Authoritative) ? "true" : "false";
    }



    public short decodeShort(byte[] data, int index){
       short ans = (short)(((data[index] & 0xFF) << 8) + (data[index + 1] & 0xFF));
        return ans;
    }

    public int decodeInt(byte[] data, int index){
        int ans = (int)(((data[index] & 0xFF)<<24) + ((data[index + 1] & 0xFF) << 16) + ((data[index + 2] & 0xFF) << 8) + (data[index + 3] & 0xFF));
        return ans;
    }

    //decode datagrampacket
    //header 
    public void decodesPacket(DatagramPacket d){
        
        byte[] data = new byte[d.getLength()];
        System.arraycopy(d.getData(), d.getOffset(), data, 0 , d.getLength());
        //bBuffer = ByteBuffer.wrap(data);
        
        byte[] headerData = Arrays.copyOfRange(data, 0, 12);
        this.offset = 12; //initialized for header (12 bytes)
        this.length = d.getLength();
        header = new Header(headerData);
        ID = header.getID();
        RCode = header.getRCCODE();
        if(RCode != 0){
            //not sure if we're allowed to throw errors as it might mess with auto testing - piazza, just need to abort query I think?
            //throw new NullPointerException("RCODE indicates error");
            return;
        }
        TC = header.getTC();
        if(TC){
            //throw new NullPointerException("TC bit set, time to fail");
            return;
        }
        numQs = header.getQDCOUNT();
         numAns = header.getANCOUNT();
         numNSer = header.getNSCOUNT();
         numAR = header.getARCOUNT();

        Authoritative = header.getAA() && header.getQR();

       
        //question is a variable length based on the domain name
        // can be multiple questions
        // Do we even need to extract these in decoding? or can we just increment offset to resource record section.
        for (int i = 0; i < numQs; i++) {
            int index = this.offset;
            String qName = parseString(data, this.offset);
           
            // !!! qName doesn't have a beginning byte !!!
            // parseString seems to be working properly, so it seems to be an issue with the offsets?

            //qName.length for each character plus 1 for terminating bytes of name
            //plus 4 bytes, 2 for qtype 2 for qclass
            //this.offset += qName.length() + 1;
            this.offset++;
            String qType = null;
            this.offset += 2;
            String qClass = null;
            this.offset += 2;
        }
        
        // System.out.println(Arrays.toString(data));

        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
       
        
        
        // make sure offset tracked, refactor to bytebuffer, make offset class variable?
        for (int i = 0; i < numAns; i++) {
            Answers.add(parseResourceRecord(data));
           
        }
        for (int i = 0; i < numNSer; i++) {
            NameServers.add(parseResourceRecord(data));
            
        }
        for (int i = 0; i < numAR; i++) {
            Additionals.add(parseResourceRecord(data));
            
        }


        
       

        
    } 

    //parse single resource record
    private ResourceRecord parseResourceRecord(byte[] data){
        // Name (Domain Name) - dynamic length
        String name = parseString(data, this.offset);
      
        // Type - two octet
        int code = decodeShort(data,this.offset);
        RecordType type = RecordType.getByCode(code);
        this.offset += 2;
      
        
        // Class - two octet
        int classShort = decodeShort(data, this.offset);
        this.offset += 2;
        

        
        // TTL - 32 bit unsigned int
        int ttlInt = decodeInt(data, this.offset);
        long ttl = (long)ttlInt;
        this.offset += 4;
      
        
        // RDLength - unsigned 16 bit int specifies length in octets of RData
        short rd = decodeShort(data , offset);
        int RDLength = (int)rd;
        this.offset += 2;
        

        // RData - dynamic ^^^, format varies based on Type/Class of resource record.
        byte[] rdata = new byte[RDLength];
        int offsetHelp = this.offset;
        for (int i = 0; i < RDLength; i++) {
            rdata[i] = data[offsetHelp++];
        }

        // StringBuilder sb = new StringBuilder();
        // for (byte b : rdata) {
        //     sb.append(String.format("%02X ", b));
        // }
        // System.out.println(sb.toString());

        // RData Formats:
        // A (3.4.1), CNAME (3.3.1), and NS (3.3.11) and AAAA (IPV6 address)
        
        String sResult;
        InetAddress iaResult;

        for (int i = 0; i < RDLength; i++) {
            this.offset++;
        }

        switch(type.getCode()){
            case 1:
            try {
                iaResult = InetAddress.getByAddress(rdata);              
                return new ResourceRecord(name, type, ttl, iaResult);
            } catch (Exception e) {
               // new UnknownHostException("Rdata unknown host");
            }
            case 5: 
                sResult = RdataParse(rdata, data);
                
                return new ResourceRecord(name, type, ttl, sResult);
            case 2: 
                sResult = RdataParse(rdata, data);
              
                return new ResourceRecord(name, type, ttl, sResult);
            case 28: 
            try {
                iaResult = InetAddress.getByAddress(rdata);
                
                return new ResourceRecord(name, type, ttl, iaResult);
            } catch (Exception e) {
              //  new UnknownHostException("Rdata unknown host");
            }     
            default: 
                sResult = "---";
                return new ResourceRecord(name, type, ttl, sResult);
        }
    }

    //calculate the offset for qName's
    private int calculateOffset(byte[] data, int index){

        byte label = data[index];
        byte identifier = (byte)(label & (byte)0xC0);
        //means it's a pointer
        if(identifier == (byte)0xC0){
            
            return 2;
        }else{
            return -1;
        }

    }

    //take off terminating .
    private String parseString(byte[] data, int index){
        String t = parseS(data, index);
        if(t.length() == 0){
            return t;
        }
        this.offset += this.dNameOffset;
        this.dNameOffset = 0;
        return t.substring(0, t.length()-1);
    }

    //takes in the whole datagram packet data
    //and the offset to the beggining of where to decode string 
    private String parseS(byte[] data, int index){
            String ans = "";
            //label can be label or pointer 
            //label has first two bits 00
            //pointer has 11
            byte label = data[index];
            
            
            byte identifier = (byte)(label & (byte)0xC0);
            
            //pointer           
            if(identifier == (byte)0xC0){
                
                int offset = ((int)(data[index] & 0x3F) << 8) + ((int)data[index + 1] & 0xFF);
                this.dNameOffset += 2;
            
                return ans += parseSnoInc(data, offset); 
            }
            else if(identifier == 0){
                if(label == 0){
                    //this.dNameOffset++;
                    return ans;
                }else{
                    int length = data[index++];
                    this.dNameOffset++;
                    for(int i = 0; i < length; i++){
                        byte curr = data[index++];
                        char t = (char)curr;
                        ans += t;
                        this.dNameOffset++;
                    }
                    return ans += "."+ parseS(data, index);
                } 
            }else{
                return null;
            }
    }

    private String parseSnoInc(byte[] data, int index){
        String ans = "";
        //label can be label or pointer 
        //label has first two bits 00
        //pointer has 11
        byte label = data[index];
        
        byte identifier = (byte)(label & (byte)0xC0);
        //pointer           
        if(identifier == (byte)0xC0){
            int offset = ((int)(data[index] & 0x3F) << 8) + ((int)data[index + 1] & 0xFF);
            return ans += parseSnoInc(data, offset); 
        }
        else if(identifier == 0){
            if(label == 0){
                return ans;
            }else{
                int length = data[index++];
                for(int i = 0; i < length; i++){
                    byte curr = data[index++];
                    char t = (char)curr;
                    ans += t;
                }
                return ans += "."+ parseSnoInc(data, index);
            } 
        }else{
            return null;
        }

    }

    private String RdataParse(byte[] rdata, byte[] data){
        String t = helpRData(rdata, data);
        if(t.length() == 0){
            return t;
        }
        return t.substring(0, t.length()-1);

    }
    private String helpRData(byte[] rdata, byte[] data){
        String ans = "";
        //label can be label or pointer 
        //label has first two bits 00
        //pointer has 11
        int len = rdata.length;
        int j = 0;
        while(j < rdata.length){
            byte label = rdata[j];
            byte identifier = (byte)(label & (byte)0xC0);          
            if(identifier == (byte)0xC0){
                int offset = ((int)(rdata[j] & 0x3F) << 8) + ((int)rdata[j + 1] & 0xFF);
                ans += parseSnoInc(data, offset); 
                j += 2;
            }
            else if(identifier == 0){
                if(label == 0){
                    return ans;
                }else{
                    int length = rdata[j];
                    j++;
                    for(int i = 0; i < length; i++){
                        byte curr = rdata[j];
                        char t = (char)curr;
                        ans += t;
                        j++;
                    }
                    ans += ".";
                } 
            }else{
                return null;
            }
        }
        return ans;
        
    }

}
 
