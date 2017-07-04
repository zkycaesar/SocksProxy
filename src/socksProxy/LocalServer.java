package socksProxy;

import java.io.BufferedReader;
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

public class LocalServer {
	private final int PORT = 1070;
	private Selector selector;
	ByteBuffer readBuffer;
	private HashMap<SocketChannel, SocketChannel> cache;
	private static HashSet<String> httpRequestTable = new HashSet<String>(){{add("GET"); add("POST"); add("CONNECT");add("HEAD");add("OPTIONS"); add("PUT"); add("DELETE"); add("TRACE");}};
	
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
			readBuffer = ByteBuffer.allocate(2048);
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
				System.out.println(count);
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
				System.out.println("Accept socket: " + socketChannel.getRemoteAddress());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if(selectionKey.isReadable()){
			socketChannel = (SocketChannel)selectionKey.channel();
			Charset cs = Charset.forName ("UTF-8");
			readBuffer.clear();
			try {
//				System.out.println("Read socket: " + socketChannel.getRemoteAddress());	
				int count = socketChannel.read(readBuffer);
				if(count > 0) {
					readBuffer.flip();
					CharBuffer charBuffer = cs.decode(readBuffer); 
					String content = charBuffer.toString();
		            System.out.println(socketChannel.getRemoteAddress());
					System.out.println(content);
					
					String[] header = content.split(System.getProperty("line.separator"));
					if(content.startsWith("CONNECT")){						
						String remoteHost = header[1].substring(6);
						int index = remoteHost.indexOf(":");
						if(index != -1)remoteHost = remoteHost.substring(0, index);
						if(!remoteHost.equals("bbs.sjtu.edu.cn"))return;
						System.out.println("Remote host: " + remoteHost);
						SocketChannel remoteSocket = null;
						remoteSocket = SocketChannel.open();
						remoteSocket.connect(new InetSocketAddress(remoteHost, 443));
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
							remoteSocket = SocketChannel.open();
							remoteSocket.connect(new InetSocketAddress(remoteHost, 80));
							remoteSocket.configureBlocking(false);
							remoteSocket.register(selector, SelectionKey.OP_READ);
						}
						remoteSocket.write(readBuffer);
						while (socketChannel.read(readBuffer) >= 0 || readBuffer.remaining() != 0) {
//							System.out.println("...............");
							readBuffer.flip();
							remoteSocket.write(readBuffer);
						    readBuffer.compact();    // In case of partial write
						}	
						System.out.println("Writing to: " + remoteSocket.getRemoteAddress());
					}
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
