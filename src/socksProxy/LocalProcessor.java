package socksProxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class LocalProcessor implements Runnable {
	private Selector selector;
	private ByteBuffer readBuffer;
	private ByteBuffer writeBuffer;
	private HashMap<SocketChannel, SocketChannel> localToProxy;
	private HashMap<SocketChannel, SocketChannel> proxyToLocal;
	private int threadID;
	private Logger logger;
	private String proxyServerIP = "";
	private int proxyServerPort = 19925;
	private String secretKey = "";
	
	
	public LocalProcessor(Selector sel, int id) throws SecurityException, IOException{
		threadID = id;
		readBuffer = ByteBuffer.allocate(1500);
		writeBuffer = ByteBuffer.allocate(1500);
		selector = sel;
		localToProxy = new HashMap<SocketChannel, SocketChannel>();
		proxyToLocal = new HashMap<SocketChannel, SocketChannel>();
		if(!sel.isOpen()){
			try {
				selector = Selector.open();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		logger = Logger.getLogger("Processor " + threadID);
		FileHandler fileHandler = new FileHandler("Processor " + threadID + ".log"); 
		fileHandler.setFormatter(new FileLogFormatter());
		logger.addHandler(fileHandler);
	}

	@Override
	public void run() {
		int count = 0;
		while(true){
			try {
				selector.select(10);
				count++;
//				System.out.println("" + count);
				Iterator it = selector.selectedKeys().iterator();
				while(it.hasNext()){
					SelectionKey selectionKey = (SelectionKey)it.next();
					it.remove();
					if(!selectionKey.isValid()) continue;					
					handleKey(selectionKey);
				}
//				Thread.sleep(1);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void handleKey(SelectionKey key) throws IOException{
		SocketChannel readSocketChannel = null;
		SocketChannel remoteSocket = null;
		SocketAddress readSocAddr = null;
		Charset cs = Charset.forName ("UTF-8");
		
		readSocketChannel = (SocketChannel)key.channel();		
		
		
		if(key.attachment() != null){
			remoteSocket = localToProxy.get(readSocketChannel);
			if(remoteSocket == null || !remoteSocket.isConnected()) {
				remoteSocket = SocketChannel.open();
				InetSocketAddress proxyServerAddr = new InetSocketAddress(proxyServerIP, proxyServerPort);
				if(proxyServerAddr.isUnresolved()){
					readSocketChannel.close();
					key.cancel();
//					System.out.println("Remote host " + remoteHost + " is unresolved.");
					logger.info("Proxy server " + proxyServerIP + " is unresolved.");
					return;
				}
				try {
					remoteSocket.configureBlocking(false);
					remoteSocket.connect(proxyServerAddr);
					while(!remoteSocket.finishConnect()){}					
				} catch(IOException e) {
					e.printStackTrace();
					logger.severe(e.getMessage());
					readSocketChannel.close();
					key.cancel();
					return;
				}	
				
				byte[] request = new byte[3];
				request[0] = 0x05;
				request[1] = 0x01;
				request[2] = 0x02;
				writeBuffer.put(request);
				writeBuffer.flip();
				try {
					while(writeBuffer.hasRemaining())remoteSocket.write(writeBuffer);
					int readByteCount = 0;
					while((readByteCount = readSocketChannel.read(readBuffer)) == 0) {}
					if(readByteCount == -1) {
						logger.severe("Socks connection establishment failed.");
						readSocketChannel.close();
						remoteSocket.close();
						key.cancel();
						return;
					}
					readBuffer.flip();
					byte[] response = new byte[2];
					readBuffer.get(response);
					if(response[0] != 0x05 || response[1] != 0x03) {
						logger.severe("Socks connection establishment failed.");
						readSocketChannel.close();
						remoteSocket.close();
						key.cancel();
						return;
					}
				} catch(IOException e) {
					e.printStackTrace();
					logger.severe(e.getMessage() + "\r\nSocks connection establishment failed.");					
					readSocketChannel.close();
					key.cancel();
					return;
				}	
				
				remoteSocket.register(selector, SelectionKey.OP_READ);
				localToProxy.put(readSocketChannel, remoteSocket);
				proxyToLocal.put(remoteSocket, readSocketChannel);	
				return;
			}
		}
		else {
			remoteSocket = proxyToLocal.get(readSocketChannel);
			if(remoteSocket == null) {
				logger.severe("Unknown Connection. Drop it.");
				readSocketChannel.close();
				key.cancel();
				return;
			}			
		}
		
		
		readBuffer.clear();
		writeBuffer.clear();
		int readByteCount = 0;			
		try {
			readSocAddr = readSocketChannel.getRemoteAddress();
			readByteCount = readSocketChannel.read(readBuffer);
		} catch (IOException e1) {
			e1.printStackTrace();
			logger.severe(e1.getMessage());
			if(key.attachment() != null){
				proxyToLocal.remove(remoteSocket);
				localToProxy.remove(readSocketChannel);
			}
			else{
				localToProxy.remove(remoteSocket);
				proxyToLocal.remove(readSocketChannel);
			}	
			remoteSocket.close();
			remoteSocket.keyFor(selector).cancel();
			key.cancel();
			readSocketChannel.close();
			return;
		}
		if(readByteCount == -1){
			if(key.attachment() != null){
				proxyToLocal.remove(remoteSocket);
				localToProxy.remove(readSocketChannel);
			}
			else{
				localToProxy.remove(remoteSocket);
				proxyToLocal.remove(readSocketChannel);
			}	
			remoteSocket.close();
			remoteSocket.keyFor(selector).cancel();
			key.cancel();
			readSocketChannel.close();
			return;
		}
		
		Cipher cipher = null;
		try {
			cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			KeySpec spec = new PBEKeySpec(secretKey.toCharArray());
			SecretKey tmp = factory.generateSecret(spec);
			SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
			cipher.init(Cipher.ENCRYPT_MODE, secret);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeySpecException | InvalidKeyException e) {
			e.printStackTrace();
		}
	}
	
	private class FileLogFormatter extends Formatter{

		@Override
		public String format(LogRecord record) {
			String log = record.getLevel() + ":" + record.getMessage()+"\r\n"; 
			return log;
		}
		
	}
	
//	private void handleKey(SelectionKey key) throws IOException{
//		SocketChannel readSocketChannel = null;
//		readSocketChannel = (SocketChannel)key.channel();
//		SocketAddress readSocAddr = null;
//		Charset cs = Charset.forName ("UTF-8");
//		readBuffer.clear();
//		int readByteCount = 0;			
//		try {
//			readSocAddr = readSocketChannel.getRemoteAddress();
//			int tmpCount = 0;
//			while((tmpCount = readSocketChannel.read(readBuffer)) > 0) readByteCount += tmpCount;
//		} catch (IOException e1) {
//			e1.printStackTrace();
//			SocketChannel pair = null;
//			if(key.attachment() != null){
//				pair = localToProxy.get(readSocketChannel);
//				if(pair != null){
//					pair.close();
//					pair.keyFor(selector).cancel();
//					proxyToLocal.remove(pair);
//					localToProxy.remove(readSocketChannel);
//				}
//			}
//			else{
//				pair = proxyToLocal.get(readSocketChannel);
//				if(pair != null){
//					pair.close();
//					pair.keyFor(selector).cancel();
//					localToProxy.remove(pair);
//					proxyToLocal.remove(readSocketChannel);
//				}
//			}			
//			key.cancel();
//			readSocketChannel.close();
//			return;
//		}
//		if(readByteCount > 0){
//			readBuffer.flip();
//			CharBuffer charBuffer = cs.decode(readBuffer);
//			String content = charBuffer.toString(); 
////			System.out.println("Get message from: " + readSocAddr + ". Byte count: " + readByteCount);
//			System.out.println("Get message from: " + readSocAddr + ". Byte count: " + readByteCount + "\r\nThe message is:\r\n" + content);
//			
//			String[] header = content.split(System.getProperty("line.separator"));
//			if(content.startsWith("CONNECT")){	
//				String remoteHost = null;
//				for(String item : header){
//					if(item.startsWith("Host")){
//						remoteHost = item.substring(6);
//						int index = remoteHost.indexOf(":");
//						if(index != -1)remoteHost = remoteHost.substring(0, index);
//						break;
//					}
//				}
//				if(remoteHost == null || remoteHost.indexOf("google") != -1){
//					readSocketChannel.close();
//					key.cancel();
//					return;
//				}
//				System.out.println("Connecting to remote host: " + remoteHost);
//				SocketChannel remoteSocket = null;
//				remoteSocket = SocketChannel.open();
//				InetSocketAddress socAdd = new InetSocketAddress(remoteHost, 443);
//				if(socAdd.isUnresolved()){
//					readSocketChannel.close();
//					key.cancel();
//					System.out.println("Remote host " + remoteHost + " is unresolved.");
//					return;
//				}
//				remoteSocket.configureBlocking(false);
//				remoteSocket.connect(socAdd);
//				while(!remoteSocket.finishConnect()){}
//				remoteSocket.register(selector, SelectionKey.OP_READ);
//				
//				localToProxy.put(readSocketChannel, remoteSocket);
//				proxyToLocal.put(remoteSocket, readSocketChannel);
//				readBuffer.clear();
//				readBuffer.put(cs.encode(CharBuffer.wrap("HTTP/1.1 200 Connection established" + System.getProperty("line.separator") + System.getProperty("line.separator"))));
//				readBuffer.flip();
//				while(readBuffer.hasRemaining())readSocketChannel.write(readBuffer);
//			}
//			else{
//				System.out.println("!!!!!!!!!!!!");
//				SocketChannel remoteSocket = null;
//				if(key.attachment() != null) remoteSocket = localToProxy.get(readSocketChannel);
//				else remoteSocket = proxyToLocal.get(readSocketChannel);
//				if(remoteSocket == null){
//					String remoteHost = null;
//					int remotePort = 80;
//					for(String item : header){
//						if(item.startsWith("Host")){
//							remoteHost = item.substring(6);
//							int index = remoteHost.indexOf(":");
//							if(index != -1){
//								remotePort = Integer.parseInt(remoteHost.substring(index));
//								remoteHost = remoteHost.substring(0, index);									
//							}
//							break;
//						}
//					}
//					if(remoteHost == null || remoteHost.indexOf("google") != -1){
//						readSocketChannel.close();
//						key.cancel();
//						return;
//					}
//					remoteSocket = SocketChannel.open();
//					InetSocketAddress socAdd = new InetSocketAddress(remoteHost, remotePort);
//					if(socAdd.isUnresolved()){
//						readSocketChannel.close();
//						key.cancel();
//						System.out.println("Remote host " + remoteHost + " is unresolved.");
//						return;
//					}
//					remoteSocket.connect(socAdd);
//					remoteSocket.configureBlocking(false);
//					remoteSocket.register(selector, SelectionKey.OP_READ);
//					if(key.attachment() != null){
//						localToProxy.put(readSocketChannel, remoteSocket);
//						proxyToLocal.put(remoteSocket, readSocketChannel);
//					}
//					else{
//						proxyToLocal.put(readSocketChannel, remoteSocket);
//						localToProxy.put(remoteSocket, readSocketChannel);
//					}
//					
//				}
//				readBuffer.flip();
//				try{
//					System.out.println("write remain: " + readBuffer.remaining());
//					int writeCount = remoteSocket.write(readBuffer);
//					System.out.println("Write bytes: " + writeCount + " Total count: " + readByteCount);
//					while (writeCount < readByteCount) {							
//						writeCount += remoteSocket.write(readBuffer);
////					    readBuffer.compact();    // In case of partial write
//					}	
//					System.out.println("Writing to: " + remoteSocket.getRemoteAddress());
//				}catch(IOException e){
//					e.printStackTrace();
//					if(key.attachment() != null){
//						localToProxy.remove(remoteSocket);
//						proxyToLocal.remove(readSocketChannel);
//					}
//					else{
//						proxyToLocal.remove(remoteSocket);
//						localToProxy.remove(readSocketChannel);
//					}					
//					key.cancel();
//					remoteSocket.keyFor(selector).cancel();
//					readSocketChannel.close();
//					remoteSocket.close();
//				}					
//			}
//		}
//	}

}
