package socksProxy;

import java.util.concurrent.TimeUnit;

public class Monitor implements Runnable {
	private Thread[] threads;
	
	public Monitor(Thread[] threads){
		this.threads = threads;
	}
	
	@Override
	public void run() {
		while (true) {
            monitor();
            try {
                TimeUnit.MILLISECONDS.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
	}
	
	private void monitor(){
		for (int i = 0; i < threads.length; i++) {
            Thread.State state = threads[i].getState(); // 获得指定线程状态
            /*
             * 如果监控线程为终止状态,则重启监控线程
             */
            if (Thread.State.TERMINATED.equals(state)) {
                System.out.println(threads[i].getName() + "监控线程已经终止,现在开始重启监控线程!");
                threads[i].start();
            }
        }
	}

}
