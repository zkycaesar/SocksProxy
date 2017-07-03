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
import java.util.Iterator;
import java.util.Map;

public class LocalServer {
	private final int PORT = 1070;
	private Selector selector;
	private Map<SelectionKey, SelectionKey> cache;
	
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void select(){
		while(true){
			try {
				selector.select();
				Iterator it = selector.selectedKeys().iterator();
				while(it.hasNext()){
					SelectionKey selectionKey = (SelectionKey)it.next();
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
					Thread.sleep(300);
				}				
				socketChannel.configureBlocking(false);
				socketChannel.register(selector, SelectionKey.OP_READ);
				System.out.println("Accept socket: " + socketChannel.getRemoteAddress());
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
		else if(selectionKey.isReadable()){
			socketChannel = (SocketChannel)selectionKey.channel();
			Charset cs = Charset.forName ("UTF-8");
			ByteBuffer readBuffer;
			readBuffer = ByteBuffer.allocate(2048);
			
			try {
				System.out.println("Read socket: " + socketChannel.getRemoteAddress());
//				int count;
				int count = socketChannel.read(readBuffer);
//				while((count = socketChannel.read(readBuffer)) != -1){
				if(count > 0) {
					readBuffer.flip();
					CharBuffer charBuffer = cs.decode(readBuffer); 
		            System.out.println(socketChannel.getRemoteAddress());
					System.out.println(charBuffer);
					readBuffer.clear();
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
