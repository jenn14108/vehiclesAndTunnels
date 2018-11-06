package cs131.pa2.CarsTunnels;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;

public class PreemptivePriorityScheduler extends Tunnel{
	
	private Lock schedulerLock; //lock for the scheduler
	private Condition priorityMet; //condition to check that priority requirement is met 
	private Condition ambulCanEnter; //condition to check whether vehicle is an ambulance 
	
	private Collection<Tunnel> tunnels; //collection of all tunnels
	private HashMap<Vehicle,Tunnel> vehiclesToTunnels; //keeps track of each vehicle and its tunnel
	private PriorityQueue<Vehicle> carPriority; //keeps track of vehicles based on priority
	private HashMap<Tunnel,Lock> tunnelLocks; //keeps track of all tunnels and their respective locks
	private HashMap<Lock,Condition> tunLockCond; //keeps track of the condition on the lock for each tunnel

	/**
	 * The constructor for the preemptivePriorityScheduler
	 */
	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name,log);
		this.tunnels = tunnels;
		
		this.schedulerLock = new ReentrantLock(); 
		this.priorityMet = this.schedulerLock.newCondition();
		this.ambulCanEnter = this.schedulerLock.newCondition(); 
		
		this.vehiclesToTunnels = new HashMap<>();
		this.tunnelLocks = new HashMap<>();
		this.tunLockCond = new HashMap<>();
		
		//for each tunnel, create a new unique lock, as well as its own condition. 
		//This is so that, just in case an ambulance enters and other vehicles have to await,
		//the right vehicles are signaled. We don't want to signal vehicles in other tunnels
		for (Tunnel t: tunnels) {
			Lock tunnelLock =  new ReentrantLock(); 
			tunnelLocks.put(t, tunnelLock);
			Condition tunnelForAmbul = tunnelLock.newCondition();
			this.tunLockCond.put(tunnelLock, tunnelForAmbul);
		}
		//priority queue with comparator based on vehicle priority
		this.carPriority = new PriorityQueue<>(new Comparator<Vehicle>() {
			@Override
			public int compare(Vehicle a, Vehicle b) {
				return b.getPriority() - a.getPriority();
			}
		});
	}

	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		this.schedulerLock.lock();
		BasicTunnel freeTunnel = null;
		try {
			//if the vehicle is an ambulance, no need to check priority because it is 
			//of the highest priority. Simply wait till there is a free tunnel
			if (vehicle instanceof Ambulance) {
				while (freeTunnel == null) {
					freeTunnel = (BasicTunnel)this.checkForFreeTunnel(vehicle);
					if (freeTunnel == null) {
						ambulCanEnter.await();
					}
				}
				//obtain the lock from that specific tunnel, and obtain the lock condition
				//equip the vehicle with that specific tunnel lock 
				Lock tunnelLock = this.tunnelLocks.get(freeTunnel);
				vehicle.setLock(tunnelLock, this.tunLockCond.get(tunnelLock));
				
				//create pairing of the vehicle and its tunnel
				vehiclesToTunnels.put(vehicle, freeTunnel);
				return true;
			} else { //not an ambulance, need to check priority, and then check if there's a free tunnel
				carPriority.add(vehicle);
				//keep waiting until priority is high enough in relation to other vehicles to proceed
				while (!carPriority.peek().equals(vehicle)) {
					this.priorityMet.await();
				}
				carPriority.remove(vehicle);
				priorityMet.signalAll();
				
				//wait for a tunnel to be free
				while (freeTunnel == null) {
					freeTunnel = (BasicTunnel)this.checkForFreeTunnel(vehicle);
				}
				//obtain lock of the specific tunnel
				Lock tunnelLock = this.tunnelLocks.get(freeTunnel);
				//equip vehicle with that free tunnel's lock 
				vehicle.setLock(tunnelLock, this.tunLockCond.get(tunnelLock));
				//create mapping of the vehicle and the tunnel it has entered
				vehiclesToTunnels.put(vehicle, freeTunnel);
				return true;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			this.schedulerLock.unlock();
		}
		return false;
	}

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		this.schedulerLock.lock();
		try {
			//obtain the tunnel the vehicle is in 
			BasicTunnel tunnel = (BasicTunnel) vehiclesToTunnels.get(vehicle);
			//if vehicle type is ambulance, then exit tunnel and signal other vehicles 
			//to proceed
			if (vehicle instanceof Ambulance) {
				tunnel.exitTunnelInner(vehicle);
				this.ambulCanEnter.signalAll();
				this.vehiclesToTunnels.remove(vehicle);
			} else { //normal vehicle, simply exit the tunnel when time is up
				tunnel.exitTunnelInner(vehicle);
				this.vehiclesToTunnels.remove(vehicle);
			}
		} finally {
			this.schedulerLock.unlock();
		}
	}
	
	/**
	 * This method simply checks for free tunnels in the collection of tunnels
	 */
	public Tunnel checkForFreeTunnel(Vehicle vehicle) {
		for (Tunnel t : tunnels) {
			if (t.tryToEnter(vehicle) == true) {
				return t;
			}
		}
		return null;
	}
}

