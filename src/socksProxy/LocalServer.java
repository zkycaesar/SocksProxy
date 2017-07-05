package socksProxy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

public class LocalServer {
	private final int PORT = 1070;
	private Selector selector;
	ByteBuffer readBuffer;
	private HashMap<SocketChannel, SocketChannel> cache;
//	private static HashSet<String> httpRequestTable = new HashSet<String>(){{add("GET"); add("POST"); add("CONNECT");add("HEAD");add("OPTIONS"); add("PUT"); add("DELETE"); add("TRACE");}};
	private final Logger logger = Logger.getLogger("Logger");
	
	public static void main(String[] args) {
		LocalServer ls = new LocalServer();
		ls.init();
		ls.select();
	}
	
	private void init(){
		ServerSocketChannel serverSocketChannel;
		try {
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
			selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			readBuffer = ByteBuffer.allocate(1500);
			cache = new HashMap<SocketChannel, SocketChannel>();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void select(){
		int count = 0;
		while(true){
			try {
				selector.select();
				count++;
				logger.info("" + count);
				Iterator it = selector.selectedKeys().iterator();
				while(it.hasNext()){
					SelectionKey selectionKey = (SelectionKey)it.next();
					it.remove();
					handleKey(selectionKey);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void handleKey(SelectionKey selectionKey){
		SocketChannel socketChannel = null;
		if(selectionKey.isAcceptable()){
			ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
			try {
				while(socketChannel == null){
					socketChannel = serverSocketChannel.accept();
//					Thread.sleep(300);
				}				
				socketChannel.configureBlocking(false);
				socketChannel.register(selector, SelectionKey.OP_READ);
				logger.info("Accept socket: " + socketChannel.getRemoteAddress());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if(selectionKey.isReadable()){
			socketChannel = (SocketChannel)selectionKey.channel();
			Charset cs = Charset.forName ("UTF-8");
			readBuffer.clear();
			try {
//				logger.info("Try to read from socket: " + socketChannel.getRemoteAddress());
				if(!socketChannel.isConnected()){
					selectionKey.cancel();
					return;
				}
				int count = socketChannel.read(readBuffer);
				if(count > 0) {
					readBuffer.flip();
					CharBuffer charBuffer = cs.decode(readBuffer);
					String content = charBuffer.toString(); 
					logger.info("Get message from: " + socketChannel.getRemoteAddress() + "\r\n The message is:\r\n" + content);
					
					String[] header = content.split(System.getProperty("line.separator"));
					if(content.startsWith("CONNECT")){	
						String remoteHost = null;
						for(String item : header){
							if(item.startsWith("Host")){
								remoteHost = item.substring(6);
								int index = remoteHost.indexOf(":");
								if(index != -1)remoteHost = remoteHost.substring(0, index);
								break;
							}
						}
						if(remoteHost == null || remoteHost.indexOf("google") != -1){
							socketChannel.close();
							selectionKey.cancel();
							return;
						}
						logger.info("Connecting to remote host: " + remoteHost);
						SocketChannel remoteSocket = null;
						remoteSocket = SocketChannel.open();
						InetSocketAddress socAdd = new InetSocketAddress(remoteHost, 443);
						if(socAdd.isUnresolved()){
							logger.warning("Remote host " + remoteHost + " is unresolved.");
							return;
						}
						remoteSocket.connect(socAdd);
						remoteSocket.configureBlocking(false);
						remoteSocket.register(selector, SelectionKey.OP_READ);
						
						cache.put(socketChannel, remoteSocket);
						cache.put(remoteSocket, socketChannel);
						readBuffer.clear();
						readBuffer.put(cs.encode(CharBuffer.wrap("HTTP/1.1 200 Connection established" + System.getProperty("line.separator") + System.getProperty("line.separator"))));
						readBuffer.flip();
						while(readBuffer.hasRemaining())socketChannel.write(readBuffer);
					}
					else{
						System.out.println("!!!!!!!!!!!!");
						SocketChannel remoteSocket = cache.get(socketChannel);						
						if(remoteSocket == null){
							String remoteHost = null;
							for(String item : header){
								if(item.startsWith("Host")){
									remoteHost = item.substring(6);
									int index = remoteHost.indexOf(":");
									if(index != -1)remoteHost = remoteHost.substring(0, index);
									break;
								}
							}
							if(remoteHost == null || remoteHost.indexOf("google") != -1){
								socketChannel.close();
								selectionKey.cancel();
								return;
							}
							remoteSocket = SocketChannel.open();
							InetSocketAddress socAdd = new InetSocketAddress(remoteHost, 443);
							if(socAdd.isUnresolved()){
								logger.warning("Remote host " + remoteHost + " is unresolved.");
								return;
							}
							remoteSocket.connect(socAdd);
							remoteSocket.configureBlocking(false);
							remoteSocket.register(selector, SelectionKey.OP_READ);
							cache.put(socketChannel, remoteSocket);
							cache.put(remoteSocket, socketChannel);
						}
						readBuffer.flip();
						System.out.println("write remain: " + readBuffer.remaining());
						int writeCount = remoteSocket.write(readBuffer);
						System.out.println("Write bytes: " + writeCount + " Total count: " + count);
						while (writeCount < count) {							
							writeCount += remoteSocket.write(readBuffer);
//						    readBuffer.compact();    // In case of partial write
						}	
						System.out.println("Writing to: " + remoteSocket.getRemoteAddress());
					}
				}
				else if(count == -1){
					socketChannel.close();
					selectionKey.cancel();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			
		}
	}
	
}

//public class LocalServer {
//	private static final int PORT = 1070;
//	private static class Handler implements Runnable{
//	    private Socket socket;
//	    public Handler(Socket socket){
//	        this.socket=socket;
//	    }
//	    
//	    public void run(){
//	        try{
//	        	InetAddress srcAddr = socket.getInetAddress();
//	        	int srcPort = socket.getPort();
//	            System.out.println("������:" + srcAddr+":" + srcPort);
//	            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
////	            char[] buffer = new char[2048];
////	            int inputLen;
////	            while((inputLen = input.read(buffer)) != -1){
////	            	
////	            }
//	            String buffer = null;
//	            while((buffer = input.readLine()) != null){
//	            	System.out.println(buffer);
//	            }
//	            input.close();
//	            if (socket != null) {    
//	                try {    
//	                    socket.close();    
//	                } catch (Exception e) {    
//	                    socket = null;    
//	                    System.out.println("����� finally �쳣:" + e.getMessage());    
//	                }    
//	            }
//	        }catch(Exception e){e.printStackTrace();}finally{
//	            try{
//	                System.out.println("�ر�����:"+socket.getInetAddress()+":"+socket.getPort());
//	                if(socket!=null)socket.close();
//	            }catch(IOException e){
//	                e.printStackTrace();
//	            }
//	        }
//	    }
//	}
//
//	public static void main(String[] args) {
//		try {
//			ServerSocket serverSocket = new ServerSocket(PORT);
//			while(true){
//				Socket socket = serverSocket.accept();
//				Thread workThread=new Thread(new Handler(socket));
//	            workThread.start();
//			}
//            
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//	}
//
//}
