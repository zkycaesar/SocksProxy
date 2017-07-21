package socksProxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

public class ProxyServer {
	private final int poolSize = 2;
	private Selector accSelector;
	private Selector[] rwSelector;
	private ServerSocketChannel[] serverSocketChannel;
	private int selectorIndex;
	private Connection connect = null;
	private final Logger logger = Logger.getLogger("Logger");
	
	public static void main(String[] args) {
		ProxyServer ps = new ProxyServer();
		ps.init();
		ps.select();
	}
	
	private void init(){
		Class.forName("com.mysql.jdbc.Driver");
		String username = "root";
		String password = "";
		connect = DriverManager.getConnection("jdbc:mysql://localhost/proxyusers?"
                        + "user=" + username + "&password=" + password);
		PreparedStatement preparedStatement = null;
		preparedStatement = connect
                .prepareStatement("SELECT * from proxyusers.users");
		ResultSet resultSet = null;
		resultSet = preparedStatement.executeQuery();
		
		ServerSocketChannel serverSocketChannel;
		try {
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
			selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			readBuffer = ByteBuffer.allocate(4096);
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
//				System.out.println("" + count);
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
	
//	private void handleKey(SelectionKey selectionKey) throws IOException{
//		
//		if(selectionKey.isAcceptable()){
//			SocketChannel accSocketChannel = null;
//			ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
//			try {
//				while(accSocketChannel == null){
//					accSocketChannel = serverSocketChannel.accept();
//				}				
//				accSocketChannel.configureBlocking(false);
//				accSocketChannel.register(selector, SelectionKey.OP_READ);
//				System.out.println("Accept socket: " + accSocketChannel.getRemoteAddress());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//		else if(selectionKey.isReadable()){
//			SocketChannel readSocketChannel = null;
//			readSocketChannel = (SocketChannel)selectionKey.channel();
//			SocketAddress readSocAddr = null;
//			Charset cs = Charset.forName ("UTF-8");
//			readBuffer.clear();
//			int readByteCount = 0;			
//			try {
//				readSocAddr = readSocketChannel.getRemoteAddress();
//				int tmpCount = 0;
//				while((tmpCount = readSocketChannel.read(readBuffer)) > 0) readByteCount += tmpCount;
//			} catch (IOException e1) {
//				e1.printStackTrace();
//				selectionKey.cancel();
//				SocketChannel pair = cache.get(readSocketChannel);
//				if(pair != null){
//					pair.close();
//					pair.keyFor(selector).cancel();
//					cache.remove(pair);
//					cache.remove(readSocketChannel);
//				}				
//				readSocketChannel.close();
//				return;
//			}
//			if(readByteCount > 0){
//				readBuffer.flip();
//				CharBuffer charBuffer = cs.decode(readBuffer);
//				String content = charBuffer.toString(); 
////				System.out.println("Get message from: " + readSocAddr + "\r\n The message is:\r\n" + content);
//				System.out.println("Get message from: " + readSocAddr + ". Byte count: " + readByteCount + "\r\n The message is:\r\n" + content);
//				
//				String[] header = content.split(System.getProperty("line.separator"));
//				if(content.startsWith("CONNECT")){	
//					String remoteHost = null;
//					for(String item : header){
//						if(item.startsWith("Host")){
//							remoteHost = item.substring(6);
//							int index = remoteHost.indexOf(":");
//							if(index != -1)remoteHost = remoteHost.substring(0, index);
//							break;
//						}
//					}
//					if(remoteHost == null || remoteHost.indexOf("google") != -1){
//						readSocketChannel.close();
//						selectionKey.cancel();
//						return;
//					}
//					System.out.println("Connecting to remote host: " + remoteHost);
//					SocketChannel remoteSocket = null;
//					remoteSocket = SocketChannel.open();
//					InetSocketAddress socAdd = new InetSocketAddress(remoteHost, 443);
//					if(socAdd.isUnresolved()){
//						System.out.println("Remote host " + remoteHost + " is unresolved.");
//						return;
//					}
//					remoteSocket.configureBlocking(false);
//					remoteSocket.connect(socAdd);
//					while(!remoteSocket.finishConnect()){}
//					remoteSocket.register(selector, SelectionKey.OP_READ);
//					
//					cache.put(readSocketChannel, remoteSocket);
//					cache.put(remoteSocket, readSocketChannel);
//					readBuffer.clear();
//					readBuffer.put(cs.encode(CharBuffer.wrap("HTTP/1.1 200 Connection established" + System.getProperty("line.separator") + System.getProperty("line.separator"))));
//					readBuffer.flip();
//					while(readBuffer.hasRemaining())readSocketChannel.write(readBuffer);
//				}
//				else{
//					System.out.println("!!!!!!!!!!!!");
//					SocketChannel remoteSocket = cache.get(readSocketChannel);	
//					if(remoteSocket == null){
//						String remoteHost = null;
//						int remotePort = 80;
//						for(String item : header){
//							if(item.startsWith("Host")){
//								remoteHost = item.substring(6);
//								int index = remoteHost.indexOf(":");
//								if(index != -1){
//									remotePort = Integer.parseInt(remoteHost.substring(index));
//									remoteHost = remoteHost.substring(0, index);									
//								}
//								break;
//							}
//						}
//						if(remoteHost == null || remoteHost.indexOf("google") != -1){
//							readSocketChannel.close();
//							selectionKey.cancel();
//							return;
//						}
//						remoteSocket = SocketChannel.open();
//						InetSocketAddress socAdd = new InetSocketAddress(remoteHost, remotePort);
//						if(socAdd.isUnresolved()){
//							System.out.println("Remote host " + remoteHost + " is unresolved.");
//							return;
//						}
//						remoteSocket.connect(socAdd);
//						remoteSocket.configureBlocking(false);
//						remoteSocket.register(selector, SelectionKey.OP_READ);
//						cache.put(readSocketChannel, remoteSocket);
//						cache.put(remoteSocket, readSocketChannel);
//					}
//					readBuffer.flip();
//					try{
//						System.out.println("write remain: " + readBuffer.remaining());
//						int writeCount = remoteSocket.write(readBuffer);
//						System.out.println("Write bytes: " + writeCount + " Total count: " + readByteCount);
//						while (writeCount < readByteCount) {							
//							writeCount += remoteSocket.write(readBuffer);
////						    readBuffer.compact();    // In case of partial write
//						}	
//						System.out.println("Writing to: " + remoteSocket.getRemoteAddress());
//					}catch(IOException e){
//						e.printStackTrace();
//						cache.remove(remoteSocket);
//						cache.remove(readSocketChannel);
//						selectionKey.cancel();
//						remoteSocket.keyFor(selector).cancel();
//						readSocketChannel.close();
//						remoteSocket.close();
//					}					
//				}
//			}
//			
//			
//		}
//	}

}
