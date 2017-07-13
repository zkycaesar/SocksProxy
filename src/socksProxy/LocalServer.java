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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocalServer {
	private final int PORT = 1070;
//	private final int poolSize = Runtime.getRuntime().availableProcessors() + 1;
	private final int poolSize = 2;
	private Selector accSelector;
	private Selector[] rwSelector;
	private ServerSocketChannel serverSocketChannel;
//	private HashMap<SocketChannel, SocketChannel> localToProxy;
//	private HashMap<SocketChannel, SocketChannel> ProxyToLocal;
	private int selectorIndex;
//	private ExecutorService pool = Executors.newCachedThreadPool(); 
	
	public static void main(String[] args) {
		LocalServer ls = new LocalServer();
		ls.init();
		ls.select();
	}
	
	private void init(){		
		try {
			System.out.println("Initiallying");
			accSelector = Selector.open();
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.socket().bind(new InetSocketAddress(PORT));			
			serverSocketChannel.register(accSelector, SelectionKey.OP_ACCEPT);
			rwSelector = new Selector[poolSize];
			Thread[] threads = new Thread[poolSize];
			for(int i = 0; i < poolSize; i++){
				rwSelector[i] = Selector.open();
				LocalProcessor p = new LocalProcessor(rwSelector[i], i);
				threads[i] = new Thread(p);
				threads[i].start();
			}
			System.out.println("Totally " + poolSize + " Thread started.");
			Monitor monitor = new Monitor(threads);
			new Thread(monitor).start();
			selectorIndex = 0;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void select(){
		System.out.println("Start acceptor.");
		int count = 0;
		while(true){
			try {
				accSelector.select();
				
				count++;
//				System.out.println("" + count);
				Iterator it = accSelector.selectedKeys().iterator();
				while(it.hasNext()){
					SelectionKey selectionKey = (SelectionKey)it.next();
					it.remove();
					if(!selectionKey.isValid()) continue;					
					SocketChannel c = serverSocketChannel.accept();
					while(c == null){
						c = serverSocketChannel.accept();
					}				
					c.configureBlocking(false);
					SelectionKey key = c.register(rwSelector[selectorIndex], SelectionKey.OP_READ);
					key.attach("local");
					rwSelector[selectorIndex].wakeup();
					selectorIndex = (selectorIndex + 1) % poolSize;
					System.out.println("Accept socket: " + c.getRemoteAddress());
				}
//				Thread.sleep(1);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
