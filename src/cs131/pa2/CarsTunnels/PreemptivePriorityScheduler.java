package cs131.pa2.CarsTunnels;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;

public class PreemptivePriorityScheduler extends Tunnel{
	
	private Lock lock;
	private Condition priorityMet;
	private Condition enterTunnel;
	private Collection<Tunnel> tunnels;
	private HashMap<Vehicle,Tunnel> vehiclesToTunnels;
	private PriorityQueue<Vehicle> priorityQueue;
	
	
	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
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
		//always let ambulance go 
		BasicTunnel freeTunnel = null;
		try {
			if (vehicle instanceof Ambulance) {
				while (freeTunnel == null) {
					//check for free tunnel
					freeTunnel = (BasicTunnel)this.checkForFreeTunnel(vehicle);
					if (freeTunnel == null) {
						enterTunnel.await();
					}
				}
				vehiclesToTunnels.put(vehicle, freeTunnel);
				return freeTunnel.tryToEnterInner(vehicle);

				
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			this.lock.unlock();
		}

		return false;
	}

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		
	}
	
	public Tunnel checkForFreeTunnel(Vehicle vehicle) {
		for (Tunnel t : tunnels) {
			if (t.tryToEnter(vehicle) == true) {
				return t;
			}
		}
		return null;
	}
	
}

