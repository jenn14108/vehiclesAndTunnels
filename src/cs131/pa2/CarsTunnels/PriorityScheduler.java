package cs131.pa2.CarsTunnels;


import java.util.concurrent.locks.*;
import java.util.*;
import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;

public class PriorityScheduler extends Tunnel{

	private Lock lock; 
	private Condition priorityMet; //check for the priority requirement
	private Condition enterTunnel; //check for free tunnel
	
	private Collection<Tunnel> tunnels; 
	private HashMap<Vehicle,Tunnel> vehiclesToTunnels; //keeps track of the tunnel each vehicle is in
	private PriorityQueue<Vehicle> priorityQueue; //highest priority vehicles get to access and enter tunnel first

	/**
	 * This is the constructor for the priorityScheduler
	 */
	public PriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name,log);
		this.tunnels = tunnels;
		this.lock = new ReentrantLock();
		this.priorityMet = lock.newCondition();
		this.enterTunnel = lock.newCondition();
		
		this.vehiclesToTunnels = new HashMap<>();
			
		//add vehicles to the queue based on priority of vehicles
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
		
		try {
			//check for priority of the vehicle
			priorityQueue.add(vehicle);
			while (!priorityQueue.peek().equals(vehicle)) {
				priorityMet.await();
			}
			
			//check that a tunnel actually exists
			BasicTunnel freeTunnel = null;
			while (freeTunnel == null) {
				for (Tunnel tunnel: tunnels) {
					if (tunnel.tryToEnter(vehicle) == true) {
						freeTunnel = (BasicTunnel) tunnel;
						break;
					}
				} 
				//check if now there is a free tunnel, if not wait
				if (freeTunnel == null) {
					enterTunnel.await();
				}
				
			}
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
			//find the tunnel the vehicle is in, have the vehicle exit the tunnel,
			BasicTunnel tunnel = (BasicTunnel) vehiclesToTunnels.get(vehicle);
			tunnel.exitTunnelInner(vehicle);
			//remove vehicle from queue and signal others awaiting
			priorityQueue.remove(vehicle);		
			priorityMet.signalAll();	
			//signal other vehicles that this vehicle has left tunnel
			enterTunnel.signalAll();
			this.vehiclesToTunnels.remove(vehicle);
		} finally {
			this.lock.unlock();
		}
	}
	
}
