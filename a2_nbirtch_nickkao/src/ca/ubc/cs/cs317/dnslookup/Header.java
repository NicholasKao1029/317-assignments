package ca.ubc.cs.cs317.dnslookup;

/**
                                       1  1  1  1  1  1
      0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                      ID                       |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE   |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    QDCOUNT                    |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    ANCOUNT                    |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    NSCOUNT                    |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    ARCOUNT                    |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    */
public class Header{
  
  private short ID;     

  private boolean QR; 
  private byte Opcode;

  private boolean AA;
  private boolean TC;
  private boolean RD;
  private boolean RA;

  private byte RCODE;

  private short QDCOUNT;
  private short ANCOUNT;
  private short NSCOUNT;
  private short ARCOUNT;


  public short getID(){
    return this.ID;
  }
  public boolean getQR(){
    return this.QR;
  }
  public byte getOpcode(){
    return this.Opcode;
  }
  public boolean getAA(){
    return this.AA;
  }
  public boolean getTC(){
    return this.TC;
  }
  public boolean getRD(){
    return this.RD;
  }
  public boolean getRA(){
    return this.RA;
  }
  public byte getRCCODE(){
    return this.RCODE;
  }
  public short getQDCOUNT(){
    return this.QDCOUNT;
  }
  public short getANCOUNT(){
    return this.ANCOUNT;
  }
  public short getNSCOUNT(){
    return this.NSCOUNT;
  }
  public short getARCOUNT(){
    return this.ARCOUNT;
  }
  
  private short extractShort(byte[] data, int index){
    short ans = (short) (((data[index] &0xff) << 8) | (data[index + 1] & 0xff));
    return ans;
  }

  public Header(byte[] data){

    ID = extractShort(data, 0);

    if((data[2] & 0x80) == 128){
      QR = true;
    }else{
      QR = false;
    }

    Opcode = (byte) ((data[2] & 0x78) >> 3);

    if((data[2] & 0x4) == 4){
      AA = true;
    }else{
      AA = false;
    }

    if((data[2] & 0x2) == 1){
      TC = true;
    }else{
      TC = false;
    }

    if((data[2] & 0x1) == 1){
      RD = true;
    }else{
      RD = false;
    }

    if((data[3] & 0x80) == 128){
      RA = true;
    }else{
      RA = false;
    }

    RCODE = (byte) (data[3] & 0xF);

    QDCOUNT = extractShort(data, 4);
    ANCOUNT = extractShort(data, 6);
    NSCOUNT = extractShort(data, 8);
    ARCOUNT = extractShort(data, 10);
  }
}