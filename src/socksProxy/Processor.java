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
import java.util.HashMap;
import java.util.Iterator;

public class Processor implements Runnable {
	private Selector selector;
	private ByteBuffer readBuffer;
	private HashMap<SocketChannel, SocketChannel> localToProxy;
	private HashMap<SocketChannel, SocketChannel> proxyToLocal;
	
	public Processor(Selector sel){
		readBuffer = ByteBuffer.allocate(1500);
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
		readSocketChannel = (SocketChannel)key.channel();
		SocketAddress readSocAddr = null;
		Charset cs = Charset.forName ("UTF-8");
		readBuffer.clear();
		int readByteCount = 0;			
		try {
			readSocAddr = readSocketChannel.getRemoteAddress();
			int tmpCount = 0;
			readByteCount = readSocketChannel.read(readBuffer);
		} catch (IOException e1) {
			e1.printStackTrace();
			SocketChannel pair = null;
			if(key.attachment() != null){
				pair = localToProxy.get(readSocketChannel);
				if(pair != null){
					pair.close();
					pair.keyFor(selector).cancel();
					proxyToLocal.remove(pair);
					localToProxy.remove(readSocketChannel);
				}
			}
			else{
				pair = proxyToLocal.get(readSocketChannel);
				if(pair != null){
					pair.close();
					pair.keyFor(selector).cancel();
					localToProxy.remove(pair);
					proxyToLocal.remove(readSocketChannel);
				}
			}			
			key.cancel();
			readSocketChannel.close();
			return;
		}
		if(readByteCount == -1){
			SocketChannel pair = null;
			if(key.attachment() != null){
				pair = localToProxy.get(readSocketChannel);
				if(pair != null){
					pair.close();
					pair.keyFor(selector).cancel();
					proxyToLocal.remove(pair);
					localToProxy.remove(readSocketChannel);
				}
			}
			else{
				pair = proxyToLocal.get(readSocketChannel);
				if(pair != null){
					pair.close();
					pair.keyFor(selector).cancel();
					localToProxy.remove(pair);
					proxyToLocal.remove(readSocketChannel);
				}
			}			
			key.cancel();
			readSocketChannel.close();
			return;
		}
		
		if(key.attachment() != null){
			readBuffer.flip();
			CharBuffer charBuffer = cs.decode(readBuffer);
			String content = charBuffer.toString(); 
//			System.out.println("Get message from: " + readSocAddr + ". Byte count: " + readByteCount);
//			System.out.println("Get message from: " + readSocAddr + ". Byte count: " + readByteCount + "\r\nThe message is:\r\n" + content);
			
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
					key.cancel();
					return;
				}
				System.out.println("Connecting to remote host: " + remoteHost);
				remoteSocket = SocketChannel.open();
				InetSocketAddress socAdd = new InetSocketAddress(remoteHost, 443);
				if(socAdd.isUnresolved()){
					readSocketChannel.close();
					key.cancel();
					System.out.println("Remote host " + remoteHost + " is unresolved.");
					return;
				}
				remoteSocket.configureBlocking(false);
				remoteSocket.connect(socAdd);
				while(!remoteSocket.finishConnect()){}
				remoteSocket.register(selector, SelectionKey.OP_READ);
				
				localToProxy.put(readSocketChannel, remoteSocket);
				proxyToLocal.put(remoteSocket, readSocketChannel);
				readBuffer.clear();
				readBuffer.put(cs.encode(CharBuffer.wrap("HTTP/1.1 200 Connection established" + System.getProperty("line.separator") + System.getProperty("line.separator"))));
				readBuffer.flip();
				int writeCount = 0;
				while(readBuffer.hasRemaining())writeCount += readSocketChannel.write(readBuffer);
				System.out.println("Get message from: " + readSocAddr + ". Byte count: " + readByteCount + "\r\nWriting " + writeCount + " bytes to: " + remoteSocket.getRemoteAddress());
				return;
			}
			else{
				remoteSocket = localToProxy.get(readSocketChannel);
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
						key.cancel();
						return;
					}
					remoteSocket = SocketChannel.open();
					InetSocketAddress socAdd = new InetSocketAddress(remoteHost, remotePort);
					if(socAdd.isUnresolved()){
						readSocketChannel.close();
						key.cancel();
						System.out.println("Remote host " + remoteHost + " is unresolved.");
						return;
					}
					try{
						remoteSocket.connect(socAdd);
						remoteSocket.configureBlocking(false);
						remoteSocket.register(selector, SelectionKey.OP_READ);
					}catch(IOException e){
						e.printStackTrace();
						key.cancel();
						readSocketChannel.close();
					}					
					localToProxy.put(readSocketChannel, remoteSocket);
					proxyToLocal.put(remoteSocket, readSocketChannel);					
				}
			}
		}
		else{
			remoteSocket = proxyToLocal.get(readSocketChannel);
			if(remoteSocket == null){
				key.cancel();
				readSocketChannel.close();
				return;
			}
		}
		
		try{
			readBuffer.flip();
			int writeCount = remoteSocket.write(readBuffer);
			readBuffer.compact();
			int tmpReadCount = 0;
			while(readBuffer.position() != 0 || (tmpReadCount = readSocketChannel.read(readBuffer)) > 0){
				readBuffer.flip();
				writeCount += remoteSocket.write(readBuffer);
				readByteCount += tmpReadCount; 
				readBuffer.compact();
			}
			System.out.println("Get message from: " + readSocAddr + ". Byte count: " + readByteCount + 
					"\r\nWriting " + writeCount + " bytes to: " + remoteSocket.getRemoteAddress());
//			System.out.println("Writing " + writeCount + " bytes to: " + remoteSocket.getRemoteAddress());
		}catch(IOException e){
			e.printStackTrace();
			if(key.attachment() != null){
				localToProxy.remove(remoteSocket);
				proxyToLocal.remove(readSocketChannel);
			}
			else{
				proxyToLocal.remove(remoteSocket);
				localToProxy.remove(readSocketChannel);
			}					
			key.cancel();
			remoteSocket.keyFor(selector).cancel();
			readSocketChannel.close();
			remoteSocket.close();
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
