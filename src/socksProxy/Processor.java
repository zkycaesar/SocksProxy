package socksProxy;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;

public class Processor implements Runnable {
	private Selector selector;
	private ByteBuffer readBuffer;
	private HashMap<SocketChannel, SocketChannel> localToProxy;
	private HashMap<SocketChannel, SocketChannel> ProxyToLocal;
	
	public Processor(Selector sel){
		readBuffer = ByteBuffer.allocate(4096);
		selector = sel;
		if(!sel.isOpen()){
			try {
				selector = Selector.open();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
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
				e.printStackTrace();
			}
		}
	}
	
	private void handleKey(SelectionKey key){
		SocketChannel readSocketChannel = null;
		readSocketChannel = (SocketChannel)key.channel();
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
			key.cancel();
			
			if(pair != null){
				pair.close();
				pair.keyFor(accSelector).cancel();
				cache.remove(pair);
				cache.remove(readSocketChannel);
			}				
			readSocketChannel.close();
			return;
		}
	}

}
