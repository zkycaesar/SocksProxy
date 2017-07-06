package socksProxy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocalServer {
	private final int PORT = 1070;
	private Selector selector;
	ByteBuffer readBuffer;
	private HashMap<SocketChannel, SocketChannel> cache;
//	private static HashSet<String> httpRequestTable = new HashSet<String>(){{add("GET"); add("POST"); add("CONNECT");add("HEAD");add("OPTIONS"); add("PUT"); add("DELETE"); add("TRACE");}};
//	private final Logger logger = Logger.getLogger("Logger");
	
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
				System.out.println("" + count);
				Iterator it = selector.selectedKeys().iterator();
				while(it.hasNext()){
					SelectionKey selectionKey = (SelectionKey)it.next();
					if(!selectionKey.isValid()) continue;
					it.remove();
					handleKey(selectionKey);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void handleKey(SelectionKey selectionKey) throws IOException{
		
		if(selectionKey.isAcceptable()){
			SocketChannel accSocketChannel = null;
			ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
			try {
				while(accSocketChannel == null){
					accSocketChannel = serverSocketChannel.accept();
				}				
				accSocketChannel.configureBlocking(false);
				accSocketChannel.register(selector, SelectionKey.OP_READ);
				System.out.println("Accept socket: " + accSocketChannel.getRemoteAddress());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if(selectionKey.isReadable()){
			SocketChannel readSocketChannel = null;
			readSocketChannel = (SocketChannel)selectionKey.channel();
			SocketAddress readSocAddr = null;
			Charset cs = Charset.forName ("UTF-8");
			readBuffer.clear();
			int readByteCount = 0;			
			try {
				readSocAddr = readSocketChannel.getRemoteAddress();
				int tmpCount = 0;
				while((tmpCount = readSocketChannel.read(readBuffer)) > 0) readByteCount += tmpCount;
			} catch (IOException e1) {
				e1.printStackTrace();
				selectionKey.cancel();
				SocketChannel pair = cache.get(readSocketChannel);
				if(pair != null){
					pair.close();
					pair.keyFor(selector).cancel();
					cache.remove(pair);
					cache.remove(readSocketChannel);
				}				
				readSocketChannel.close();
			}
			if(readByteCount > 0){
				readBuffer.flip();
				CharBuffer charBuffer = cs.decode(readBuffer);
				String content = charBuffer.toString(); 
				System.out.println("Get message from: " + readSocAddr + "\r\n The message is:\r\n" + content);
				
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
						readSocketChannel.close();
						selectionKey.cancel();
						return;
					}
					System.out.println("Connecting to remote host: " + remoteHost);
					SocketChannel remoteSocket = null;
					remoteSocket = SocketChannel.open();
					InetSocketAddress socAdd = new InetSocketAddress(remoteHost, 443);
					if(socAdd.isUnresolved()){
						System.out.println("Remote host " + remoteHost + " is unresolved.");
						return;
					}
					remoteSocket.configureBlocking(false);
					remoteSocket.connect(socAdd);
					while(!remoteSocket.finishConnect()){}
					remoteSocket.register(selector, SelectionKey.OP_READ);
					
					cache.put(readSocketChannel, remoteSocket);
					cache.put(remoteSocket, readSocketChannel);
					readBuffer.clear();
					readBuffer.put(cs.encode(CharBuffer.wrap("HTTP/1.1 200 Connection established" + System.getProperty("line.separator") + System.getProperty("line.separator"))));
					readBuffer.flip();
					while(readBuffer.hasRemaining())readSocketChannel.write(readBuffer);
				}
				else{
					System.out.println("!!!!!!!!!!!!");
					SocketChannel remoteSocket = cache.get(readSocketChannel);	
					if(remoteSocket == null){
						String remoteHost = null;
						int remotePort = 80;
						for(String item : header){
							if(item.startsWith("Host")){
								remoteHost = item.substring(6);
								int index = remoteHost.indexOf(":");
								if(index != -1){
									remotePort = Integer.parseInt(remoteHost.substring(index));
									remoteHost = remoteHost.substring(0, index);									
								}
								break;
							}
						}
						if(remoteHost == null || remoteHost.indexOf("google") != -1){
							readSocketChannel.close();
							selectionKey.cancel();
							return;
						}
						remoteSocket = SocketChannel.open();
						InetSocketAddress socAdd = new InetSocketAddress(remoteHost, remotePort);
						if(socAdd.isUnresolved()){
							System.out.println("Remote host " + remoteHost + " is unresolved.");
							return;
						}
						remoteSocket.connect(socAdd);
						remoteSocket.configureBlocking(false);
						remoteSocket.register(selector, SelectionKey.OP_READ);
						cache.put(readSocketChannel, remoteSocket);
						cache.put(remoteSocket, readSocketChannel);
					}
					readBuffer.flip();
					try{
						System.out.println("write remain: " + readBuffer.remaining());
						int writeCount = remoteSocket.write(readBuffer);
						System.out.println("Write bytes: " + writeCount + " Total count: " + readByteCount);
						while (writeCount < readByteCount) {							
							writeCount += remoteSocket.write(readBuffer);
//						    readBuffer.compact();    // In case of partial write
						}	
						System.out.println("Writing to: " + remoteSocket.getRemoteAddress());
					}catch(IOException e){
						e.printStackTrace();
						cache.remove(remoteSocket);
						cache.remove(readSocketChannel);
						selectionKey.cancel();
						remoteSocket.keyFor(selector).cancel();
						readSocketChannel.close();
						remoteSocket.close();
					}					
				}
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
//	            System.out.println("新连接:" + srcAddr+":" + srcPort);
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
//	                    System.out.println("服务端 finally 异常:" + e.getMessage());    
//	                }    
//	            }
//	        }catch(Exception e){e.printStackTrace();}finally{
//	            try{
//	                System.out.println("关闭连接:"+socket.getInetAddress()+":"+socket.getPort());
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
