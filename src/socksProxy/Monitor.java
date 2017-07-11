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
            Thread.State state = threads[i].getState(); // ���ָ���߳�״̬
            /*
             * �������߳�Ϊ��ֹ״̬,����������߳�
             */
            if (Thread.State.TERMINATED.equals(state)) {
                System.out.println(threads[i].getName() + "����߳��Ѿ���ֹ,���ڿ�ʼ��������߳�!");
                threads[i].start();
            }
        }
	}

}
