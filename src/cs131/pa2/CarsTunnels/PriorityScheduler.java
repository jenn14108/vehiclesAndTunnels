package cs131.pa2.CarsTunnels;


import java.util.concurrent.locks.*;
import java.util.*;
import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;

public class PriorityScheduler extends Tunnel{

	private Lock lock;
	private Condition priorityMet;
	private Condition enterTunnel;
	private Collection<Tunnel> tunnels;
	private HashMap<Vehicle,Tunnel> vehiclesToTunnels;
	private PriorityQueue<Vehicle> priorityQueue;

	public PriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name,log);
		this.tunnels = tunnels;
		this.lock = new ReentrantLock();
		this.priorityMet = lock.newCondition();
		this.enterTunnel = lock.newCondition();
		
		this.vehiclesToTunnels = new HashMap<>();
			
		this.priorityQueue = new PriorityQueue<>(new Comparator<Vehicle>() {
			@Override
			public int compare(Vehicle a, Vehicle b) {
				return b.getPriority() - a.getPriority();
			}
		});
	}

	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		this.lock.lock();
		//add 
		//check 2 conditions: there is a free tunnel, priority is high enough to enter
		try {
			//before we even check the priority of the vehicle, we check that a tunnel actually exists
			BasicTunnel freeTunnel = null;
			while (freeTunnel == null) {
				for (Tunnel tunnel: tunnels) {
					if (((BasicTunnel) tunnel).canEnter(vehicle) == true) {
						freeTunnel = (BasicTunnel) tunnel;
						break;
					}
				} 
				//check if now there is a free tunnel, if not wait
				if (freeTunnel == null) {
					enterTunnel.await();
				}
				
			}
			//since we exited out of the while loop, it means that there is a free tunnel
			//now we check priority
			priorityQueue.add(vehicle);
			while (!priorityQueue.peek().equals(vehicle)) {
				priorityMet.await();
			}
		
			priorityQueue.remove(vehicle);
			priorityMet.signalAll();			
			vehiclesToTunnels.put(vehicle, freeTunnel);

			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();	
		} finally {
			this.lock.unlock();
		}
		
		return false;
	}

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		this.lock.lock();
		try {
			BasicTunnel tunnel = (BasicTunnel) vehiclesToTunnels.get(vehicle);
			tunnel.exitTunnelInner(vehicle);
			enterTunnel.signalAll();
			this.vehiclesToTunnels.remove(vehicle);
		} finally {
			this.lock.unlock();
		}
	}
	
}
