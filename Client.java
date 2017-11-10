import javax.crypto.*;
import java.security.*;
import java.security.spec.*;
import java.io.*;
import java.net.*;
import java.util.Base64;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


public class Client {
    public static void main( String args[] ) {
        
        String tags = "";
        if( args.length >= 3 ){
            tags = args[2];
        }else if( args.length < 2 ){
            System.err.println( "Usage: java Client <host name> <port number> <tags: c, i, and/or a>" );
            System.exit( 1 );
        }

        System.out.println( "Starting Client.java..." );

        String hostName = args[0];
        int portNumber = Integer.parseInt( args[1] );
 
        try(
            Socket serverSocket = new Socket( hostName, portNumber );

            PrintWriter out = new PrintWriter( serverSocket.getOutputStream(), true );
            BufferedReader in = new BufferedReader( new InputStreamReader( serverSocket.getInputStream() ) );
            BufferedReader stdIn = new BufferedReader( new InputStreamReader( System.in ) );
        ){
            // Prepare common functions library
            Common commonLib = new Common();



            //  If confidentiality, send an confidentiality handshake
            SecretKeySpec clientAesKey = null;
            if( tags.toLowerCase().contains("c") ){
                // The Client creates its own DH key pair with 2048-bit key size
                KeyPairGenerator clientKpairGen = KeyPairGenerator.getInstance("DH");
                clientKpairGen.initialize(2048);
                KeyPair clientKpair = clientKpairGen.generateKeyPair();
                
                // The Client creates and initializes its DH KeyAgreement object
                KeyAgreement clientKeyAgree = KeyAgreement.getInstance("DH");
                clientKeyAgree.init( clientKpair.getPrivate() );
                
                // The Client encodes its public key, and sends it over to The Server.
                byte[] clientPubKeyEnc = clientKpair.getPublic().getEncoded();
                out.println( "C " + commonLib.encodeWithBase64( clientPubKeyEnc ) );

                // Expect a handshake message
                String initialHandshake = in.readLine();
                byte[] serverPubKeyEnc = commonLib.decodeBase64( initialHandshake );
                
                KeyFactory keyFact = KeyFactory.getInstance("DH");
                PublicKey serverPubKey = keyFact.generatePublic( new X509EncodedKeySpec( serverPubKeyEnc ) );
                
                clientKeyAgree.doPhase(serverPubKey, true);

                clientAesKey = new SecretKeySpec( clientKeyAgree.generateSecret(), 0, 16, "AES" );
            }

            // If integrity, send an integrity handshake
            PublicKey serverPuKey = null;
            if( tags.toLowerCase().contains("i") ){
                System.out.println("Starting integrity");
                String encodedPublicKey = commonLib.createKeysAndEncodePublicKey( out );

                String newTags = "";
                if( tags.toLowerCase().contains("c") ){
                    newTags += "c";
                }
                commonLib.sendMessage( "I " + encodedPublicKey, out, newTags, clientAesKey );
                commonLib.sendMessage( encodedPublicKey, out, newTags, clientAesKey );
                
                

                // Expect handshake response
                byte[] handshakeMessage = commonLib.decodeBase64( in.readLine() );

                KeyFactory keyFact = KeyFactory.getInstance("RSA");
                serverPuKey = keyFact.generatePublic( new X509EncodedKeySpec( handshakeMessage ) );
            }
            
            // else{
            //     // TODO: do this better
            //     String encoded = commonLib.encodeWithBase64( "Simple handshake".getBytes() );
            //     commonLib.sendMessage( encoded, out, tags, null );
            // }

            


            // TODO: Client-side authentication goes here

            // Start retrieval thread
            commonLib.startInboxThread( in, "Server", tags, serverPuKey, clientAesKey );

            System.out.println("Client started!");

            String userInput;
            while( ( userInput = stdIn.readLine() ) != null ){ // TODO: fix graphical issue for when messages pop up when typing a message
                // Send off the message
                commonLib.sendMessage( userInput, out, tags, clientAesKey );
            }

        }catch ( UnknownHostException e ){
            System.err.println( "Don't know about host " + hostName );
            System.exit( 1 );
        }catch( IOException e ){
            System.err.println( "Couldn't get I/O for the connection to " + hostName );
            System.exit( 1 );
        }catch( InvalidKeySpecException e ){
                System.err.println( "Exception caught for Key Spec" );
                System.exit( 1 );
        }catch( NoSuchAlgorithmException e ){
                System.err.println( "Attempted to create a key pair with an invalid algorithm" );
                System.exit( 1 );
        }catch( InvalidKeyException e ){
                System.err.println( "Invalid key" );
                System.exit( 1 );
        }
    }
}